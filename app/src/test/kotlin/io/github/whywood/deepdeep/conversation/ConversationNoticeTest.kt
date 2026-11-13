package io.github.whywood.deepdeep.conversation

import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantStep
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.BackgroundTurnStarted
import io.github.whywood.deepdeep.runtime.CapabilityDelta
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.runtime.NoticeEvent
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import io.github.whywood.deepdeep.runtime.TurnNotice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationNoticeTest {
    private val strings = ConversationStrings.fallback()

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
    fun jobFinishedDuringStreamingAppendsProcessStep() {
        val history = listOf(
            AssistantMessage(0, AssistantRole.USER, "run"),
            AssistantMessage(
                id = 1,
                role = AssistantRole.ASSISTANT,
                text = "",
                streaming = true,
                status = MessageStatus.RUNNING,
                startedAt = 100L,
                runId = "run-1",
            ),
        )
        val reduced = reduce(
            ConversationState(messages = history),
            NoticeEvent("run-1", TurnNotice.JobFinished("build", failed = false)),
            strings,
            now = 1000L,
        )
        assertEquals(2, reduced.messages.size)
        val step = reduced.messages[1].steps.single()
        assertTrue(step is AssistantStep.Notice)
        assertEquals(TurnNotice.JobFinished("build", false), (step as AssistantStep.Notice).notice)
    }

    @Test
    fun jobFinishedOutsideTurnKeepsStandaloneNotice() {
        val reduced = reduce(
            ConversationState(),
            NoticeEvent("run-1", TurnNotice.JobFinished("build", failed = true)),
            strings,
            now = 1000L,
        )
        val message = reduced.messages.single()
        assertEquals(TurnNotice.JobFinished("build", true), message.notice)
        assertTrue(message.steps.isEmpty())
    }

    @Test
    fun nonJobNoticeDuringStreamingStaysStandalone() {
        val history = listOf(
            AssistantMessage(
                id = 0,
                role = AssistantRole.ASSISTANT,
                text = "",
                streaming = true,
                status = MessageStatus.RUNNING,
                startedAt = 100L,
                runId = "run-1",
            ),
        )
        val reduced = reduce(
            ConversationState(messages = history),
            NoticeEvent("run-1", TurnNotice.Compacted(20000, 4000)),
            strings,
            now = 1000L,
        )
        assertEquals(2, reduced.messages.size)
        assertEquals(TurnNotice.Compacted(20000, 4000), reduced.messages[1].notice)
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

    @Test
    fun capabilitiesChangedAtTurnStartInsertsBeforeLastUser() {
        val history = listOf(
            AssistantMessage(0, AssistantRole.USER, "old question"),
            AssistantMessage(1, AssistantRole.ASSISTANT, "old answer"),
            AssistantMessage(2, AssistantRole.USER, "new question"),
            AssistantMessage(
                id = 3,
                role = AssistantRole.ASSISTANT,
                text = "",
                streaming = true,
                status = MessageStatus.RUNNING,
                startedAt = 400L,
                runId = "run-9",
            ),
        )
        val delta = CapabilityDelta(
            toolsAdded = listOf("web_search"),
            toolsRemoved = listOf("bash"),
            skillsAdded = listOf("mail"),
        )
        val reduced = reduce(
            ConversationState(messages = history),
            RuntimeEvent.CapabilitiesChanged("run-9", delta),
            strings,
            now = 500L,
        )
        assertEquals(5, reduced.messages.size)
        assertEquals(
            2,
            reduced.messages.indexOfFirst { it.notice == TurnNotice.CapabilitiesChanged(delta) },
        )
        assertEquals("new question", reduced.messages[3].text)
        assertEquals("run-9", reduced.messages[4].runId)
        assertTrue(reduced.messages[2].steps.isEmpty())
        assertEquals(reduced.messages.size, reduced.messages.map { it.id }.toSet().size)
    }

    @Test
    fun capabilitiesChangedMidTurnAppendsAfterAssistant() {
        val history = listOf(
            AssistantMessage(0, AssistantRole.USER, "question"),
            AssistantMessage(
                id = 1,
                role = AssistantRole.ASSISTANT,
                text = "",
                streaming = true,
                status = MessageStatus.RUNNING,
                startedAt = 100L,
                runId = "run-9",
                steps = listOf(AssistantStep.Text(0, "partial")),
            ),
        )
        val delta = CapabilityDelta(skillsRemoved = listOf("mail"))
        val reduced = reduce(
            ConversationState(messages = history),
            RuntimeEvent.CapabilitiesChanged("run-9", delta),
            strings,
            now = 500L,
        )
        assertEquals(3, reduced.messages.size)
        assertEquals(TurnNotice.CapabilitiesChanged(delta), reduced.messages[2].notice)
        assertEquals(1L, reduced.messages[1].id)
        assertEquals(reduced.messages.size, reduced.messages.map { it.id }.toSet().size)
    }

    @Test
    fun capabilitiesChangedWithoutTurnPlaceholderAppendsNotice() {
        val history = listOf(
            AssistantMessage(0, AssistantRole.USER, "old question"),
            AssistantMessage(1, AssistantRole.ASSISTANT, "old answer"),
        )
        val delta = CapabilityDelta(toolsAdded = listOf("web_search"))
        val reduced = reduce(
            ConversationState(messages = history),
            RuntimeEvent.CapabilitiesChanged("run-9", delta),
            strings,
            now = 500L,
        )
        assertEquals(3, reduced.messages.size)
        assertEquals(TurnNotice.CapabilitiesChanged(delta), reduced.messages.last().notice)
        assertEquals(MessageStatus.DONE, reduced.messages.last().status)
    }

    @Test
    fun capabilitiesChangedForOtherRunAppendsNotice() {
        val history = listOf(
            AssistantMessage(0, AssistantRole.USER, "question"),
            AssistantMessage(
                id = 1,
                role = AssistantRole.ASSISTANT,
                text = "",
                streaming = true,
                status = MessageStatus.RUNNING,
                startedAt = 100L,
                runId = "run-9",
            ),
        )
        val reduced = reduce(
            ConversationState(messages = history),
            RuntimeEvent.CapabilitiesChanged("run-other", CapabilityDelta(toolsAdded = listOf("web_search"))),
            strings,
            now = 500L,
        )
        assertEquals(3, reduced.messages.size)
        assertNotNull(reduced.messages.last().notice)
        assertEquals("run-9", reduced.messages[1].runId)
    }

    @Test
    fun emptyCapabilityDeltaAddsNoNotice() {
        val state = ConversationState(
            messages = listOf(AssistantMessage(0, AssistantRole.USER, "question")),
        )
        val reduced = reduce(
            state,
            RuntimeEvent.CapabilitiesChanged("run-9", CapabilityDelta()),
            strings,
            now = 500L,
        )
        assertEquals(state, reduced)
    }
}
