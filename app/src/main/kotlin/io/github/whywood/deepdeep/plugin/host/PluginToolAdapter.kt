package io.github.whywood.deepdeep.plugin.host

import io.github.whywood.deepdeep.sdk.agent.AgentToolResult
import io.github.whywood.deepdeep.sdk.agent.AgentToolUpdateCallback
import io.github.whywood.deepdeep.sdk.ai.RequestAbortedException
import io.github.whywood.deepdeep.sdk.ai.RunSignal
import io.github.whywood.deepdeep.sdk.coding.tools.CodingTool
import io.github.whywood.deepdeep.plugin.api.PluginRunSignal
import io.github.whywood.deepdeep.plugin.api.ScreenTarget
import io.github.whywood.deepdeep.plugin.api.PluginTool
import io.github.whywood.deepdeep.plugin.api.PluginToolCall
import io.github.whywood.deepdeep.plugin.api.PluginToolResult
import io.github.whywood.deepdeep.runtime.DeferredTool
import io.github.whywood.deepdeep.plugin.host.events.toAgentToolResult
import io.github.whywood.deepdeep.plugin.host.services.GUI_ACT_PLUGIN_ID
import io.github.whywood.deepdeep.plugin.host.services.PluginScreenEvidence
import io.github.whywood.deepdeep.screen.SCREEN_TARGET_GUIDELINE
import io.github.whywood.deepdeep.sdk.ai.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

internal object PluginToolAdapter {
    fun toCodingTool(
        tool: PluginTool,
        sessionId: () -> String = { "" },
    ): CodingTool = CodingTool(
        name = tool.name,
        description = tool.description,
        parameters = tool.parameters,
        label = tool.label,
        promptSnippet = tool.promptSnippet,
        promptGuidelines = (if (pluginId == GUI_ACT_PLUGIN_ID && tool.name in setOf("screen_act", "screen_probe"))
            tool.promptGuidelines + SCREEN_TARGET_GUIDELINE else tool.promptGuidelines).takeIf { it.isNotEmpty() },
        execute = { callId, args, signal, onUpdate ->
            execute(tool, callId, args, signal, onUpdate, sessionId, pluginId)
        },
    )

    fun toCodingTools(
        tools: List<PluginTool>,
        sessionId: () -> String = { "" },
    ): List<CodingTool> = tools.map { tool -> toCodingTool(tool, sessionId) }

    fun toDeferredTool(tool: PluginTool, source: String, sessionId: () -> String = { "" }): DeferredTool =
        DeferredTool(
            name = tool.name,
            description = tool.description,
            parameters = tool.parameters,
            source = source,
        ) { callId, args, signal, onUpdate ->
            execute(tool, callId, args, signal, onUpdate, sessionId, source.removePrefix("plugin:"))
        }

    private suspend fun execute(
        tool: PluginTool,
        callId: String,
        args: JsonObject,
        signal: RunSignal?,
        onUpdate: AgentToolUpdateCallback?,
        sessionId: () -> String,
        pluginId: String?,
    ): AgentToolResult = coroutineScope {
        signal?.throwIfAborted()
        val pluginSignal = signal?.let(::PluginRunSignalAdapter)
        val resolvedSessionId = sessionId()
        if (onUpdate == null) {
            runTool {
                tool.execute(HostPluginToolCall(callId, args, pluginSignal, resolvedSessionId, null))
            }
        } else {
            val updates = Channel<PluginToolResult>(Channel.UNLIMITED)
            val forward = launch {
                for (partial in updates) onUpdate(partial.toAgentToolResult())
            }
            val result = try {
                executeWithScreenEvidence(tool, pluginId,
                    HostPluginToolCall(callId, args, pluginSignal, resolvedSessionId) { partial ->
                        updates.trySend(partial)
                    },
                )
            } finally {
                updates.close()
            }
            forward.join()
            result
        }
    }

    private suspend fun executeWithScreenEvidence(tool: PluginTool, pluginId: String?, call: PluginToolCall): AgentToolResult {
        if (pluginId != GUI_ACT_PLUGIN_ID || tool.name != "screen_act") return runTool { tool.execute(call) }
        val target = if ((call.args["target"] as? JsonPrimitive)?.contentOrNull.equals("main", ignoreCase = true)) {
            ScreenTarget.MAIN
        } else {
            ScreenTarget.VIRTUAL
        }
        val collector = PluginScreenEvidence(target)
        val steps = call.args["steps"] as? JsonArray
        val instrumented = object : PluginToolCall by call {
            override fun partial(result: PluginToolResult) {
                val index = (result.details?.get("gui_act_step_begin") as? JsonPrimitive)?.intOrNull
                if (index == null) call.partial(result)
                else if (steps != null && index in steps.indices) collector.beginStep(index)
            }
        }
        val result = withContext(collector) { runTool { tool.execute(instrumented) } }
        val actions = collector.actions()
        val first = result.content.firstOrNull() as? TextContent
        val payload = first?.let { runCatching { Json.parseToJsonElement(it.text) as? JsonObject }.getOrNull() }
        if (payload == null) {
            if (actions.isNotEmpty()) result.content += TextContent(JsonObject(mapOf("host_actions" to JsonArray(actions))).toString())
        } else {
            val annotated = (payload["results"] as? JsonArray)?.map { item ->
                val step = item as? JsonObject ?: return@map item
                val index = (step["step"] as? JsonPrimitive)?.intOrNull ?: return@map step
                val expected = ((steps?.getOrNull(index) as? JsonObject)?.get("present") as? JsonPrimitive)?.contentOrNull
                val matches = if (step["op"] == JsonPrimitive("assert") && !expected.isNullOrBlank()) {
                    collector.assertion(index, expected)
                } else null
                val actionIndices = actions.filter { it["step"] == JsonPrimitive(index) }.mapNotNull { it["index"] }
                if (matches == null && actionIndices.isEmpty()) step else JsonObject(step + ("host_verification" to buildJsonObject {
                    if (matches != null) put("matched", matches)
                    if (actionIndices.isNotEmpty()) put("actions", JsonArray(actionIndices))
                }))
            }
            val augmented = payload + ("host_actions" to JsonArray(actions))
            val rendered = JsonObject(if (annotated == null) augmented else augmented + ("results" to JsonArray(annotated)))
            result.content = listOf(TextContent(rendered.toString())) +
                result.content.drop(1)
        }
        return result
    }

    private class HostPluginToolCall(
        override val callId: String,
        override val args: JsonObject,
        override val signal: PluginRunSignal?,
        override val sessionId: String,
        private val update: ((PluginToolResult) -> Unit)? = null,
    ) : PluginToolCall {
        override fun partial(result: PluginToolResult) {
            update?.invoke(result)
        }
    }

    private class PluginRunSignalAdapter(private val signal: RunSignal) : PluginRunSignal {
        override val cancelled: Boolean get() = signal.isAborted

        override fun throwIfCancelled() {
            signal.throwIfAborted()
        }

        override fun onCancel(listener: () -> Unit): () -> Unit = signal.onAbort(listener)
    }

    private suspend fun runTool(
        block: suspend () -> PluginToolResult,
    ): AgentToolResult = try {
        block().toAgentToolResult()
    } catch (error: RequestAbortedException) {
        throw error
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        toolError(error.message ?: error.javaClass.simpleName)
    }

    private fun toolError(message: String): AgentToolResult =
        AgentToolResult(content = listOf(io.github.whywood.deepdeep.sdk.ai.TextContent(message)), isError = true)
}
