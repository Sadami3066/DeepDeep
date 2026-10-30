package io.github.whywood.deepdeep.ui.chat

import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.AssistantStep
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.runtime.RuntimeActivity
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import io.github.whywood.deepdeep.runtime.RunStatus
import io.github.whywood.deepdeep.runtime.ToolActivityStatus
import io.github.whywood.deepdeep.runtime.ToolTrace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationReducerTest {
    private val strings = ChatUiStrings.fallback()

    @Test
    fun runStartedAdoptsStreamingPlaceholder() {
        val state = ConversationState(
            messages = listOf(AssistantMessage(1, AssistantRole.ASSISTANT, "", streaming = true)),
        )
        val reduced = reduce(state, RuntimeEvent.RunStarted("run-1", 1000L), strings, now = 2000L)
        assertEquals("run-1", reduced.messages.single().runId)
        assertTrue(reduced.sending)
        assertEquals(RuntimeActivity.Thinking, reduced.activity)
        assertEquals(1000L, reduced.activityStartedAt)
    }

    @Test
    fun textDeltaStreamsAndRunFinishedConsumesFinalText() {
        var state = ConversationState(
            messages = listOf(AssistantMessage(1, AssistantRole.ASSISTANT, "", streaming = true, runId = "run-1")),
        )
        state = reduce(state, RuntimeEvent.TextDelta("run-1", "hel"), strings)
        state = reduce(state, RuntimeEvent.TextDelta("run-1", "lo"), strings)
        assertEquals("hello", (state.messages.single().steps.single() as AssistantStep.Text).text)

        state = reduce(state, RuntimeEvent.RunFinished("run-1", "hello", RunStatus.DONE, 3000L), strings)
        val message = state.messages.single()
        assertEquals("hello", message.text)
        assertEquals(MessageStatus.DONE, message.status)
        assertFalse(message.streaming)
        assertTrue(message.steps.isEmpty())
        assertFalse(state.sending)
        assertNull(state.activity)
    }

    @Test
    fun runFailedLocalizesErrorAndKeepsPartialText() {
        val state = ConversationState(
            messages = listOf(
                AssistantMessage(
                    id = 1,
                    role = AssistantRole.ASSISTANT,
                    text = "partial",
                    streaming = true,
                    runId = "run-1",
                    steps = listOf(
                        AssistantStep.Tool(
                            ToolTrace(id = 0, name = "bash", arguments = emptyMap()),
                        ),
                    ),
                ),
            ),
            sending = true,
        )
        val reduced = reduce(state, RuntimeEvent.RunFailed("run-1", "402 Insufficient Balance"), strings, now = 4000L)
        val message = reduced.messages.single()
        assertEquals(MessageStatus.FAILED, message.status)
        assertEquals("partial", message.text)
        assertEquals(strings.withCode(strings.insufficientBalance, "402"), message.error)
        assertEquals(4000L, message.finishedAt)
        assertEquals(ToolActivityStatus.FAILED, (message.steps.single() as AssistantStep.Tool).trace.status)
        assertFalse(reduced.sending)
    }

    @Test
    fun activityChangedStoresRuntimeActivity() {
        val state = ConversationState(
            messages = listOf(AssistantMessage(1, AssistantRole.ASSISTANT, "", streaming = true, runId = "run-1")),
        )
        val reduced = reduce(
            state,
            RuntimeEvent.ActivityChanged("run-1", RuntimeActivity.RunningTool("bash")),
            strings,
            now = 9000L,
        )
        assertEquals(RuntimeActivity.RunningTool("bash"), reduced.activity)
        assertEquals(9000L, reduced.activityStartedAt)
    }

    @Test
    fun turnInjectedAppendsUserAndStreamingPlaceholder() {
        val raw = "[定时提醒] 喝水\n这是你设置的定时提醒，现在到时间了。\n\n喝水提醒"
        val reduced = reduce(
            ConversationState(),
            RuntimeEvent.TurnInjected("run-9", raw, "喝水"),
            strings,
            now = 5000L,
        )
        assertEquals(2, reduced.messages.size)
        val user = reduced.messages[0]
        assertEquals(AssistantRole.USER, user.role)
        assertEquals("喝水提醒", user.text)
        assertEquals("喝水", user.reminder)
        val assistant = reduced.messages[1]
        assertEquals(AssistantRole.ASSISTANT, assistant.role)
        assertTrue(assistant.streaming)
        assertEquals("run-9", assistant.runId)
        assertTrue(reduced.sending)
        assertEquals(RuntimeActivity.Thinking, reduced.activity)
        assertEquals(5000L, reduced.activityStartedAt)
    }

    @Test
    fun turnInjectedWhileStreamingOnlyAppendsUserMessage() {
        val state = ConversationState(
            messages = listOf(AssistantMessage(1, AssistantRole.ASSISTANT, "", streaming = true, runId = "run-1")),
            sending = true,
        )
        val reduced = reduce(state, RuntimeEvent.TurnInjected("run-1", "ping", "提醒"), strings)
        assertEquals(2, reduced.messages.size)
        assertEquals(AssistantRole.USER, reduced.messages.last().role)
        assertEquals("提醒", reduced.messages.last().reminder)
    }

    @Test
    fun adoptActiveRunMarksTailStreaming() {
        val state = ConversationState(
            messages = listOf(
                AssistantMessage(1, AssistantRole.USER, "hi"),
                AssistantMessage(2, AssistantRole.ASSISTANT, "answer", status = MessageStatus.DONE),
            ),
        )
        val adopted = state.adoptActiveRun("run-live", RuntimeActivity.Thinking, now = 7000L)
        val tail = adopted.messages.last()
        assertTrue(tail.streaming)
        assertEquals(MessageStatus.RUNNING, tail.status)
        assertEquals("run-live", tail.runId)
        assertTrue(adopted.sending)
        assertEquals(7000L, adopted.activityStartedAt)
    }

    @Test
    fun adoptActiveRunAppendsPlaceholderWhenTailIsUser() {
        val state = ConversationState(
            messages = listOf(
                AssistantMessage(1, AssistantRole.ASSISTANT, "previous", status = MessageStatus.DONE),
                AssistantMessage(2, AssistantRole.USER, "reminder"),
            ),
        )
        val adopted = state.adoptActiveRun("run-live", RuntimeActivity.Thinking, now = 7000L)
        assertEquals(3, adopted.messages.size)
        assertTrue(adopted.messages.last().streaming)
        assertEquals("run-live", adopted.messages.last().runId)
        assertTrue(adopted.sending)
    }

    @Test
    fun ignoreEventsForUnknownRun() {
        val state = ConversationState(
            messages = listOf(AssistantMessage(1, AssistantRole.ASSISTANT, "keep", runId = "run-1")),
        )
        val reduced = reduce(state, RuntimeEvent.TextDelta("run-2", "lost"), strings)
        assertEquals("keep", reduced.messages.single().text)
    }

    @Test
    fun runStartedCreatesPlaceholderWhileSending() {
        val state = ConversationState(
            messages = listOf(AssistantMessage(1, AssistantRole.USER, "hi")),
            sending = true,
            activity = RuntimeActivity.Thinking,
        )
        val reduced = reduce(state, RuntimeEvent.RunStarted("run-9", 4200L), strings)
        assertEquals(2, reduced.messages.size)
        val placeholder = reduced.messages.last()
        assertEquals(AssistantRole.ASSISTANT, placeholder.role)
        assertEquals("run-9", placeholder.runId)
        assertTrue(placeholder.streaming)
        assertEquals(MessageStatus.RUNNING, placeholder.status)
        assertEquals(4200L, reduced.activityStartedAt)
        assertTrue(reduced.sending)
    }

    @Test
    fun terminalWithDifferentRunIdSettlesStreamingAssistant() {
        val state = ConversationState(
            messages = listOf(
                AssistantMessage(1, AssistantRole.USER, "hi"),
                AssistantMessage(
                    id = 2,
                    role = AssistantRole.ASSISTANT,
                    text = "partial",
                    streaming = true,
                    status = MessageStatus.RUNNING,
                    runId = "run-known",
                ),
            ),
            sending = true,
            activity = RuntimeActivity.Thinking,
            activityStartedAt = 1000L,
        )
        val reduced = reduce(
            state,
            RuntimeEvent.RunFinished("run-other", "final text", RunStatus.DONE, 3000L),
            strings,
        )
        val message = reduced.messages.last()
        assertEquals("final text", message.text)
        assertEquals(MessageStatus.DONE, message.status)
        assertFalse(message.streaming)
        assertFalse(reduced.sending)
        assertNull(reduced.activity)
        assertNull(reduced.activityStartedAt)
    }

    @Test
    fun failedTerminalWithDifferentRunIdSettlesStreamingAssistant() {
        val state = ConversationState(
            messages = listOf(
                AssistantMessage(
                    id = 1,
                    role = AssistantRole.ASSISTANT,
                    text = "partial",
                    streaming = true,
                    status = MessageStatus.RUNNING,
                    runId = "run-known",
                ),
            ),
            sending = true,
        )
        val reduced = reduce(state, RuntimeEvent.RunFailed("run-other", "429 too many"), strings, now = 8000L)
        val message = reduced.messages.single()
        assertEquals(MessageStatus.FAILED, message.status)
        assertFalse(message.streaming)
        assertEquals(strings.withCode(strings.rateLimited, "429"), message.error)
        assertFalse(reduced.sending)
    }

    @Test
    fun terminalClearsEveryStreamingMessage() {
        val state = ConversationState(
            messages = listOf(
                AssistantMessage(
                    id = 1,
                    role = AssistantRole.ASSISTANT,
                    text = "stale",
                    streaming = true,
                    status = MessageStatus.RUNNING,
                    runId = "run-a",
                ),
                AssistantMessage(
                    id = 2,
                    role = AssistantRole.ASSISTANT,
                    text = "current",
                    streaming = true,
                    status = MessageStatus.RUNNING,
                    runId = "run-b",
                ),
            ),
            sending = true,
        )
        val reduced = reduce(state, RuntimeEvent.RunFinished("run-b", "done", RunStatus.DONE, 1000L), strings)
        assertTrue(reduced.messages.none { it.streaming })
        assertEquals(MessageStatus.DONE, reduced.messages[1].status)
        assertFalse(reduced.sending)
    }

    @Test
    fun consumeFinalTextDropsTrailingStreamStep() {
        val steps = listOf(AssistantStep.Text(id = 0, text = "hello"))
        assertTrue(consumeFinalText(steps, "hello").isEmpty())
        assertTrue(consumeFinalText(steps, "other").isNotEmpty())
    }
}
