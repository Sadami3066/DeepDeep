package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.sdk.ai.PlainTextContent
import io.github.whywood.deepdeep.sdk.coding.session.CustomMessageEntry
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
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
}
