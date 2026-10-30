package io.github.whywood.deepdeep.ui.chat.state

import io.github.whywood.deepdeep.runtime.ActiveRunSnapshot
import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import io.github.whywood.deepdeep.runtime.RunStatus
import io.github.whywood.deepdeep.runtime.SessionUsage
import io.github.whywood.deepdeep.ui.chat.ChatUiStrings
import io.github.whywood.deepdeep.ui.chat.ConversationState
import io.github.whywood.deepdeep.ui.chat.testing.FakeAgentRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SessionProjectionTest {
    private val dispatcher = StandardTestDispatcher()
    private val strings = ChatUiStrings.fallback()
    private val usage = SessionUsage(
        contextTokens = 1_000,
        contextWindow = 128_000,
        inputTokens = 10,
        outputTokens = 20,
        cacheReadTokens = 0,
        cacheWriteTokens = 0,
        cost = 0.5,
    )

    private fun projection(
        runtime: FakeAgentRuntime,
        scope: CoroutineScope,
    ) = SessionProjection(runtime, scope, dispatcher) { strings }

    @Test
    fun openSeedsHistoryUsageAndActiveRun() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime().apply {
            histories = mapOf(
                "session-1" to listOf(
                    AssistantMessage(0, AssistantRole.USER, "hi"),
                    AssistantMessage(1, AssistantRole.ASSISTANT, "hello"),
                ),
            )
            usages = mapOf("session-1" to usage)
            activeRuns.value = mapOf("session-1" to ActiveRunSnapshot("run-live", 500L))
        }
        val projection = projection(runtime, backgroundScope)

        projection.open("session-1")

        val state = projection.state.value
        assertTrue(state.loaded)
        assertEquals(usage, state.usage)
        assertEquals(2, state.messages.size)
        assertEquals("hi", state.messages[0].text)
        val tail = state.messages.last()
        assertEquals("run-live", tail.runId)
        assertTrue(tail.streaming)
        assertTrue(state.sending)
        assertEquals(500L, state.activityStartedAt)
    }

    @Test
    fun eventsForOpenedSessionAppendToState() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val projection = projection(runtime, backgroundScope)
        projection.open("session-1")
        runCurrent()

        runtime.emitFor("session-1", RuntimeEvent.RunStarted("run-1", 1000L, prompt = "from overlay"))
        runtime.emitFor("session-1", RuntimeEvent.TextDelta("run-1", "hi"))
        runtime.emitFor("session-1", RuntimeEvent.RunFinished("run-1", "hi", RunStatus.DONE, 2000L))
        runCurrent()

        val messages = projection.state.value.messages
        assertEquals(2, messages.size)
        assertEquals(AssistantRole.USER, messages[0].role)
        assertEquals("from overlay", messages[0].text)
        assertEquals("hi", messages[1].text)
        assertEquals(MessageStatus.DONE, messages[1].status)
        assertFalse(projection.state.value.sending)
    }

    @Test
    fun askAppendsOptimisticTurnAndSendsPrompt() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val projection = projection(runtime, backgroundScope)
        projection.open("session-1")
        runCurrent()

        projection.ask("  hello  ")

        assertEquals(2, projection.state.value.messages.size)
        assertEquals("hello", projection.state.value.messages[0].text)
        assertEquals(AssistantRole.USER, projection.state.value.messages[0].role)
        assertEquals(AssistantRole.ASSISTANT, projection.state.value.messages[1].role)
        assertTrue(projection.state.value.sending)

        runCurrent()
        assertEquals(listOf("hello"), runtime.sent)
    }

    @Test
    fun sendFailureMarksTailFailed() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime().apply {
            onSend = { _, _ -> error("boom") }
        }
        val projection = projection(runtime, backgroundScope)
        projection.open("session-1")
        runCurrent()

        projection.ask("hello")
        runCurrent()

        val tail = projection.state.value.messages.last()
        assertEquals(MessageStatus.FAILED, tail.status)
        assertEquals(strings.runError("boom"), tail.error)
        assertFalse(projection.state.value.sending)
    }

    @Test
    fun retryLastMarksTailRetryingAndRetries() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime().apply {
            histories = mapOf(
                "session-1" to listOf(
                    AssistantMessage(0, AssistantRole.USER, "hi"),
                    AssistantMessage(
                        id = 1,
                        role = AssistantRole.ASSISTANT,
                        text = "partial",
                        status = MessageStatus.FAILED,
                        startedAt = 1L,
                        runId = "run-1",
                    ),
                ),
            )
        }
        val projection = projection(runtime, backgroundScope)
        projection.open("session-1")
        runCurrent()

        projection.retryLast()
        runCurrent()

        assertEquals(1, runtime.retried)
        val tail = projection.state.value.messages.last()
        assertTrue(tail.streaming)
        assertTrue(tail.retrying)
        assertEquals("", tail.runId)
        assertTrue(projection.state.value.sending)
    }

    @Test
    fun stopForwardsToRuntime() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val projection = projection(runtime, backgroundScope)
        projection.open("session-1")

        projection.stop()

        assertEquals(listOf("session-1"), runtime.stopped)
    }

    @Test
    fun closeStopsApplyingEvents() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val projection = projection(runtime, backgroundScope)
        projection.open("session-1")
        runCurrent()

        projection.close()
        runCurrent()
        runtime.emitFor("session-1", RuntimeEvent.RunStarted("run-1", 1000L, prompt = "late"))
        runCurrent()

        assertEquals(ConversationState(), projection.state.value)
    }
}
