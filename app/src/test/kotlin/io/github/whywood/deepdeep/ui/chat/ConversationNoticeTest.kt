package io.github.whywood.deepdeep.ui.chat

import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.BackgroundTurnStarted
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.runtime.NoticeEvent
import io.github.whywood.deepdeep.runtime.TurnNotice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationNoticeTest {
    private val strings = ChatUiStrings.fallback()

    @Test
    fun noticeEventAppendsNoticeMessage() {
        val reduced = reduce(
            ConversationState(),
            NoticeEvent("run-1", TurnNotice.Compacted(20000, 4000)),
            strings,
            now = 1000L,
        )
        val message = reduced.messages.single()
        assertEquals(AssistantRole.ASSISTANT, message.role)
        assertEquals(TurnNotice.Compacted(20000, 4000), message.notice)
    }

    @Test
    fun backgroundTurnAddsNoticeAndStreamingAssistant() {
        val reduced = reduce(
            ConversationState(),
            BackgroundTurnStarted("run-2", TurnNotice.GoalRound(2, 10, "Ship it")),
            strings,
            now = 500L,
        )
        assertEquals(2, reduced.messages.size)
        assertNotNull(reduced.messages[0].notice)
        assertEquals("run-2", reduced.messages[1].runId)
        assertTrue(reduced.messages[1].streaming)
        assertTrue(reduced.sending)
    }

    @Test
    fun backgroundTurnWithoutNoticeAddsOnlyStreamingAssistant() {
        val reduced = reduce(
            ConversationState(),
            BackgroundTurnStarted("run-3"),
            strings,
            now = 500L,
        )
        assertEquals(1, reduced.messages.size)
        assertNull(reduced.messages[0].notice)
        assertEquals("run-3", reduced.messages[0].runId)
        assertTrue(reduced.messages[0].streaming)
        assertTrue(reduced.sending)
    }

    @Test
    fun noticeIdsDoNotCollideWithHistory() {
        val history = listOf(
            AssistantMessage(0, AssistantRole.USER, "hi"),
            AssistantMessage(1, AssistantRole.ASSISTANT, "hello"),
        )
        val reduced = reduce(
            ConversationState(messages = history),
            NoticeEvent("", TurnNotice.GoalFinished(true)),
            strings,
            now = 1000L,
        )
        assertEquals(3, reduced.messages.size)
        assertEquals(setOf(0L, 1L, 2L), reduced.messages.map { it.id }.toSet())
        assertEquals(MessageStatus.DONE, reduced.messages.last().status)
    }
}
