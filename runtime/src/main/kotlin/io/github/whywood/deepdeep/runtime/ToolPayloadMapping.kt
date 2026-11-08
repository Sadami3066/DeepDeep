package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.sdk.agent.AgentToolResult
import io.github.whywood.deepdeep.sdk.ai.Content
import io.github.whywood.deepdeep.sdk.ai.ImageContent
import io.github.whywood.deepdeep.sdk.ai.TextContent
import io.github.whywood.deepdeep.sdk.ai.ToolResultMessage
import kotlinx.serialization.json.JsonObject

internal fun AgentToolResult.toToolResultPayload(): ToolResultPayload =
    content.toToolResultPayload(ToolResultDetails.decodeFileRefs(details))

internal fun ToolResultMessage.toToolResultPayload(): ToolResultPayload =
    content.toToolResultPayload(ToolResultDetails.decodeFileRefs(details))

private fun List<Content>.toToolResultPayload(
    fileRefs: List<ToolResultPayload.Block.FileRef>,
): ToolResultPayload {
    val blocks = buildList {
        this@toToolResultPayload.forEach { content ->
            when (content) {
                is TextContent -> add(ToolResultPayload.Block.Text(content.text))
                is ImageContent -> add(ToolResultPayload.Block.Image(content.data, content.mimeType))
                else -> Unit
            }
        }
        fileRefs.forEach { add(it) }
    }
    if (blocks.isEmpty()) return ToolResultPayload.Empty
    if (blocks.all { it is ToolResultPayload.Block.Text }) {
        return ToolResultPayload.Text(
            blocks.joinToString("") { block -> (block as ToolResultPayload.Block.Text).text },
        )
    }
    return ToolResultPayload.Blocks(blocks)
}

internal fun JsonObject.toDisplayArguments(): Map<String, String> = entries.associate { (key, value) ->
    key to value.toString().trim('"')
}
