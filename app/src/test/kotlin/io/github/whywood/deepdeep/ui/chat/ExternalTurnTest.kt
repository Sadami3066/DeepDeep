package io.github.whywood.deepdeep.ui.chat

import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.runtime.RuntimeActivity
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import io.github.whywood.deepdeep.runtime.RunStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalTurnTest {
    private val strings = ChatUiStrings.fallback()

    @Test
    fun externalRunStartedAppendsUserAndStreamingAssistant() {
        val state = ConversationState(
            messages = listOf(
                AssistantMessage(1, AssistantRole.USER, "earlier"),
                AssistantMessage(2, AssistantRole.ASSISTANT, "previous"),
            ),
        )
        val reduced = reduce(
            state,
            RuntimeEvent.RunStarted("run-ext", 1000L, prompt = "from overlay"),
            strings,
        )
        assertEquals(4, reduced.messages.size)
        val user = reduced.messages[2]
        assertEquals(AssistantRole.USER, user.role)
        assertEquals("from overlay", user.text)
        val assistant = reduced.messages[3]
        assertEquals(AssistantRole.ASSISTANT, assistant.role)
        assertTrue(assistant.streaming)
        assertEquals(MessageStatus.RUNNING, assistant.status)
        assertEquals("run-ext", assistant.runId)
        assertEquals(1000L, assistant.startedAt)
        assertTrue(reduced.sending)
        assertEquals(RuntimeActivity.Thinking, reduced.activity)
        assertEquals(1000L, reduced.activityStartedAt)
    }

    @Test
    fun externalRunStartedReusesTrailingUserWithSameText() {
        val state = ConversationState(
            messages = listOf(
                AssistantMessage(1, AssistantRole.ASSISTANT, "previous"),
                AssistantMessage(2, AssistantRole.USER, "from overlay"),
            ),
        )
        val reduced = reduce(
            state,
            RuntimeEvent.RunStarted("run-ext", 1500L, prompt = "from overlay"),
            strings,
        )
        assertEquals(3, reduced.messages.size)
        assertEquals(1, reduced.messages.count { it.role == AssistantRole.USER })
        assertEquals(AssistantRole.ASSISTANT, reduced.messages.last().role)
        assertTrue(reduced.messages.last().streaming)
        assertEquals("run-ext", reduced.messages.last().runId)
    }

    @Test
    fun optimisticSendAttachesRunIdWithoutDuplicatingUser() {
        val state = ConversationState(
            messages = listOf(
                AssistantMessage(1, AssistantRole.USER, "hello"),
                AssistantMessage(2, AssistantRole.ASSISTANT, "", streaming = true, status = MessageStatus.RUNNING),
            ),
            sending = true,
            activity = RuntimeActivity.Thinking,
        )
        val reduced = reduce(state, RuntimeEvent.RunStarted("run-1", 2000L, prompt = "hello"), strings)
        assertEquals(2, reduced.messages.size)
        assertEquals("run-1", reduced.messages.last().runId)
        assertEquals(1, reduced.messages.count { it.role == AssistantRole.USER })
        assertTrue(reduced.sending)
        assertEquals(2000L, reduced.activityStartedAt)
    }

    @Test
    fun retryRunStartedWithoutPromptAttachesStreamingTail() {
        val state = ConversationState(
            messages = listOf(
                AssistantMessage(1, AssistantRole.USER, "hello"),
                AssistantMessage(2, AssistantRole.ASSISTANT, "", streaming = true, status = MessageStatus.RUNNING),
            ),
            sending = true,
        )
        val reduced = reduce(state, RuntimeEvent.RunStarted("run-retry", 2500L), strings)
        assertEquals(2, reduced.messages.size)
        assertEquals("run-retry", reduced.messages.last().runId)
        assertTrue(reduced.messages.last().streaming)
        assertTrue(reduced.sending)
    }

    @Test
    fun duplicateRunStartedIsIdempotent() {
        val state = ConversationState(
            messages = listOf(
                AssistantMessage(1, AssistantRole.USER, "hello"),
                AssistantMessage(
                    id = 2,
                    role = AssistantRole.ASSISTANT,
                    text = "",
                    streaming = true,
                    status = MessageStatus.RUNNING,
                    runId = "run-1",
                ),
            ),
            sending = true,
        )
        val reduced = reduce(state, RuntimeEvent.RunStarted("run-1", 3000L, prompt = "hello"), strings)
        assertEquals(state, reduced)
    }

    @Test
    fun externalRunStreamsDeltasAndFinishes() {
        var state = ConversationState(messages = listOf(AssistantMessage(1, AssistantRole.ASSISTANT, "previous")))
        state = reduce(state, RuntimeEvent.RunStarted("run-ext", 1000L, prompt = "from overlay"), strings)
        state = reduce(state, RuntimeEvent.TextDelta("run-ext", "ans"), strings)
        state = reduce(state, RuntimeEvent.TextDelta("run-ext", "wer"), strings)
        state = reduce(state, RuntimeEvent.RunFinished("run-ext", "answer", RunStatus.DONE, 4000L), strings)

        assertEquals(3, state.messages.size)
        assertEquals("from overlay", state.messages[1].text)
        val answer = state.messages[2]
        assertEquals("answer", answer.text)
        assertEquals(MessageStatus.DONE, answer.status)
        assertFalse(answer.streaming)
        assertTrue(answer.steps.isEmpty())
        assertFalse(state.sending)
    }

    @Test
    fun externalRetryResumesFailedTail() {
        val state = ConversationState(
            messages = listOf(
                AssistantMessage(1, AssistantRole.USER, "hello"),
                AssistantMessage(
                    id = 2,
                    role = AssistantRole.ASSISTANT,
                    text = "",
                    streaming = false,
                    status = MessageStatus.FAILED,
                    error = "boom",
                ),
            ),
        )
        val reduced = reduce(state, RuntimeEvent.RunStarted("run-retry", 5000L), strings)
        assertEquals(2, reduced.messages.size)
        val tail = reduced.messages.last()
        assertEquals(AssistantRole.ASSISTANT, tail.role)
        assertTrue(tail.streaming)
        assertEquals("run-retry", tail.runId)
        assertTrue(reduced.sending)
        assertEquals(RuntimeActivity.Thinking, reduced.activity)
    }

    @Test
    fun externalRetryDoesNotResurrectNonFailedTail() {
        val state = ConversationState(
            messages = listOf(
                AssistantMessage(1, AssistantRole.USER, "hello"),
                AssistantMessage(2, AssistantRole.ASSISTANT, "done", status = MessageStatus.DONE),
            ),
        )
        val reduced = reduce(state, RuntimeEvent.RunStarted("run-orphan", 6000L), strings)
        assertEquals(state, reduced)
    }
}
