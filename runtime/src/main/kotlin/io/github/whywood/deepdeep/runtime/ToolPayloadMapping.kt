package io.github.whywood.deepdeep.runtime

import dev.pi.agent.AgentToolResult
import dev.pi.ai.Content
import dev.pi.ai.ImageContent
import dev.pi.ai.TextContent
import kotlinx.serialization.json.JsonObject

internal fun AgentToolResult.toToolResultPayload(): ToolResultPayload = content.toToolResultPayload()

internal fun List<Content>.toToolResultPayload(): ToolResultPayload {
    val blocks = mapNotNull { content ->
        when (content) {
            is TextContent -> ToolResultPayload.Block.Text(content.text)
            is ImageContent -> ToolResultPayload.Block.Image(content.data, content.mimeType)
            else -> null
        }
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
