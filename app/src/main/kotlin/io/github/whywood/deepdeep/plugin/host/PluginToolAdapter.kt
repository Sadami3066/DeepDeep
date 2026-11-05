package io.github.whywood.deepdeep.plugin.host

import dev.pi.agent.AgentToolResult
import dev.pi.agent.AgentToolUpdateCallback
import dev.pi.ai.Content
import dev.pi.ai.ImageContent
import dev.pi.ai.RequestAbortedException
import dev.pi.ai.RunSignal
import dev.pi.ai.TextContent
import dev.pi.coding.tools.CodingTool
import io.github.whywood.deepdeep.plugin.api.PluginTool
import io.github.whywood.deepdeep.plugin.api.PluginToolCall
import io.github.whywood.deepdeep.plugin.api.PluginRunSignal
import io.github.whywood.deepdeep.plugin.api.PluginToolResult
import io.github.whywood.deepdeep.plugin.api.ToolBlock
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

internal object PluginToolAdapter {
    fun toCodingTool(tool: PluginTool, sessionId: String = ""): CodingTool = CodingTool(
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

    fun toCodingTools(tools: List<PluginTool>, sessionId: String = ""): List<CodingTool> = tools.map { toCodingTool(it, sessionId) }

    private suspend fun execute(
        tool: PluginTool,
        callId: String,
        args: JsonObject,
        signal: RunSignal?,
        onUpdate: AgentToolUpdateCallback?,
        sessionId: String,
    ): AgentToolResult = coroutineScope {
        signal?.throwIfAborted()
        val pluginSignal = signal?.let(::PluginRunSignalAdapter)
        if (onUpdate == null) {
            runTool { tool.execute(PluginToolCall(callId, args, pluginSignal, sessionId)) }
        } else {
            val updates = Channel<PluginToolResult>(Channel.UNLIMITED)
            val forward = launch {
                for (partial in updates) onUpdate(partial.toAgentResult())
            }
            val result = runTool {
                tool.execute(PluginToolCall(callId, args, pluginSignal, sessionId) { partial -> updates.trySend(partial) })
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
        block().toAgentResult()
    } catch (error: RequestAbortedException) {
        throw error
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        toolError(error.message ?: error.javaClass.simpleName)
    }

    private fun PluginToolResult.toAgentResult(): AgentToolResult = when (this) {
        is PluginToolResult.Text -> AgentToolResult(
            content = listOf(TextContent(text)),
            details = details,
            isError = isError.takeIf { it },
        )

        is PluginToolResult.Rich -> AgentToolResult(
            content = blocks.map { it.toContent() },
            details = details,
            isError = isError.takeIf { it },
        )
    }

    private fun ToolBlock.toContent(): Content = when (this) {
        is ToolBlock.Text -> TextContent(text)
        is ToolBlock.Image -> ImageContent(
            data = Base64.getEncoder().encodeToString(data),
            mimeType = mimeType,
        )

        is ToolBlock.FileRef -> TextContent(fileRefText(path, mimeType))
    }

    private fun fileRefText(path: String, mimeType: String?): String =
        if (mimeType.isNullOrBlank()) "file: $path" else "file: $path ($mimeType)"

    private fun toolError(message: String): AgentToolResult =
        AgentToolResult(content = listOf(TextContent(message)), isError = true)
}
