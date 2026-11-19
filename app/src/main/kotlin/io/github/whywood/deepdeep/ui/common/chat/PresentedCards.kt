package io.github.whywood.deepdeep.ui.common.chat

import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantStep
import io.github.whywood.deepdeep.runtime.ToolActivityStatus
import io.github.whywood.deepdeep.plugin.api.Cards
import io.github.whywood.deepdeep.runtime.ToolTrace
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal sealed interface PresentedCardRef {
    data class Payload(
        val customType: String,
        val payload: JsonObject,
        val title: String?,
    ) : PresentedCardRef

    data class Tool(val trace: ToolTrace) : PresentedCardRef
}

internal fun presentedCardLookup(messages: List<AssistantMessage>): Map<String, PresentedCardRef> = buildMap {
    messages.forEach { message ->
        message.steps.forEach { step ->
            if (step !is AssistantStep.Tool) return@forEach
            val key = step.trace.toolCallId.trim()
            if (key.isEmpty()) return@forEach
            presentedCardRef(step.trace)?.let { card -> put(key, card) }
        }
    }
}

private fun presentedCardRef(trace: ToolTrace): PresentedCardRef? {
    cardDescriptor(trace)?.let { return it }
    if (trace.status != ToolActivityStatus.DONE) return null
    return PresentedCardRef.Tool(trace)
}

private fun cardDescriptor(trace: ToolTrace): PresentedCardRef.Payload? {
    val descriptor = trace.resultDetails?.get(Cards.KEY) as? JsonObject ?: return null
    val customType = descriptor.string("customType") ?: return null
    val payload = descriptor["payload"] as? JsonObject ?: return null
    return PresentedCardRef.Payload(
        customType = customType,
        payload = payload,
        title = descriptor.string("title"),
    )
}

private fun JsonObject.string(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
