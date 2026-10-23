package io.github.whywood.deepdeep.runtime

import dev.pi.ai.RequestAbortedException
import dev.pi.agent.AgentToolResult
import dev.pi.coding.tools.CodingTool
import dev.pi.coding.tools.textResult
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
    add(JsonPrimitive("edit"))
    add(JsonPrimitive("pause"))
    add(JsonPrimitive("resume"))
    add(JsonPrimitive("complete"))
    add(JsonPrimitive("blocked"))
}

private val getGoalParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {}
}

private val createGoalParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("objective") {
            put("type", "string")
            put("description", "Concrete objective to keep working toward across turns")
        }
        putJsonObject("max_goal_rounds") {
            put("type", "number")
            put("description", "Optional maximum number of goal rounds before the goal is blocked, between 1 and 50. Omit for unlimited rounds")
        }
    }
    put("required", buildJsonArray { add(JsonPrimitive("objective")) })
}

private val updateGoalParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("goal_id") {
            put("type", "string")
            put("description", "Id of the current goal from get_goal")
        }
        putJsonObject("revision") {
            put("type", "number")
            put("description", "Current goal revision from get_goal; a stale revision is rejected")
        }
        putJsonObject("action") {
            put("type", "string")
            put("enum", GOAL_ACTION_VALUES)
            put("description", "edit, pause, resume, complete or blocked")
        }
        putJsonObject("objective") {
            put("type", "string")
            put("description", "New objective text for action edit")
        }
        putJsonObject("max_goal_rounds") {
            put("type", "number")
            put("description", "New maximum round count for action edit, between 1 and 50. Omit for unlimited")
        }
        putJsonObject("blocked_reason") {
            put("type", "string")
            put("description", "Concrete blocker description, required for action blocked")
        }
    }
    put("required", buildJsonArray {
        add(JsonPrimitive("goal_id"))
        add(JsonPrimitive("revision"))
        add(JsonPrimitive("action"))
    })
}

fun goalTools(binding: SessionBinding, service: GoalService): List<CodingTool> = listOf(
    getGoalTool(binding, service),
    createGoalTool(binding, service),
    updateGoalTool(binding, service),
)

private fun getGoalTool(binding: SessionBinding, service: GoalService): CodingTool = CodingTool(
    name = "get_goal",
    description = "Read the goal for the current conversation, including its objective, revision, phase, round progress and whether it is armed for the next turn.",
    parameters = getGoalParameters,
    label = "get_goal",
    promptSnippet = "Read the current goal",
    promptGuidelines = listOf(
        "Call get_goal before update_goal so the goal id and revision are current.",
        "Do not infer goal state; read it with get_goal.",
    ),
    execute = { _, _, signal, _ ->
        signal?.throwIfAborted()
        goalToolResult {
            val session = binding.current()
                ?: return@goalToolResult textResult("No session is bound. Goal tools require an active session.", isError = true)
            textResult(goalViewJson(service.goals.value[session.sessionId]).toString())
        }
    },
)

private fun createGoalTool(binding: SessionBinding, service: GoalService): CodingTool = CodingTool(
    name = "create_goal",
    description = "Create a goal for the current conversation. The goal is armed immediately, so subsequent turns keep working toward the objective until it is completed, blocked or paused.",
    parameters = createGoalParameters,
    label = "create_goal",
    promptSnippet = "Create a goal for this conversation",
    promptGuidelines = listOf(
        "Use create_goal only when the user directly asks to start a goal.",
        "Never create a goal on your own initiative.",
        "Keep the objective concrete and verifiable.",
    ),
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        goalToolResult {
            val session = binding.current()
                ?: return@goalToolResult textResult("No session is bound. Goal tools require an active session.", isError = true)
            val source = service.currentTurnSource(session.sessionId)
            if (!source.isUserInitiated()) {
                return@goalToolResult textResult(
                    "create_goal is only allowed when the user directly asks for a goal. Do not create goals on your own.",
                    isError = true,
                )
            }
            val objective = args.requiredString("objective")
            val maxRounds = args.intOrNull("max_goal_rounds")
            val created = service.create(session.sessionId, objective, maxRounds)
                ?: return@goalToolResult textResult("The objective must not be blank.", isError = true)
            textResult(goalViewJson(created).toString())
        }
    },
)

