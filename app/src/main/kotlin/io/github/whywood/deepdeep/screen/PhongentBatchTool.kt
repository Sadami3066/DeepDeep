package io.github.whywood.deepdeep.screen

import com.wangzan101.mika.core.agent.BatchActionOutcome
import com.wangzan101.mika.core.agent.BatchActionPort
import com.wangzan101.mika.core.agent.BatchEvent
import com.wangzan101.mika.core.agent.BatchExecution
import com.wangzan101.mika.core.agent.BatchRecorder
import com.wangzan101.mika.core.agent.BatchRunner
import com.wangzan101.mika.core.agent.BatchScreenPort
import com.wangzan101.mika.core.agent.BatchToolCall
import com.wangzan101.mika.core.agent.DO_STEPS_TOOL
import com.wangzan101.mika.core.guard.OperationGuards
import com.wangzan101.mika.core.record.Redactor
import com.wangzan101.mika.core.run.SteerSignal
import com.wangzan101.mika.core.run.waitOrSteer
import com.wangzan101.mika.core.tool.Until
import com.wangzan101.mika.core.tool.UntilOutcome
import com.wangzan101.mika.core.uistate.ScreenSnapshot
import com.wangzan101.mika.core.uistate.UntilWait
import com.wangzan101.mika.core.uistate.UntilWaitPort
import io.github.whywood.deepdeep.sdk.agent.AgentToolResult
import io.github.whywood.deepdeep.sdk.ai.RequestAbortedException
import io.github.whywood.deepdeep.sdk.ai.RunSignal
import io.github.whywood.deepdeep.sdk.ai.TextContent
import io.github.whywood.deepdeep.sdk.coding.tools.CodingTool
import io.github.whywood.deepdeep.sdk.coding.tools.textResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.time.TimeSource
import kotlin.coroutines.coroutineContext

private val batchableScreenTools = listOf(
    "screen_tap", "screen_swipe", "screen_scroll", "screen_type", "screen_key", "screen_wait", "screen_launch",
)

