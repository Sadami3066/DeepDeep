package io.github.whywood.deepdeep.conversation

import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.AssistantStep
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.runtime.RuntimeActivity
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import io.github.whywood.deepdeep.runtime.RunStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserInputAdmissionTest {
    private val strings = ConversationStrings.fallback()

    @Test
    fun admissionSettlesStreamingAssistantAndInsertsUserTurn() {
        val state = ConversationState(
            messages = listOf(
                AssistantMessage(1, AssistantRole.ASSISTANT, "", streaming = true, status = MessageStatus.RUNNING, runId = "run-1"),
            ),
            sending = true,
            activity = RuntimeActivity.Thinking,
        )
        val reduced = reduce(
            state,
            RuntimeEvent.UserInputAdmitted("run-1", "change direction"),
            strings,
            now = 5000L,
        )
        assertEquals(3, reduced.messages.size)
        val settled = reduced.messages[0]
        assertFalse(settled.streaming)
        assertEquals(MessageStatus.DONE, settled.status)
        assertEquals(5000L, settled.finishedAt)
        val user = reduced.messages[1]
        assertEquals(AssistantRole.USER, user.role)
        assertEquals("change direction", user.text)
        val next = reduced.messages[2]
        assertEquals(AssistantRole.ASSISTANT, next.role)
        assertTrue(next.streaming)
        assertEquals(MessageStatus.RUNNING, next.status)
        assertEquals("run-1", next.runId)
        assertTrue(reduced.sending)
    }

    @Test
    fun consecutiveAdmissionsInsertEachUserTurn() {
        var state = ConversationState(
            messages = listOf(
                AssistantMessage(1, AssistantRole.ASSISTANT, "", streaming = true, status = MessageStatus.RUNNING, runId = "run-1"),
            ),
            sending = true,
        )
        state = reduce(state, RuntimeEvent.UserInputAdmitted("run-1", "first steer"), strings, now = 1000L)
        state = reduce(state, RuntimeEvent.TextDelta("run-1", "partial"), strings)
        state = reduce(state, RuntimeEvent.UserInputAdmitted("run-1", "second steer"), strings, now = 2000L)
        assertEquals(5, state.messages.size)
        assertEquals(listOf("first steer", "second steer"), state.messages.filter { it.role == AssistantRole.USER }.map { it.text })
        val assistants = state.messages.filter { it.role == AssistantRole.ASSISTANT }
        assertEquals(3, assistants.size)
        assertFalse(assistants[0].streaming)
        assertFalse(assistants[1].streaming)
        assertTrue(assistants[2].streaming)
        assertEquals("partial", (assistants[1].steps.single() as AssistantStep.Text).text)
        assertTrue(state.sending)
    }

    @Test
    fun runFinishedFinalizesLastAssistantAfterAdmissions() {
        var state = ConversationState(
            messages = listOf(
                AssistantMessage(1, AssistantRole.ASSISTANT, "", streaming = true, status = MessageStatus.RUNNING, runId = "run-1"),
            ),
            sending = true,
        )
        state = reduce(state, RuntimeEvent.UserInputAdmitted("run-1", "steer"), strings, now = 1000L)
        state = reduce(state, RuntimeEvent.TextDelta("run-1", "answer"), strings)
        state = reduce(state, RuntimeEvent.RunFinished("run-1", "answer", RunStatus.DONE, 3000L), strings, now = 3000L)
        val assistants = state.messages.filter { it.role == AssistantRole.ASSISTANT }
        assertEquals(2, assistants.size)
        assertEquals(MessageStatus.DONE, assistants[0].status)
        assertEquals(MessageStatus.DONE, assistants[1].status)
        assertFalse(assistants[1].streaming)
        assertEquals("answer", assistants[1].text)
        assertFalse(state.sending)
    }

    @Test
    fun runFailedFinalizesLastAssistantAfterAdmissions() {
        var state = ConversationState(
            messages = listOf(
                AssistantMessage(1, AssistantRole.ASSISTANT, "", streaming = true, status = MessageStatus.RUNNING, runId = "run-1"),
            ),
            sending = true,
        )
        state = reduce(state, RuntimeEvent.UserInputAdmitted("run-1", "steer"), strings, now = 1000L)
        state = reduce(state, RuntimeEvent.RunFailed("run-1", "boom"), strings, now = 2000L)
        val assistants = state.messages.filter { it.role == AssistantRole.ASSISTANT }
        assertEquals(MessageStatus.DONE, assistants[0].status)
        assertEquals(MessageStatus.FAILED, assistants[1].status)
        assertEquals(strings.runError("boom"), assistants[1].error)
        assertFalse(state.sending)
    }
}
