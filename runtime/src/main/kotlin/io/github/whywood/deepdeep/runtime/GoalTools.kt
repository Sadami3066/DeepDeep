package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.sdk.ai.RequestAbortedException
import io.github.whywood.deepdeep.sdk.agent.AgentToolResult
import io.github.whywood.deepdeep.sdk.coding.tools.CodingTool
import io.github.whywood.deepdeep.sdk.coding.tools.textResult
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

private val GOAL_ACTION_VALUES = buildJsonArray {
    add(JsonPrimitive("get"))
    add(JsonPrimitive("create"))
    add(JsonPrimitive("edit"))
    add(JsonPrimitive("pause"))
    add(JsonPrimitive("resume"))
    add(JsonPrimitive("complete"))
    add(JsonPrimitive("blocked"))
}

private val goalParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("action") {
            put("type", "string")
            put("enum", GOAL_ACTION_VALUES)
            put("description", "get, create, edit, pause, resume, complete or blocked")
        }
        putJsonObject("goalId") {
            put("type", "string")
            put("description", "Id of the current goal from get_goal; required for change actions")
        }
        putJsonObject("revision") {
            put("type", "integer")
            put("description", "Current goal revision from get_goal; required for change actions")
        }
        putJsonObject("objective") {
            put("type", "string")
            put("description", "Concrete objective, required for create and optional for edit")
        }
        putJsonObject("maxRounds") {
            put("type", "integer")
            put("description", "Optional maximum goal rounds, 1-50; omit for unlimited")
        }
        putJsonObject("blockedReason") {
            put("type", "string")
            put("description", "Concrete blocker description, required for action=blocked")
        }
    }
    put("required", buildJsonArray { add(JsonPrimitive("action")) })
}

fun goalTools(
    binding: SessionBinding,
    service: GoalService,
    defaultMaxRounds: () -> Int = { 0 },
): List<CodingTool> {
    val delegate = goalTool(binding, service, defaultMaxRounds)
    return listOf(
        toolVariant(delegate, "get_goal", "Read this conversation's goal, its id, revision, state and round progress.", emptySet(), fixedAction = "get"),
        toolVariant(delegate, "create_goal", "Create and arm a concrete goal when the user requests it. Omit maxRounds for the configured default.", setOf("objective", "maxRounds"), setOf("objective"), "create"),
        toolVariant(delegate, "update_goal", "Update the current goal using its exact id and revision from get_goal. edit/pause/resume require a user request; complete requires achieved work; blocked requires the same blocker across three goal rounds.", setOf("goalId", "revision", "action", "objective", "maxRounds", "blockedReason"), setOf("goalId", "revision", "action"), actions = listOf("edit", "pause", "resume", "complete", "blocked")),
    )
}

private fun goalTool(
    binding: SessionBinding,
    service: GoalService,
    defaultMaxRounds: () -> Int,
): CodingTool = CodingTool(
    name = "goal",
    description = "Read or change this conversation's goal. get reads it; create arms a new goal; edit, pause, resume, complete and blocked change it. Changes need the current revision; create, edit, pause and resume need a direct user request.",
    parameters = goalParameters,
    label = "goal",
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        goalToolResult {
            val session = binding.current()
                ?: return@goalToolResult textResult("No session is bound. Goal tools require an active session.", isError = true)
            when (val action = args.requiredString("action").lowercase()) {
                "get" -> textResult(goalViewJson(service.goals.value[session.sessionId]).toString())
                "create" -> createGoal(service, session.sessionId, args, defaultMaxRounds)
                "edit", "pause", "resume", "complete", "blocked" ->
                    changeGoal(service, session.sessionId, args, action)
                else -> textResult(
                    "action must be get, create, edit, pause, resume, complete or blocked.",
                    isError = true,
                )
            }
        }
    },
)

private suspend fun createGoal(
    service: GoalService,
    sessionId: String,
    args: JsonObject,
    defaultMaxRounds: () -> Int,
): AgentToolResult {
    val source = service.currentTurnSource(sessionId)
    if (!source.isUserInitiated()) {
        return textResult(
            "create is only allowed when the user directly asks for a goal. Do not create goals on your own.",
            isError = true,
        )
    }
    val objective = args.requiredString("objective")
    val maxRounds = args.intOrNull("maxRounds", "max_goal_rounds") ?: defaultMaxRounds().takeIf { it > 0 }
    val created = service.create(sessionId, objective, maxRounds)
        ?: return textResult("The objective must not be blank.", isError = true)
    return textResult(goalViewJson(created).toString())
}

