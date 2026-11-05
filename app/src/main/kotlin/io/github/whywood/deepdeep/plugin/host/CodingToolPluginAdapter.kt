package io.github.whywood.deepdeep.plugin.host

import dev.pi.agent.AgentToolResult
import dev.pi.ai.ImageContent
import dev.pi.ai.RunSignal
import dev.pi.ai.TextContent
import dev.pi.coding.tools.CodingTool
import io.github.whywood.deepdeep.plugin.api.PluginRunSignal
import io.github.whywood.deepdeep.plugin.api.PluginTool
import io.github.whywood.deepdeep.plugin.api.PluginToolResult
import io.github.whywood.deepdeep.plugin.api.ToolBlock
import java.util.Base64
import kotlinx.serialization.json.JsonObject

internal object CodingToolPluginAdapter {
    fun toPluginTools(tools: List<CodingTool>): List<PluginTool> = tools.map(::toPluginTool)

    fun toPluginTool(tool: CodingTool): PluginTool = PluginTool(
        name = tool.name,
        label = tool.label,
        description = tool.description,
        parameters = tool.parameters,
        promptSnippet = tool.promptSnippet,
        promptGuidelines = tool.promptGuidelines.orEmpty(),
    ) { call ->
        val signal = call.signal?.let(::runSignalFor)
        val result = tool.execute(call.callId, call.args, signal) { partial ->
            call.partial(partial.toPluginResult())
        }
        result.toPluginResult()
    }

    private fun runSignalFor(source: PluginRunSignal): RunSignal {
        val signal = RunSignal()
        if (source.cancelled) signal.abort()
        source.onCancel { signal.abort() }
        return signal
    }

    private fun AgentToolResult.toPluginResult(): PluginToolResult {
        val blocks = content.map { item ->
            when (item) {
                is TextContent -> ToolBlock.Text(item.text)
                is ImageContent -> ToolBlock.Image(
                    data = runCatching { Base64.getDecoder().decode(item.data) }.getOrDefault(ByteArray(0)),
                    mimeType = item.mimeType,
                )

                else -> ToolBlock.Text(item.toString())
            }
        }
        val details = details as? JsonObject
        val failed = isError == true
        return if (blocks.size == 1 && blocks.first() is ToolBlock.Text) {
            PluginToolResult.Text(
                text = (blocks.first() as ToolBlock.Text).text,
                details = details,
                isError = failed,
            )
        } else {
            PluginToolResult.Rich(blocks = blocks, details = details, isError = failed)
        }
    }
}
