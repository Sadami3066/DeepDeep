package io.github.whywood.deepdeep.conversation

import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.AssistantStep
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import io.github.whywood.deepdeep.runtime.ToolTrace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun appendsCardToRunningTurnAsStep() {
        val state = ConversationState(
            messages = listOf(
                AssistantMessage(
                    id = 4,
                    role = AssistantRole.ASSISTANT,
                    text = "",
                    streaming = true,
                    status = MessageStatus.RUNNING,
                    runId = "run-1",
                    steps = listOf(AssistantStep.Tool(ToolTrace(id = 0, name = "mail_search", arguments = emptyMap()))),
                ),
            ),
            sending = true,
        )
        val reduced = reduce(
            state,
            RuntimeEvent.PluginCustomMessage(
                runId = "run-1",
                customType = "mail.search",
                payload = "{\"count\":2}",
                text = "邮件卡片",
            ),
            strings,
        )
        val message = reduced.messages.single()
        assertEquals(2, message.steps.size)
        val card = message.steps[1] as AssistantStep.CustomCard
        assertEquals("mail.search", card.customType)
        assertEquals("{\"count\":2}", card.payload)
        assertEquals("邮件卡片", card.text)
        assertNull(message.customType)
    }

    @Test
    fun keepsCardStandaloneWhenRunIsNotStreaming() {
        val state = ConversationState(
            messages = listOf(
                AssistantMessage(id = 4, role = AssistantRole.ASSISTANT, text = "done", runId = "run-1"),
            ),
        )
        val reduced = reduce(
            state,
            RuntimeEvent.PluginCustomMessage(
                runId = "run-1",
                customType = "mail.search",
                payload = "{}",
                text = "邮件卡片",
            ),
            strings,
        )
        assertEquals(2, reduced.messages.size)
        assertEquals("mail.search", reduced.messages.last().customType)
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
