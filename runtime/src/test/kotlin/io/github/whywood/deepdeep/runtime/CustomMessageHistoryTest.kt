package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.sdk.ai.PlainTextContent
import io.github.whywood.deepdeep.sdk.ai.AssistantMessage as PiAssistantMessage
import io.github.whywood.deepdeep.sdk.ai.StopReason
import io.github.whywood.deepdeep.sdk.ai.TextContent
import io.github.whywood.deepdeep.sdk.ai.ToolCall
import io.github.whywood.deepdeep.sdk.ai.UserMessage
import io.github.whywood.deepdeep.sdk.ai.Usage
import io.github.whywood.deepdeep.sdk.coding.session.CustomMessageEntry
import io.github.whywood.deepdeep.sdk.coding.session.SessionEntry
import io.github.whywood.deepdeep.sdk.coding.session.SessionMessageEntry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomMessageHistoryTest {
    @Test
    fun mapsVisibleCustomMessageToAssistantCard() {
        val entry = CustomMessageEntry(
            id = "e1",
            parentId = null,
            timestamp = "2026-01-01T00:00:00Z",
            customType = "sample.card",
            content = PlainTextContent("card body"),
            display = true,
            details = buildJsonObject {
                put("title", "Hello")
                put("body", "world")
            },
        )
        val messages = HistoryMapper.mapEntries(listOf(entry))
        assertEquals(1, messages.size)
        val message = messages.single()
        assertEquals(AssistantRole.ASSISTANT, message.role)
        assertEquals("card body", message.text)
        assertEquals("sample.card", message.customType)
        assertEquals("{\"title\":\"Hello\",\"body\":\"world\"}", message.customPayload)
    }

    @Test
    fun hidesNonDisplayCustomMessage() {
        val entry = CustomMessageEntry(
            id = "e2",
            parentId = null,
            timestamp = "2026-01-01T00:00:00Z",
            customType = "sample.hidden",
            content = PlainTextContent("hidden"),
            display = false,
        )
        assertEquals(0, HistoryMapper.mapEntries(listOf(entry)).size)
    }

    @Test
    fun keepsTurnCustomMessageInlineAfterToolStep() {
        val entries = listOf(
            messageEntry(
                PiAssistantMessage(
                    content = mutableListOf(ToolCall("call-1", "mail_search", JsonObject(emptyMap()))),
                    api = "",
                    provider = "",
                    model = "",
                    usage = Usage.empty(),
                    stopReason = StopReason.ToolUse,
                    timestamp = 2L,
                ),
            ),
            CustomMessageEntry(
                id = "e3",
                parentId = "e2",
                timestamp = "2026-01-01T00:00:03Z",
                customType = "mail.search",
                content = PlainTextContent("邮件卡片"),
                display = true,
                details = buildJsonObject { put("count", 2) },
            ),
        )
        val messages = HistoryMapper.mapEntries(entries)
        assertEquals(1, messages.size)
        val turn = messages.single()
        assertEquals(2, turn.steps.size)
        assertTrue(turn.steps[0] is AssistantStep.Tool)
        val card = turn.steps[1] as AssistantStep.CustomCard
        assertEquals("mail.search", card.customType)
        assertEquals("邮件卡片", card.text)
        assertEquals("{\"count\":2}", card.payload)
        assertNull(turn.customType)
    }


    @Test
    fun keepsIdleCustomMessageStandaloneBeforeNextUserTurn() {
        val entries = listOf(
            messageEntry(
                PiAssistantMessage(
                    content = mutableListOf(TextContent("turn answer")),
                    api = "",
                    provider = "",
                    model = "",
                    usage = Usage.empty(),
                    stopReason = StopReason.Stop,
                    timestamp = 2L,
                ),
            ),
            CustomMessageEntry(
                id = "e3",
                parentId = "e2",
                timestamp = "2026-01-01T00:00:03Z",
                customType = "mail.search",
                content = PlainTextContent("idle card"),
                display = true,
            ),
            SessionMessageEntry(
                id = "e4",
                parentId = "e3",
                timestamp = "2026-01-01T00:00:04Z",
                message = UserMessage(PlainTextContent("next question"), 4L),
            ),
        )
        val messages = HistoryMapper.mapEntries(entries)
        assertEquals(3, messages.size)
        assertEquals(AssistantRole.ASSISTANT, messages[0].role)
        assertTrue(messages[0].steps.none { it is AssistantStep.CustomCard })
        assertEquals("mail.search", messages[1].customType)
        assertEquals("idle card", messages[1].text)
        assertEquals(AssistantRole.USER, messages[2].role)
    }

    @Test
    fun keepsCustomMessageInlineWhenTurnContinuesAfterCard() {
        val entries = listOf(
            messageEntry(
                PiAssistantMessage(
                    content = mutableListOf(ToolCall("call-1", "mail_search", JsonObject(emptyMap()))),
                    api = "",
                    provider = "",
                    model = "",
                    usage = Usage.empty(),
                    stopReason = StopReason.ToolUse,
                    timestamp = 2L,
                ),
            ),
            CustomMessageEntry(
                id = "e3",
                parentId = "e2",
                timestamp = "2026-01-01T00:00:03Z",
                customType = "mail.search",
                content = PlainTextContent("邮件卡片"),
                display = true,
            ),
            messageEntry(
                PiAssistantMessage(
                    content = mutableListOf(TextContent("follow-up answer")),
                    api = "",
                    provider = "",
                    model = "",
                    usage = Usage.empty(),
                    stopReason = StopReason.Stop,
                    timestamp = 4L,
                ),
            ),
        )
        val messages = HistoryMapper.mapEntries(entries)
        assertEquals(1, messages.size)
        val turn = messages.single()
        assertEquals(3, turn.steps.size)
        assertTrue(turn.steps[1] is AssistantStep.CustomCard)
        assertTrue(turn.steps[2] is AssistantStep.Text)
        assertEquals("follow-up answer", turn.text)
    }

    private fun messageEntry(message: io.github.whywood.deepdeep.sdk.ai.Message): SessionEntry = SessionMessageEntry(
        id = "e2",
        parentId = "e1",
        timestamp = "2026-01-01T00:00:02Z",
        message = message,
    )
}
