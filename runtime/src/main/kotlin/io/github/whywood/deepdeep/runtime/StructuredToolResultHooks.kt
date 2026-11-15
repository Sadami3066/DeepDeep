package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.sdk.agent.AgentToolResult
import io.github.whywood.deepdeep.sdk.ai.PiJson
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal const val DEEPDEEP_STRUCTURED_CONTENT_VERSION = 1
internal const val DEEPDEEP_STRUCTURED_CONTENT_MAX_BYTES = 256 * 1024

internal class StructuredToolResultHooks(
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
        if (structured == null) return decision
        val enriched = effective.withStructuredDetails(structured)
        return ToolResultDecision.Replace(enriched)
    }
}

internal fun ToolExecutionHooks?.withStructuredDetails(): ToolExecutionHooks =
    StructuredToolResultHooks(this)

internal fun AgentToolResult.withStructuredDetails(structured: JsonElement): AgentToolResult {
    val details = details as? JsonObject ?: JsonObject(emptyMap())
    val existing = details[ToolResultDetails.DEEPDEEP_KEY] as? JsonObject ?: JsonObject(emptyMap())
    val encodedSize = runCatching {
        PiJson.encodeToString(JsonElement.serializer(), structured).encodeToByteArray().size
    }.getOrDefault(0)
    val deep = existing.toMutableMap().apply {
        put("version", JsonPrimitive(DEEPDEEP_STRUCTURED_CONTENT_VERSION))
        if (encodedSize > DEEPDEEP_STRUCTURED_CONTENT_MAX_BYTES) {
            put(ToolResultDetails.STRUCTURED_CONTENT_OMITTED_KEY, JsonPrimitive(true))
            put("structuredContentSize", JsonPrimitive(encodedSize))
        } else {
            put(ToolResultDetails.STRUCTURED_CONTENT_KEY, structured)
            put("structuredContentSize", JsonPrimitive(encodedSize))
        }
    }
    return AgentToolResult(
        content = content,
        details = JsonObject(details + (ToolResultDetails.DEEPDEEP_KEY to JsonObject(deep))),
        structuredContent = structuredContent,
        usage = usage,
        isError = isError,
        terminate = terminate,
    )
}
