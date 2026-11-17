package io.github.whywood.deepdeep.plugin.api

import kotlinx.serialization.json.JsonElement

public interface McpStructuredCallResult {
    public val blocks: List<ToolBlock>

    public val structuredContent: JsonElement?

    public val isError: Boolean
}

public fun mcpStructuredCallResult(
    blocks: List<ToolBlock>,
    structuredContent: JsonElement?,
    isError: Boolean = false,
): McpStructuredCallResult = McpStructuredCallResultImpl(blocks, structuredContent, isError)

internal data class McpStructuredCallResultImpl(
    override val blocks: List<ToolBlock>,
    override val structuredContent: JsonElement?,
    override val isError: Boolean,
) : McpStructuredCallResult
