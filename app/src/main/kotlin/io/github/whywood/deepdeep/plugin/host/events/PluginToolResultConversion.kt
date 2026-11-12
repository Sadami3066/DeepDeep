package io.github.whywood.deepdeep.plugin.host.events

import io.github.whywood.deepdeep.sdk.agent.AgentToolResult
import io.github.whywood.deepdeep.sdk.ai.Content
import io.github.whywood.deepdeep.sdk.ai.ImageContent
import io.github.whywood.deepdeep.sdk.ai.TextContent
import io.github.whywood.deepdeep.plugin.api.FileRefBlock
import io.github.whywood.deepdeep.plugin.api.ImageBlock
import io.github.whywood.deepdeep.plugin.api.PluginToolResult
import io.github.whywood.deepdeep.plugin.api.TextBlock
import io.github.whywood.deepdeep.plugin.api.ToolBlock
import io.github.whywood.deepdeep.plugin.api.fileRefBlock
import io.github.whywood.deepdeep.plugin.api.imageBlock
import io.github.whywood.deepdeep.plugin.api.richResult
import io.github.whywood.deepdeep.plugin.api.textBlock
import io.github.whywood.deepdeep.plugin.api.textResult
import io.github.whywood.deepdeep.runtime.ToolResultDetails
import io.github.whywood.deepdeep.runtime.ToolResultPayload
import io.github.whywood.deepdeep.runtime.fileRefDisplayText
import java.util.Base64
import kotlinx.serialization.json.JsonObject

internal fun AgentToolResult.toPluginToolResult(): PluginToolResult {
    val blocks = content.map { item -> item.toToolBlock() }
    val details = details as? JsonObject
    val failed = isError == true
    val single = blocks.singleOrNull()
    return if (single is TextBlock) {
        textResult(single.text, details, isError = failed)
    } else {
        richResult(blocks, details, isError = failed)
    }
}

internal fun PluginToolResult.toAgentToolResult(): AgentToolResult {
    val text = text
    return if (text != null) {
        AgentToolResult(
            content = listOf(TextContent(text)),
            details = details,
            isError = isError.takeIf { it },
        )
    } else {
        AgentToolResult(
            content = blocks.map { block -> block.toContent() },
            details = details.withFileRefs(blocks.filterIsInstance<FileRefBlock>()),
            isError = isError.takeIf { it },
        )
    }
}

private fun JsonObject?.withFileRefs(fileRefs: List<FileRefBlock>): JsonObject? {
    if (fileRefs.isEmpty()) return this
    val base = this ?: JsonObject(emptyMap())
    val encoded = ToolResultDetails.encodeFileRefs(
        fileRefs.map { fileRef -> ToolResultPayload.Block.FileRef(fileRef.path, fileRef.mimeType) },
    )
    return JsonObject(base + (ToolResultDetails.FILE_REFS_KEY to encoded))
}

private fun Content.toToolBlock(): ToolBlock = when (this) {
    is TextContent -> textBlock(text)
    is ImageContent -> imageBlock(
        data = runCatching { Base64.getDecoder().decode(data) }.getOrDefault(ByteArray(0)),
        mimeType = mimeType,
    )

    else -> textBlock(toString())
}

private fun ToolBlock.toContent(): Content = when (this) {
    is TextBlock -> TextContent(text)
    is ImageBlock -> ImageContent(
        data = Base64.getEncoder().encodeToString(data),
        mimeType = mimeType,
    )

    is FileRefBlock -> TextContent(fileRefDisplayText(path, mimeType))

    else -> TextContent(toString())
}