private suspend fun changeGoal(
    service: GoalService,
    sessionId: String,
    args: JsonObject,
    action: String,
): AgentToolResult {
    val changeFields = when (action) {
        "edit" -> setOf("objective", "maxRounds")
        "blocked" -> setOf("blockedReason")
        else -> emptySet()
    }
    val unexpected = args.keys - (setOf("goalId", "revision", "action") + changeFields)
    if (unexpected.isNotEmpty()) return textResult("action=$action does not accept: ${unexpected.joinToString()}", isError = true)
    val goalId = args.requiredString("goalId", "goal_id")
    val revision = args.requiredInt("revision")
    val current = service.goals.value[sessionId]
        ?: return textResult("No goal exists for this session. Use create_goal first.", isError = true)
    if (current.goal.id != goalId) {
        return textResult(
            "goalId does not match the current goal (${current.goal.id}).",
            isError = true,
        )
    }
    if (current.goal.revision != revision) {
        return textResult(
            "revision does not match the current goal revision (${current.goal.revision}). Call get_goal again.",
            isError = true,
        )
    }
    val source = service.currentTurnSource(sessionId)
    return when (action) {
        "edit" -> {
            if (!source.isUserInitiated()) {
                return textResult(
                    "edit is only allowed when the user directly asks. Continue the current turn.",
                    isError = true,
                )
            }
            val objective = args.stringOrNull("objective")?.trim()?.takeIf { it.isNotEmpty() }
            val maxRounds = args.intOrNull("maxRounds", "max_goal_rounds")
            if (objective == null && maxRounds == null) {
                return textResult("edit requires objective or maxRounds.", isError = true)
            }
            val updated = service.edit(sessionId, objective, maxRounds)
                ?: return textResult(
                    "The goal could not be edited. The objective must not be blank.",
                    isError = true,
                )
            textResult(goalViewJson(updated).toString())
        }

        "pause" -> {
            if (!source.isUserInitiated()) {
                return textResult(
                    "pause is only allowed when the user directly asks. Continue the current turn.",
                    isError = true,
                )
            }
            val updated = service.pause(sessionId)
                ?: return textResult("No goal exists for this session.", isError = true)
            textResult(goalViewJson(updated).toString())
        }

        "resume" -> {
            if (!source.isUserInitiated()) {
                return textResult(
                    "resume is only allowed when the user directly asks. Continue the current turn.",
                    isError = true,
                )
            }
            val updated = service.resume(sessionId)
                ?: return textResult(resumeFailureMessage(current.goal), isError = true)
            textResult(goalViewJson(updated).toString())
        }

        "complete" -> {
            if (source != TurnSource.USER && source != TurnSource.RETRY && source != TurnSource.GOAL) {
                return textResult(
                    "complete is only allowed during a user turn or a goal round.",
                    isError = true,
                )
            }
            if (current.goal.phase == GoalPhase.COMPLETE) {
                return textResult("The goal is already complete.", isError = true)
            }
            val updated = service.complete(sessionId)
                ?: return textResult("The goal could not be completed.", isError = true)
            textResult(goalViewJson(updated).toString())
        }

        "blocked" -> {
            if (source != TurnSource.GOAL) {
                return textResult(
                    "blocked is only allowed during a goal round. Continue working toward the objective.",
                    isError = true,
                )
            }
            if (current.goal.roundsStarted < GoalService.MIN_BLOCKED_ROUNDS) {
                return textResult(
                    "The same blocker has not persisted for at least 3 rounds. Continue working toward the objective.",
                    isError = true,
                )
            }
            val reason = args.requiredString("blockedReason", "blocked_reason")
            val updated = service.block(sessionId, reason)
                ?: return textResult(
                    "The goal could not be blocked. Continue working toward the objective.",
                    isError = true,
                )
            textResult(goalViewJson(updated).toString())
        }

        else -> textResult(
            "action must be get, create, edit, pause, resume, complete or blocked.",
            isError = true,
        )
    }
}

private fun resumeFailureMessage(goal: Goal): String = when {
    goal.phase == GoalPhase.COMPLETE -> "The goal is already complete."
    goal.maxRounds > 0 && goal.roundsStarted >= goal.maxRounds ->
        "The goal has no rounds left (${goal.roundsStarted} of ${goal.maxRounds} used). Raise maxRounds with update_goal action=edit before resuming."
    else -> "The goal is already active. Use get_goal to read its state."
}

private fun goalViewJson(view: GoalView?): JsonObject = buildJsonObject {
    if (view == null) {
        put("goal", JsonNull)
    } else {
        put("goal", buildJsonObject {
            put("id", view.goal.id)
            put("revision", view.goal.revision)
            put("objective", view.goal.objective)
            put("phase", view.goal.phase.name)
            put("roundsStarted", view.goal.roundsStarted)
            put("maxRounds", view.goal.maxRounds)
            if (view.goal.blockedReason == null) {
                put("blockedReason", JsonNull)
            } else {
                put("blockedReason", view.goal.blockedReason)
            }
            put("createdAt", view.goal.createdAt)
            put("updatedAt", view.goal.updatedAt)
        })
    }
    putJsonObject("activation") { put("armed", view?.armed ?: false) }
}

private suspend fun goalToolResult(block: suspend () -> AgentToolResult): AgentToolResult =
    try {
        block()
    } catch (error: RequestAbortedException) {
        throw error
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        textResult(error.message ?: "Goal operation failed", isError = true)
    }

private fun TurnSource?.isUserInitiated(): Boolean =
    this == TurnSource.USER || this == TurnSource.RETRY

private fun JsonObject.stringOrNull(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.intOrNull(vararg names: String): Int? {
    names.forEach { name ->
        val value = stringOrNull(name) ?: return@forEach
        return value.toIntOrNull() ?: value.toDoubleOrNull()?.toInt()
    }
    return null
}

private fun JsonObject.requiredString(vararg names: String): String =
    names.firstNotNullOfOrNull { name -> stringOrNull(name)?.trim()?.takeIf { it.isNotEmpty() } }
        ?: throw IllegalArgumentException("Missing required parameter: ${names.first()}")

private fun JsonObject.requiredInt(vararg names: String): Int =
    intOrNull(*names) ?: throw IllegalArgumentException("Missing or invalid required parameter: ${names.first()}")
