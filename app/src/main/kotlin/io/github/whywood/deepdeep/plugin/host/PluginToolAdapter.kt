package io.github.whywood.deepdeep.plugin.host

import dev.pi.agent.AgentToolResult
import dev.pi.agent.AgentToolUpdateCallback
import dev.pi.ai.RequestAbortedException
import dev.pi.ai.RunSignal
import dev.pi.coding.tools.CodingTool
import io.github.whywood.deepdeep.plugin.api.PluginTool
import io.github.whywood.deepdeep.plugin.api.PluginToolCall
import io.github.whywood.deepdeep.plugin.api.PluginRunSignal
import io.github.whywood.deepdeep.plugin.api.PluginToolResult
import io.github.whywood.deepdeep.runtime.DeferredTool
import io.github.whywood.deepdeep.plugin.host.events.toAgentToolResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

internal object PluginToolAdapter {
    fun toCodingTool(tool: PluginTool, sessionId: () -> String = { "" }): CodingTool = CodingTool(
        name = tool.name,
        description = tool.description,
        parameters = tool.parameters,
        label = tool.label,
        promptSnippet = tool.promptSnippet,
        promptGuidelines = tool.promptGuidelines.takeIf { it.isNotEmpty() },
        execute = { callId, args, signal, onUpdate ->
            execute(tool, callId, args, signal, onUpdate, sessionId)
        },
    )

    fun toCodingTools(tools: List<PluginTool>, sessionId: () -> String = { "" }): List<CodingTool> =
        tools.map { toCodingTool(it, sessionId) }

    fun toDeferredTool(tool: PluginTool, source: String, sessionId: () -> String = { "" }): DeferredTool =
        DeferredTool(
            name = tool.name,
            description = tool.description,
            parameters = tool.parameters,
            source = source,
        ) { callId, args, signal, onUpdate ->
            execute(tool, callId, args, signal, onUpdate, sessionId)
        }

    private suspend fun execute(
        tool: PluginTool,
        callId: String,
        args: JsonObject,
        signal: RunSignal?,
        onUpdate: AgentToolUpdateCallback?,
        sessionId: () -> String,
    ): AgentToolResult = coroutineScope {
        signal?.throwIfAborted()
        val pluginSignal = signal?.let(::PluginRunSignalAdapter)
        val resolvedSessionId = sessionId()
        if (onUpdate == null) {
            runTool { tool.execute(PluginToolCall(callId, args, pluginSignal, resolvedSessionId)) }
        } else {
            val updates = Channel<PluginToolResult>(Channel.UNLIMITED)
            val forward = launch {
                for (partial in updates) onUpdate(partial.toAgentToolResult())
            }
            val result = runTool {
                tool.execute(
                    PluginToolCall(callId, args, pluginSignal, resolvedSessionId) { partial -> updates.trySend(partial) },
                )
            }
            updates.close()
            forward.join()
            result
        }
    }

    private class PluginRunSignalAdapter(private val signal: RunSignal) : PluginRunSignal {
        override val cancelled: Boolean get() = signal.isAborted

        override fun throwIfCancelled() {
            signal.throwIfAborted()
        }

        override fun onCancel(listener: () -> Unit): () -> Unit = signal.onAbort(listener)
    }

    private suspend fun runTool(block: suspend () -> PluginToolResult): AgentToolResult = try {
        block().toAgentToolResult()
    } catch (error: RequestAbortedException) {
        throw error
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        toolError(error.message ?: error.javaClass.simpleName)
    }

    private fun toolError(message: String): AgentToolResult =
        AgentToolResult(content = listOf(dev.pi.ai.TextContent(message)), isError = true)
}