internal fun phongentBatchTool(
    resolve: (String) -> ScreenController?,
    toolsForTarget: (String) -> Map<String, CodingTool>,
    steerPending: () -> Boolean = { false },
    executeStep: suspend (String, CodingTool, JsonObject, RunSignal?) -> AgentToolResult =
        { id, tool, args, signal -> tool.execute(id, args, signal, null) },
    steerSignal: StateFlow<Boolean>? = null,
): CodingTool {
    val spec = BatchRunner.spec(batchableScreenTools)
    val schema = Json.parseToJsonElement(spec.parametersJsonSchema).jsonObject
    val properties = schema.getValue("properties").jsonObject
    val targetSchema = buildJsonObject {
        put("type", "string")
        put("enum", JsonArray(listOf(JsonPrimitive("main"), JsonPrimitive("virtual"))))
        put("default", "virtual")
    }
    val baseSchema = buildJsonObject {
        put("type", "string")
        put("description", "Optional snapshotId from screen_observe(interface=true), used for steps with a stable ref unless the step specifies its own snapshotId.")
    }
    return CodingTool(
        name = DO_STEPS_TOOL,
        label = DO_STEPS_TOOL,
        promptGuidelines = listOf(SCREEN_TARGET_GUIDELINE),
        description = spec.description
            .replace("app package or page address", "app package")
            .replace("Inspect the final screen before deciding what to do next.", "Inspect the returned final screen, or use screen_observe if a read was unavailable."),
        parameters = JsonObject(schema + ("properties" to JsonObject(properties + mapOf(
            "target" to targetSchema,
            "snapshotId" to baseSchema,
        )))),
        execute = execute@{ id, args, signal, update ->
            signal?.throwIfAborted()
            val pending = steerSignal ?: coroutineContext[SteerSignal]?.pending
            fun steerWaiting() = steerPending() || pending?.value == true
            val target = if ("target" in args) args.string("target") else "virtual"
            if (target != "main" && target != "virtual") {
                return@execute textResult(batchRejection("target: must be main or virtual").toString(), isError = true)
            }
            val controller = resolve(target) ?: return@execute textResult(
                batchRejection("Screen target unavailable: $target").toString(), isError = true,
            )
            val batchBase = args.string("snapshotId")
            if ("snapshotId" in args && batchBase.isNullOrBlank()) return@execute textResult(
                batchRejection("snapshotId: must be a non-blank string").toString(), isError = true,
            )
            val tools = toolsForTarget(target)
            val (steps, invalid) = BatchRunner.parse(
                JsonObject(args - "target" - "snapshotId").toString(), tools.keys, batchableScreenTools,
            )
            if (steps == null) {
                return@execute textResult(batchRejection(requireNotNull(invalid)).toString(), isError = true)
            }
            val wrongTarget = steps.indexOfFirst { step ->
                val stepArgs = Json.parseToJsonElement(step.argsJson).jsonObject
                "target" in stepArgs && stepArgs.string("target") != target
            }
            if (wrongTarget >= 0) return@execute textResult(
                batchRejection("Step ${wrongTarget + 1} acts on a different screen. One do_steps call stays on one screen.", "mixed_surface")
                    .toString(), isError = true,
            )
            suspend fun runBatch(): JsonObject {
                val elapsed = TimeSource.Monotonic.markNow()
                val clock = { elapsed.elapsedNow().inWholeMilliseconds }
                val guards = OperationGuards()
                var batch: BatchEvent? = null
                val recorder = BatchRecorder(now = clock, event = { batch = it })
                val executor = object : BatchActionPort {
                    override val recorder = recorder
                    override val confirms = false

                    override suspend fun run(call: BatchToolCall, step: Int): BatchActionOutcome {
                        signal?.throwIfAborted()
                        val original = Json.parseToJsonElement(call.argsJson).jsonObject
                        val routed = buildJsonObject {
                            original.forEach { (key, value) -> put(key, value) }
                            put("target", target)
                            if (batchBase != null && original.string("ref")?.startsWith("@") == true && "snapshotId" !in original) {
                                put("snapshotId", batchBase)
                            }
                        }
                        val before = guards.beforeAction(call.name, original)
                        val tool = tools.getValue(call.name)
                        val result = awaitStep(call, tool, routed, signal, executeStep)
                        val fields = result.toBatchFields(call.name)
                        val after = buildList {
                            if (call.name != "screen_wait") {
                                guards.noteAction()
                                (fields["effect"] as? JsonObject)?.string("kind")?.let { addAll(guards.effect(it)) }
                            }
                            addAll(guards.result(call.name, fields))
                        }
                        update?.invoke(textResult(buildJsonObject {
                            put("step", call.id.substringAfterLast('.').toInt())
                            put("tool", call.name)
                            put("ok", fields["ok"] ?: JsonPrimitive(false))
                            fields["effect"]?.let { put("effect", it) }
                        }.toString()))
                        val waited = if (call.name == "screen_wait") {
                            (fields["elapsedMs"] as? JsonPrimitive)?.longOrNull ?: 0L
                        } else 0L
                        return BatchActionOutcome.Ran(
                            BatchExecution(fields, changed = fields.boolean("changed"), waitMs = waited), before, after,
                        )
                    }
                }
                val port = object : BatchScreenPort {
                    override suspend fun settle(timeoutMs: Long) {
                        waitOrSteer { controller.settleForEffect(timeoutMs) ?: false }
                    }

                    override suspend fun waitUntil(until: Until, clock: () -> Long, interrupted: () -> Boolean) =
                        waitPhongentScreen(controller, until, interrupted, clock)
                }
                val runner = BatchRunner(port, guards, executor, steerWaiting = ::steerWaiting)
                val execution = runner.run(steps, id)
                val postBatch = if (execution.content["screen"] == null && execution.content.boolean("ok") == true &&
                    !steerWaiting()
                ) {
                    tools["screen_observe"]?.let { observe ->
                        val observation = waitOrSteer {
                            executeStep("$id.final-screen", observe, buildJsonObject {
                                put("target", target)
                                put("interface", true)
                                batchBase?.let { put("baseSnapshotId", it) }
                            }, signal)
                        }
                        observation?.takeUnless { it.isError == true }?.jsonPayload()
                    }
                } else null
                return JsonObject(execution.content + mapOf(
                    "target" to JsonPrimitive(target),
                    "waited_ms" to JsonPrimitive(runner.waitedMs),
                    "elapsed_ms" to JsonPrimitive(clock()),
                    "action_elapsed_ms" to JsonPrimitive(batch?.atMs ?: clock()),
                ) + if (postBatch == null) emptyMap() else mapOf("screen" to postBatch))
            }
            val payload = coroutineScope {
                val job = async {
                    if (steerSignal != null) withContext(SteerSignal(steerSignal)) { runBatch() } else runBatch()
                }
                val remove = signal?.onAbort { job.cancel() }
                try {
                    job.await().also { signal?.throwIfAborted() }
                } catch (error: CancellationException) {
                    if (signal?.isAborted == true) throw RequestAbortedException(signal.reason)
                    throw error
                } finally {
                    remove?.invoke()
                }
            }
            AgentToolResult(
                content = listOf(TextContent(payload.toString())),
                structuredContent = payload,
                isError = payload["ok"] != JsonPrimitive(true),
            )
        },
    )
}

