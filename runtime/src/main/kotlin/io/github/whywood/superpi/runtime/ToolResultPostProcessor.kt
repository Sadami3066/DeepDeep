package io.github.whywood.superpi.runtime

import io.github.whywood.superpi.sdk.agent.AgentToolResult
import io.github.whywood.superpi.sdk.ai.PiJson
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal const val SUPERPI_STRUCTURED_CONTENT_VERSION = 1
internal const val SUPERPI_STRUCTURED_CONTENT_MAX_BYTES = 256 * 1024

internal class ToolResultPostProcessor(
    private val chained: ToolExecutionHooks?,
) : ToolExecutionHooks {
    override suspend fun beforeToolCall(context: ToolCallContext): ToolCallDecision =
        chained?.beforeToolCall(context) ?: ToolCallDecision.Continue

    override suspend fun afterToolResult(context: ToolResultContext): ToolResultDecision {
        val decision = chained?.afterToolResult(context) ?: ToolResultDecision.Keep
        val effective = when (decision) {
            ToolResultDecision.Keep -> context.result
            is ToolResultDecision.Replace -> decision.result
        }
        val structured = effective.structuredContent ?: context.result.structuredContent
            ?: return if (decision is ToolResultDecision.Keep) decision else ToolResultDecision.Replace(effective)
        return ToolResultDecision.Replace(effective.withStructuredDetails(structured))
    }
}

internal fun ToolExecutionHooks?.withPostProcessing(): ToolExecutionHooks =
    ToolResultPostProcessor(this)

internal fun AgentToolResult.withStructuredDetails(structured: JsonElement): AgentToolResult {
    val details = details as? JsonObject ?: JsonObject(emptyMap())
    val existing = details[ToolResultDetails.SUPERPI_KEY] as? JsonObject ?: JsonObject(emptyMap())
    val encodedSize = runCatching {
        PiJson.encodeToString(JsonElement.serializer(), structured).encodeToByteArray().size
    }.getOrDefault(0)
    val deep = existing.toMutableMap().apply {
        put("version", JsonPrimitive(SUPERPI_STRUCTURED_CONTENT_VERSION))
        if (encodedSize > SUPERPI_STRUCTURED_CONTENT_MAX_BYTES) {
            put(ToolResultDetails.STRUCTURED_CONTENT_OMITTED_KEY, JsonPrimitive(true))
            put("structuredContentSize", JsonPrimitive(encodedSize))
        } else {
            put(ToolResultDetails.STRUCTURED_CONTENT_KEY, structured)
            put("structuredContentSize", JsonPrimitive(encodedSize))
        }
    }
    return rebuilt(content, JsonObject(details + (ToolResultDetails.SUPERPI_KEY to JsonObject(deep))))
}

private fun AgentToolResult.rebuilt(
    content: List<io.github.whywood.superpi.sdk.ai.Content>,
    details: JsonElement?,
): AgentToolResult = AgentToolResult(
    content = content,
    details = details,
    structuredContent = structuredContent,
    usage = usage,
    isError = isError,
    terminate = terminate,
)
