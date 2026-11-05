package io.github.whywood.deepdeep.plugin.host.events

import dev.pi.agent.AgentToolResult
import dev.pi.ai.Content
import dev.pi.ai.ImageContent
import dev.pi.ai.TextContent
import io.github.whywood.deepdeep.plugin.api.PluginToolResult
import io.github.whywood.deepdeep.plugin.api.ToolBlock
import java.util.Base64
import kotlinx.serialization.json.JsonObject

internal fun AgentToolResult.toPluginToolResult(): PluginToolResult {
    val blocks = content.map { item -> item.toToolBlock() }
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

internal fun PluginToolResult.toAgentToolResult(): AgentToolResult = when (this) {
    is PluginToolResult.Text -> AgentToolResult(
        content = listOf(TextContent(text)),
        details = details,
        isError = isError.takeIf { it },
    )

    is PluginToolResult.Rich -> AgentToolResult(
        content = blocks.map { block -> block.toContent() },
        details = details,
        isError = isError.takeIf { it },
    )
}

private fun Content.toToolBlock(): ToolBlock = when (this) {
    is TextContent -> ToolBlock.Text(text)
    is ImageContent -> ToolBlock.Image(
        data = runCatching { Base64.getDecoder().decode(data) }.getOrDefault(ByteArray(0)),
        mimeType = mimeType,
    )

    else -> ToolBlock.Text(toString())
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
