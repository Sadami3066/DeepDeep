package io.github.whywood.deepdeep.assistant

import io.github.whywood.deepdeep.runtime.ActiveRunSnapshot
import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.AssistantStep
import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import io.github.whywood.deepdeep.runtime.RunStatus
import io.github.whywood.deepdeep.runtime.ToolActivityStatus
import io.github.whywood.deepdeep.ui.chat.ChatUiStrings
import io.github.whywood.deepdeep.ui.chat.testing.FakeAgentRuntime
import io.github.whywood.deepdeep.ui.chat.testing.HOME_CWD
import io.github.whywood.deepdeep.ui.chat.testing.HOME_ID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssistantOverlayControllerTest {
    private val dispatcher = StandardTestDispatcher()
    private val strings = ChatUiStrings.fallback()

    private fun session(id: String = "session-1", updatedAt: Long = 10L) = ConversationSummary(
        id = id,
        title = "Title",
        updatedAt = updatedAt,
        filePath = "$id.jsonl",
        cwd = HOME_CWD,
    )

    private fun controller(
        runtime: FakeAgentRuntime,
        active: String?,
        scope: CoroutineScope,
        onSelected: (String) -> Unit = {},
    ) = AssistantOverlayController(
        runtime = runtime,
        activeSessionId = { active },
        homeWorkspaceId = HOME_ID,
        scope = scope,
        onSessionSelected = onSelected,
        ioDispatcher = dispatcher,
        strings = { strings },
    )

    @Test
    fun startOpensActiveSessionAndSeedsHistory() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime().apply {
            histories = mapOf(
                "session-1" to listOf(
                    AssistantMessage(0, AssistantRole.USER, "hi"),
                    AssistantMessage(1, AssistantRole.ASSISTANT, "hello"),
                ),
            )
        }
        val controller = controller(runtime, "session-1", this)

        controller.start()
        runCurrent()

        assertEquals("session-1", controller.state.value.sessionId)
        assertTrue(controller.state.value.conversation.loaded)
        assertEquals(2, controller.state.value.conversation.messages.size)
        assertEquals("hello", controller.state.value.conversation.messages[1].text)
        assertTrue(runtime.openCalls.isEmpty())
        controller.reset()
    }

    @Test
    fun startPrefersSessionWithActiveRun() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime().apply {
            sessions = listOf(session("session-1", updatedAt = 10L), session("session-2", updatedAt = 20L))
            histories = mapOf(
                "session-2" to listOf(
                    AssistantMessage(0, AssistantRole.USER, "running"),
                    AssistantMessage(1, AssistantRole.ASSISTANT, ""),
                ),
            )
            activeRuns.value = mapOf(
                "session-2" to ActiveRunSnapshot("run-9", 5L),
                "session-1" to ActiveRunSnapshot("run-8", 1L),
            )
        }
        val selected = mutableListOf<String>()
        val controller = controller(runtime, "session-1", this) { selected += it }

        controller.start()
        runCurrent()

        assertEquals("session-2", controller.state.value.sessionId)
        assertEquals(listOf("session-2"), selected)
        assertTrue(controller.state.value.conversation.sending)
        controller.reset()
    }

    @Test
    fun startAttachesLatestSessionOnColdStart() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime().apply {
            sessions = listOf(session("session-1", updatedAt = 10L), session("session-2", updatedAt = 20L))
            histories = mapOf(
                "session-2" to listOf(
                    AssistantMessage(0, AssistantRole.USER, "old"),
                    AssistantMessage(1, AssistantRole.ASSISTANT, "answer"),
                ),
            )
        }
        val controller = controller(runtime, null, this)

        controller.start()
        runCurrent()

        assertEquals("session-2", controller.state.value.sessionId)
        assertEquals(listOf("session-2.jsonl"), runtime.openCalls)
        assertEquals(listOf("session-2"), runtime.historyCalls.keys.toList())
        assertEquals(2, controller.state.value.conversation.messages.size)
        controller.reset()
    }

    @Test
    fun startCreatesSessionWhenNoneExists() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val controller = controller(runtime, null, this)

        controller.start()
        runCurrent()

        assertEquals("session-1", controller.state.value.sessionId)
        assertTrue(controller.state.value.conversation.loaded)
        assertTrue(runtime.openCalls.isEmpty())
        controller.reset()
    }

    @Test
    fun askBeginsTurnThenSendsOptimistically() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime().apply { onSend = { _, _ -> } }
        val controller = controller(runtime, "session-1", this)
        controller.start()
        runCurrent()

        assertTrue(controller.ask("  hello  "))
        runCurrent()

        assertEquals(listOf("hello"), runtime.sent)
        val messages = controller.state.value.conversation.messages
        assertEquals(2, messages.size)
        assertEquals(AssistantRole.USER, messages[0].role)
        assertEquals("hello", messages[0].text)
        assertTrue(messages[1].streaming)
        assertTrue(controller.state.value.conversation.sending)
        controller.reset()
    }

    @Test
    fun askStreamsToDone() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime().apply {
            onSend = { sessionId, _ ->
                emitFor(sessionId, RuntimeEvent.RunStarted("run-1", 100L, prompt = "hello"))
                emitFor(sessionId, RuntimeEvent.ReasoningDelta("run-1", "think"))
                emitFor(sessionId, RuntimeEvent.TextDelta("run-1", "world"))
                emitFor(sessionId, RuntimeEvent.RunFinished("run-1", "world", RunStatus.DONE, 200L))
            }
        }
        val controller = controller(runtime, "session-1", this)
        controller.start()
        runCurrent()

        assertTrue(controller.ask("hello"))
        runCurrent()

        assertEquals(listOf("hello"), runtime.sent)
        val tail = controller.state.value.conversation.messages.last()
        assertEquals("world", tail.text)
        assertEquals(MessageStatus.DONE, tail.status)
        assertFalse(controller.state.value.conversation.sending)
        controller.reset()
    }

    @Test
    fun askIgnoredWhileSendingAndBusyClearsWhenRunFinishes() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime().apply {
            onSend = { sessionId, _ -> emitFor(sessionId, RuntimeEvent.RunStarted("run-1", 100L, prompt = "one")) }
        }
        val controller = controller(runtime, "session-1", this)
        controller.start()
        runCurrent()

        assertTrue(controller.ask("one"))
        runCurrent()
        assertFalse(controller.ask("two"))
        assertEquals(listOf("one"), runtime.sent)
        assertEquals(AssistantOverlayError.Busy, controller.state.value.error)

        runtime.emitFor("session-1", RuntimeEvent.RunFinished("run-1", "done", RunStatus.DONE, 300L))
        runCurrent()

        assertNull(controller.state.value.error)
        controller.reset()
    }

    @Test
    fun askIgnoredWhenRuntimeAlreadyRunsSession() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val controller = controller(runtime, "session-1", this)
        controller.start()
        runCurrent()

        runtime.activeRuns.value = mapOf("session-1" to ActiveRunSnapshot("run-9", 0L))

        assertFalse(controller.ask("hi"))
        assertTrue(runtime.sent.isEmpty())
        assertEquals(AssistantOverlayError.Busy, controller.state.value.error)
        controller.reset()
    }

    @Test
    fun externalTurnEventsAppearInConversation() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val controller = controller(runtime, "session-1", this)
        controller.start()
        runCurrent()

        runtime.emitFor("session-1", RuntimeEvent.RunStarted("run-7", 1_000L, prompt = "from main"))
        runCurrent()

        val seeded = controller.state.value.conversation.messages
        assertEquals(2, seeded.size)
        assertEquals(AssistantRole.USER, seeded[0].role)
        assertEquals("from main", seeded[0].text)
        assertTrue(seeded[1].streaming)

        runtime.emitFor("session-1", RuntimeEvent.ReasoningDelta("run-7", "plan"))
        runtime.emitFor(
            "session-1",
            RuntimeEvent.ToolStarted("run-7", "call-1", "bash", mapOf("command" to "date"), 1_100L),
        )
        runtime.emitFor(
            "session-1",
            RuntimeEvent.ToolFinished("run-7", "call-1", "bash", "output", isError = false, durationMs = 20L),
        )
        runtime.emitFor("session-1", RuntimeEvent.TextDelta("run-7", "working"))
        runtime.emitFor("session-1", RuntimeEvent.RunFinished("run-7", "done", RunStatus.DONE, 2_000L))
        runCurrent()

        val messages = controller.state.value.conversation.messages
        val tail = messages.last()
        assertEquals("done", tail.text)
        assertEquals(MessageStatus.DONE, tail.status)
        val reasoning = tail.steps.filterIsInstance<AssistantStep.Reasoning>().first()
        assertEquals("plan", reasoning.text)
        val tool = tail.steps.filterIsInstance<AssistantStep.Tool>().first()
        assertEquals("bash", tool.trace.name)
        assertEquals(ToolActivityStatus.DONE, tool.trace.status)
        controller.reset()
    }

    @Test
    fun stopForwardsToRuntime() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val controller = controller(runtime, "session-1", this)
        controller.start()
        runCurrent()

        controller.stop()

        assertEquals(listOf("session-1"), runtime.stopped)
        controller.reset()
    }

    @Test
    fun retryOnlyAcceptsFailedTail() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime().apply {
            histories = mapOf(
                "session-1" to listOf(
                    AssistantMessage(0, AssistantRole.USER, "hi"),
                    AssistantMessage(
                        id = 1,
                        role = AssistantRole.ASSISTANT,
                        text = "partial",
                        status = MessageStatus.FAILED,
                        error = "failed",
                        startedAt = 1L,
                        finishedAt = 2L,
                    ),
                ),
            )
        }
        val controller = controller(runtime, "session-1", this)
        controller.start()
        runCurrent()

        controller.retry(0L)
        controller.retry(99L)
        assertEquals(0, runtime.retried)

        controller.retry(1L)
        runCurrent()

        assertEquals(1, runtime.retried)
        assertTrue(controller.state.value.conversation.messages.last().retrying)
        controller.reset()
    }

    @Test
    fun retryIgnoresCompletedTail() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime().apply {
            histories = mapOf(
                "session-1" to listOf(
                    AssistantMessage(0, AssistantRole.USER, "hi"),
                    AssistantMessage(id = 1, role = AssistantRole.ASSISTANT, text = "ok"),
                ),
            )
        }
        val controller = controller(runtime, "session-1", this)
        controller.start()
        runCurrent()

        controller.retry(1L)
        runCurrent()

        assertEquals(0, runtime.retried)
        controller.reset()
    }

    @Test
    fun sendFailureMarksTailFailed() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime().apply {
            onSend = { _, _ -> error("boom") }
        }
        val controller = controller(runtime, "session-1", this)
        controller.start()
        runCurrent()

        assertTrue(controller.ask("hello"))
        runCurrent()

        val tail = controller.state.value.conversation.messages.last()
        assertEquals(MessageStatus.FAILED, tail.status)
        assertEquals(strings.runError("boom"), tail.error)
        assertFalse(controller.state.value.conversation.sending)
        controller.reset()
    }

    @Test
    fun toggleStepExpandsReasoning() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime().apply {
            histories = mapOf(
                "session-1" to listOf(
                    AssistantMessage(
                        id = 1,
                        role = AssistantRole.ASSISTANT,
                        text = "answer",
                        steps = listOf(AssistantStep.Reasoning(0L, "reasoning")),
                    ),
                ),
            )
        }
        val controller = controller(runtime, "session-1", this)
        controller.start()
        runCurrent()

        controller.toggleStep(1L, 0L)
        runCurrent()

        val step = controller.state.value.conversation.messages.first()
            .steps.filterIsInstance<AssistantStep.Reasoning>().first()
        assertTrue(step.expanded)
        controller.reset()
    }

    @Test
    fun askBeforeStartIsIgnored() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val controller = controller(runtime, null, this)

        assertFalse(controller.ask("hi"))
        assertTrue(runtime.sent.isEmpty())
        controller.reset()
    }

    @Test
    fun resetClearsState() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val controller = controller(runtime, "session-1", this)
        controller.start()
        runCurrent()
        controller.ask("hi")
        runCurrent()

        controller.reset()

        assertEquals(AssistantOverlayState(), controller.state.value)
    }
}