private suspend fun awaitStep(
    call: BatchToolCall,
    tool: CodingTool,
    args: JsonObject,
    signal: RunSignal?,
    executeStep: suspend (String, CodingTool, JsonObject, RunSignal?) -> AgentToolResult,
): AgentToolResult {
    suspend fun execute() = executeStep(call.id, tool, args, signal)
    return if (call.name == "screen_wait") {
        waitOrSteer { execute() } ?: textResult(batchRejection("Interrupted by a new message", BatchRunner.NEW_MESSAGE).toString())
    } else {
        execute()
    }
}

private fun AgentToolResult.toBatchFields(tool: String): JsonObject {
    val result = jsonPayload() ?: JsonObject(emptyMap())
    return buildJsonObject {
        result.forEach { (key, value) -> put(key, value) }
        val ok = isError != true && result.boolean("ok") != false
        put("ok", ok)
        if (!ok && result["error"] == null) put(
            "error", result.string("reason") ?: result.string("message")
                ?: content.filterIsInstance<TextContent>().firstOrNull()?.text?.let { Redactor.text(it).take(300) } ?: "failed",
        )
        val effect = result["effect"] as? JsonObject
        if (tool == "screen_type" && result.string("verification") != "match" &&
            result.boolean("secret") != true && effect?.string("readback") != null &&
            effect.boolean("readbackMatches") == false
        ) {
            put("verification", "mismatch")
        }
        if (effect != null && effect.string("kind") == "place_changed" && tool != "screen_launch") {
            effect.string("to")?.let { put("app_switched", it) }
        }
        if (effect?.boolean("treeBlind") == true) {
            val screen = result["screen"] as? JsonObject
            put("screen", JsonObject(screen.orEmpty() + ("readable" to JsonPrimitive(false))))
        }
    }
}

private fun AgentToolResult.jsonPayload(): JsonObject? =
    structuredContent as? JsonObject ?: content.filterIsInstance<TextContent>().firstNotNullOfOrNull {
        runCatching { Json.parseToJsonElement(it.text) as? JsonObject }.getOrNull()
    }

private fun batchRejection(message: String, code: String = "invalid_arguments") = buildJsonObject {
    put("ok", false)
    put("error", code)
    put("message", message)
    put("completed", 0)
}

private fun JsonObject.string(name: String) = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

private fun JsonObject.boolean(name: String) = (this[name] as? JsonPrimitive)?.booleanOrNull

internal suspend fun waitPhongentScreen(
    controller: ScreenController,
    condition: Until,
    interrupted: () -> Boolean = { false },
    clock: () -> Long = TimeSource.Monotonic.markNow().let { mark -> { mark.elapsedNow().inWholeMilliseconds } },
): UntilOutcome {
    val start = clock()
    var complete = false
    suspend fun read(): ScreenSnapshot? = try {
        controller.withScreenExecution {
            controller.observe(SCREEN_DEFAULT_MAX_DEPTH, GUI_MAX_NODES, null).snapshot.let { snapshot ->
                complete = !snapshot.truncated
                snapshot.toEffectSnapshot(controller.effectDisplayInfo())
            }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        complete = false
        null
    }
    val port = object : UntilWaitPort {
        override suspend fun waitStable(timeoutMs: Long): Boolean = controller.settleForEffect(timeoutMs) ?: run {
            val first = read() ?: return false
            val wasComplete = complete
            delay(minOf(500L, timeoutMs))
            val after = read() ?: return false
            wasComplete && complete && first.packageName == after.packageName &&
                first.elements.map { it.copy(ref = "") } == after.elements.map { it.copy(ref = "") }
        }

        override suspend fun awaitChange(timeoutMs: Long): Boolean = controller.awaitScreenChange(timeoutMs) ?: run {
            delay(timeoutMs)
            false
        }
    }
    val budget = condition.timeoutMs.coerceIn(0L, Until.MAX_MS)
    val wait = UntilWait(port, ::read, clock, interrupted, readWasComplete = { complete })
    return if (budget == 0L) wait.run(condition) else (
        withTimeoutOrNull(budget) { wait.run(condition) }
            ?: UntilOutcome(false, UntilOutcome.TIMEOUT, (clock() - start).coerceAtLeast(0))
    )
}
