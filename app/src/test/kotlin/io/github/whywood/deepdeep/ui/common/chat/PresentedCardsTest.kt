package io.github.whywood.deepdeep.ui.common.chat

import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.AssistantStep
import io.github.whywood.deepdeep.runtime.ToolActivityStatus
import io.github.whywood.deepdeep.runtime.ToolTrace
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PresentedCardsTest {
    @Test
    fun payloadCardsResolveByToolCallId() {
        val message = assistant(
            tool("call-1", details = cardDetails("sysu.card")),
            tool("call-2"),
        )

        val cards = presentedCardLookup(listOf(message))

        val payload = cards.getValue("call-1") as PresentedCardRef.Payload
        assertEquals("sysu.card", payload.customType)
        assertEquals("课表", payload.title)
        assertEquals("call-2", (cards.getValue("call-2") as PresentedCardRef.Tool).trace.toolCallId)
    }

    @Test
    fun runningAndBlankCallIdsAreIgnored() {
        val message = assistant(
            tool("running", status = ToolActivityStatus.RUNNING),
            tool(""),
            tool("done"),
        )

        val cards = presentedCardLookup(listOf(message))

        assertEquals(setOf("done"), cards.keys)
        assertNull(cards["running"])
    }

    private fun assistant(vararg steps: AssistantStep): AssistantMessage = AssistantMessage(
        id = 1,
        role = AssistantRole.ASSISTANT,
        text = "",
        steps = steps.toList(),
    )

    private fun tool(
        callId: String,
        status: ToolActivityStatus = ToolActivityStatus.DONE,
        details: kotlinx.serialization.json.JsonObject? = null,
    ): AssistantStep.Tool = AssistantStep.Tool(
        ToolTrace(
            id = 1,
            name = "tool",
            arguments = emptyMap(),
            status = status,
            toolCallId = callId,
            resultDetails = details,
        ),
    )

    private fun cardDetails(customType: String) = buildJsonObject {
        put(
            "deepdeep.card",
            buildJsonObject {
                put("customType", customType)
                put("title", "课表")
                put("payload", buildJsonObject { put("kind", "schedule") })
            },
        )
    }
}
