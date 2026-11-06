package io.github.whywood.deepdeep.conversation

import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class PluginCustomMessageReducerTest {
    private val strings = ConversationStrings.fallback()

    @Test
    fun appendsCustomMessageCardAndKeepsPayload() {
        val reduced = reduce(
            ConversationState(),
            RuntimeEvent.PluginCustomMessage(
                customType = "sample.card",
                payload = "{\"title\":\"Hi\"}",
                text = "card body",
            ),
            strings,
        )
        val message = reduced.messages.single()
        assertEquals(AssistantRole.ASSISTANT, message.role)
        assertEquals("sample.card", message.customType)
        assertEquals("card body", message.text)
        assertEquals("{\"title\":\"Hi\"}", message.customPayload)
    }

    @Test
    fun ignoresNonDisplayCustomMessage() {
        val reduced = reduce(
            ConversationState(),
            RuntimeEvent.PluginCustomMessage(
                customType = "sample.card",
                payload = "{}",
                text = "hidden",
                display = false,
            ),
            strings,
        )
        assertEquals(0, reduced.messages.size)
    }
}
