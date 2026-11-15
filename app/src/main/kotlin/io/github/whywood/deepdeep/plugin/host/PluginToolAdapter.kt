package io.github.whywood.deepdeep.plugin.host

import io.github.whywood.deepdeep.sdk.agent.AgentToolResult
import io.github.whywood.deepdeep.sdk.agent.AgentToolUpdateCallback
import io.github.whywood.deepdeep.sdk.ai.RequestAbortedException
import io.github.whywood.deepdeep.sdk.ai.RunSignal
import io.github.whywood.deepdeep.sdk.coding.tools.CodingTool
import io.github.whywood.deepdeep.plugin.api.PluginRunSignal
import io.github.whywood.deepdeep.plugin.api.PluginTool
import io.github.whywood.deepdeep.plugin.api.PluginToolCall
import io.github.whywood.deepdeep.plugin.api.PluginToolResult
import io.github.whywood.deepdeep.runtime.DeferredTool
import io.github.whywood.deepdeep.plugin.host.events.toAgentToolResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

internal object PluginToolAdapter {
    fun toCodingTool(
        tool: PluginTool,
        showCard: Boolean = false,
        sessionId: () -> String = { "" },
    ): CodingTool = CodingTool(
        name = tool.name,
        description = tool.description,
        parameters = tool.parameters,
        label = tool.label,
        promptSnippet = tool.promptSnippet,
        promptGuidelines = tool.promptGuidelines.takeIf { it.isNotEmpty() },
        execute = { callId, args, signal, onUpdate ->
            execute(tool, callId, args, signal, onUpdate, sessionId, showCard)
        },
    )

    fun toCodingTools(
        tools: List<PluginTool>,
        sessionId: () -> String = { "" },
        showCard: (String) -> Boolean = { false },
    ): List<CodingTool> = tools.map { tool -> toCodingTool(tool, showCard(tool.name), sessionId) }

    fun toDeferredTool(tool: PluginTool, source: String, sessionId: () -> String = { "" }): DeferredTool =
        DeferredTool(
            name = tool.name,
            description = tool.description,
            parameters = tool.parameters,
            source = source,
        ) { callId, args, signal, onUpdate ->
            execute(tool, callId, args, signal, onUpdate, sessionId, false)
        }

    private suspend fun execute(
        tool: PluginTool,
        callId: String,
        args: JsonObject,
        signal: RunSignal?,
        onUpdate: AgentToolUpdateCallback?,
        sessionId: () -> String,
        showCard: Boolean,
    ): AgentToolResult = coroutineScope {
        signal?.throwIfAborted()
        val pluginSignal = signal?.let(::PluginRunSignalAdapter)
        val resolvedSessionId = sessionId()
        if (onUpdate == null) {
            runTool(showCard, callId) {
                tool.execute(HostPluginToolCall(callId, args, pluginSignal, resolvedSessionId, null))
            }
        } else {
            val updates = Channel<PluginToolResult>(Channel.UNLIMITED)
            val forward = launch {
                for (partial in updates) onUpdate(partial.toAgentToolResult())
            }
            val result = runTool(showCard, callId) {
                tool.execute(
                    HostPluginToolCall(callId, args, pluginSignal, resolvedSessionId) { partial ->
                        updates.trySend(partial)
                    },
                )
            }
            updates.close()
            forward.join()
            result
        }
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
        showCard: Boolean,
        callId: String,
        block: suspend () -> PluginToolResult,
    ): AgentToolResult = try {
        block().toAgentToolResult().withCardHint(showCard, callId)
    } catch (error: RequestAbortedException) {
        throw error
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        toolError(error.message ?: error.javaClass.simpleName)
    }

    private fun toolError(message: String): AgentToolResult =
        AgentToolResult(content = listOf(io.github.whywood.deepdeep.sdk.ai.TextContent(message)), isError = true)

    private fun AgentToolResult.withCardHint(showCard: Boolean, callId: String): AgentToolResult {
        if (!showCard || isError == true) return this
        content = content + io.github.whywood.deepdeep.sdk.ai.TextContent("\n\n卡片 id：$callId")
        return this
    }
}
