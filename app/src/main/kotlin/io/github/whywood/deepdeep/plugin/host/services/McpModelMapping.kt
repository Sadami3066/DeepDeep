package io.github.whywood.deepdeep.plugin.host.services

import io.github.whywood.deepdeep.plugin.api.McpCallResult
import io.github.whywood.deepdeep.plugin.api.McpTool
import io.github.whywood.deepdeep.plugin.api.ToolBlock
import java.util.Base64
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal object McpModelMapping {
    fun tool(tool: io.github.whywood.deepdeep.sdk.mcp.protocol.McpTool): McpTool = McpTool(
        name = tool.name,
        description = tool.description,
        inputSchema = tool.inputSchema,
    )

    fun result(result: io.github.whywood.deepdeep.sdk.mcp.protocol.McpCallResult): McpCallResult = McpCallResult(
        blocks = result.content.map(::block),
        isError = result.isError,
    )

    private fun block(content: JsonObject): ToolBlock {
        val type = (content["type"] as? JsonPrimitive)?.contentOrNull
        return when (type) {
            "image" -> {
                val data = (content["data"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                val mimeType = (content["mimeType"] as? JsonPrimitive)?.contentOrNull ?: "image/png"
                val bytes = runCatching { Base64.getDecoder().decode(data) }.getOrDefault(ByteArray(0))
                ToolBlock.Image(bytes, mimeType)
            }

            else -> {
                val text = (content["text"] as? JsonPrimitive)?.contentOrNull
                ToolBlock.Text(text ?: content.toString())
            }
        }
    }
}