private fun updateGoalTool(binding: SessionBinding, service: GoalService): CodingTool = CodingTool(
    name = "update_goal",
    description = "Update the goal for the current conversation. The revision must match the current goal. Actions edit, pause and resume require a direct user request; complete is allowed after the objective is achieved; blocked is only allowed during a goal round after the same blocker persisted for at least 3 rounds.",
    parameters = updateGoalParameters,
    label = "update_goal",
    promptSnippet = "Update the current goal",
    promptGuidelines = listOf(
        "Call get_goal before update_goal so the goal id and revision are current.",
        "Use action complete only after the objective is achieved.",
        "Use action blocked only during a goal round after the same blocker persisted for at least 3 rounds.",
        "Use action edit, pause or resume only when the user directly asks.",
        "Do not stop working toward an active goal without completing or blocking it.",
    ),
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        goalToolResult {
            val session = binding.current()
                ?: return@goalToolResult textResult("No session is bound. Goal tools require an active session.", isError = true)
            val goalId = args.requiredString("goal_id")
            val revision = args.requiredInt("revision")
            val action = args.requiredString("action").lowercase()
            val current = service.goals.value[session.sessionId]
                ?: return@goalToolResult textResult("No goal exists for this session. Use create_goal first.", isError = true)
            if (current.goal.id != goalId) {
                return@goalToolResult textResult(
                    "goal_id does not match the current goal (${current.goal.id}).",
                    isError = true,
                )
            }
            if (current.goal.revision != revision) {
                return@goalToolResult textResult(
                    "revision does not match the current goal revision (${current.goal.revision}). Call get_goal again.",
                    isError = true,
                )
            }
            val source = service.currentTurnSource(session.sessionId)
            when (action) {
                "edit" -> {
                    if (!source.isUserInitiated()) {
                        return@goalToolResult textResult(
                            "update_goal edit is only allowed when the user directly asks. Continue the current turn.",
                            isError = true,
                        )
                    }
                    val objective = args.stringOrNull("objective")?.trim()?.takeIf { it.isNotEmpty() }
                    val maxRounds = args.intOrNull("max_goal_rounds")
                    if (objective == null && maxRounds == null) {
                        return@goalToolResult textResult(
                            "update_goal edit requires objective or max_goal_rounds.",
                            isError = true,
                        )
                    }
                    val updated = service.edit(session.sessionId, objective, maxRounds)
                        ?: return@goalToolResult textResult(
                            "The goal could not be edited. The objective must not be blank.",
                            isError = true,
                        )
                    textResult(goalViewJson(updated).toString())
                }
                "pause" -> {
                    if (!source.isUserInitiated()) {
                        return@goalToolResult textResult(
                            "update_goal pause is only allowed when the user directly asks. Continue the current turn.",
                            isError = true,
                        )
                    }
                    val updated = service.pause(session.sessionId)
                        ?: return@goalToolResult textResult("No goal exists for this session.", isError = true)
                    textResult(goalViewJson(updated).toString())
                }
                "resume" -> {
                    if (!source.isUserInitiated()) {
                        return@goalToolResult textResult(
                            "update_goal resume is only allowed when the user directly asks. Continue the current turn.",
                            isError = true,
                        )
                    }
                    val updated = service.resume(session.sessionId)
                        ?: return@goalToolResult textResult(
                            "The goal cannot be resumed: it is complete, already active or its round limit was reached.",
                            isError = true,
                        )
                    textResult(goalViewJson(updated).toString())
                }
                "complete" -> {
                    if (source != TurnSource.USER && source != TurnSource.RETRY && source != TurnSource.GOAL) {
                        return@goalToolResult textResult(
                            "update_goal complete is only allowed during a user turn or a goal round.",
                            isError = true,
                        )
                    }
                    if (current.goal.phase == GoalPhase.COMPLETE) {
                        return@goalToolResult textResult("The goal is already complete.", isError = true)
                    }
                    val updated = service.complete(session.sessionId)
                        ?: return@goalToolResult textResult("The goal could not be completed.", isError = true)
                    textResult(goalViewJson(updated).toString())
                }
                "blocked" -> {
                    if (source != TurnSource.GOAL) {
                        return@goalToolResult textResult(
                            "update_goal blocked is only allowed during a goal round. Continue working toward the objective.",
                            isError = true,
                        )
                    }
                    if (current.goal.roundsStarted < GoalService.MIN_BLOCKED_ROUNDS) {
                        return@goalToolResult textResult(
                            "The same blocker has not persisted for at least 3 rounds. Continue working toward the objective.",
                            isError = true,
                        )
                    }
                    val reason = args.requiredString("blocked_reason")
                    val updated = service.block(session.sessionId, reason)
                        ?: return@goalToolResult textResult(
                            "The goal could not be blocked. Continue working toward the objective.",
                            isError = true,
                        )
                    textResult(goalViewJson(updated).toString())
                }
                else -> textResult(
                    "action must be edit, pause, resume, complete or blocked.",
                    isError = true,
                )
            }
        }
    },
)

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

private fun JsonObject.intOrNull(name: String): Int? =
    stringOrNull(name)?.let { it.toIntOrNull() ?: it.toDoubleOrNull()?.toInt() }

private fun JsonObject.requiredString(name: String): String =
    stringOrNull(name)?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw IllegalArgumentException("Missing required parameter: $name")

private fun JsonObject.requiredInt(name: String): Int =
    intOrNull(name) ?: throw IllegalArgumentException("Missing or invalid required parameter: $name")
