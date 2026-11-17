package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.sdk.agent.AgentToolResult
import io.github.whywood.deepdeep.sdk.ai.PiJson
import io.github.whywood.deepdeep.sdk.ai.TextContent
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal const val DEEPDEEP_STRUCTURED_CONTENT_VERSION = 1
internal const val DEEPDEEP_STRUCTURED_CONTENT_MAX_BYTES = 256 * 1024
internal const val COMPLETE_PAYLOAD_HEADING = "完整返回（上游原样）："
internal const val GUIDANCE_HEADING = "使用说明："
internal const val CARD_ID_HINT_LABEL = "卡片 id："

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
        val details = effective.details as? JsonObject
        val raw = details.rawText()
        val guidance = details.guidanceText()
        val hasCard = details?.get(ToolResultDetails.CARD_KEY) is JsonObject
        val cardTitle = details.cardTitle()
        val error = effective.isError == true
        var content = effective.content
        var changed = false
        if (raw != null) {
            val blocks = effective.content.filterNot { it is TextContent }
            content = blocks + TextContent("$COMPLETE_PAYLOAD_HEADING\n$raw")
            changed = true
        }
        if (guidance != null) {
            content = content + TextContent("\n\n$GUIDANCE_HEADING\n$guidance")
            changed = true
        }
        if (hasCard && !error && !content.containsCardHint(context.callId)) {
            val title = cardTitle?.takeIf { it.isNotBlank() }?.let { "（$it）" }.orEmpty()
            content = content + TextContent("\n\n$CARD_ID_HINT_LABEL${context.callId}$title")
            changed = true
        }
        val stripKeys = buildList {
            if (raw != null) add(ToolResultDetails.RAW_KEY)
            if (guidance != null) add(ToolResultDetails.GUIDANCE_KEY)
        }
        val cleanedDetails = if (stripKeys.isEmpty() || details == null) {
            effective.details
        } else {
            JsonObject(details - stripKeys.toSet())
        }
        val detailsChanged = cleanedDetails != effective.details
        var result = if (changed || detailsChanged) {
            effective.rebuilt(content, cleanedDetails)
        } else {
            effective
        }
        val structured = result.structuredContent ?: context.result.structuredContent
        if (structured != null) {
            result = result.withStructuredDetails(structured)
            return ToolResultDecision.Replace(result)
        }
        return if (result === effective && decision is ToolResultDecision.Keep) {
            decision
        } else {
            ToolResultDecision.Replace(result)
        }
    }
}

internal fun ToolExecutionHooks?.withPostProcessing(): ToolExecutionHooks =
    ToolResultPostProcessor(this)

private fun JsonObject?.rawText(): String? =
    (this?.get(ToolResultDetails.RAW_KEY) as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

private fun JsonObject?.guidanceText(): String? =
    (this?.get(ToolResultDetails.GUIDANCE_KEY) as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

private fun JsonObject?.cardTitle(): String? =
    ((this?.get(ToolResultDetails.CARD_KEY) as? JsonObject)?.get("title") as? JsonPrimitive)
        ?.contentOrNull
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

private fun List<io.github.whywood.deepdeep.sdk.ai.Content>.containsCardHint(callId: String): Boolean =
    filterIsInstance<TextContent>().any { part ->
        part.text.contains("$CARD_ID_HINT_LABEL$callId") || part.text.contains("$CARD_ID_HINT_LABEL $callId")
    }

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
    return rebuilt(content, JsonObject(details + (ToolResultDetails.DEEPDEEP_KEY to JsonObject(deep))))
}

private fun AgentToolResult.rebuilt(
    content: List<io.github.whywood.deepdeep.sdk.ai.Content>,
    details: JsonElement?,
): AgentToolResult = AgentToolResult(
    content = content,
    details = details,
    structuredContent = structuredContent,
    usage = usage,
    isError = isError,
    terminate = terminate,
)
