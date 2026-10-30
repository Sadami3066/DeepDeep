package io.github.whywood.deepdeep.assistant

import io.github.whywood.deepdeep.runtime.ActiveRunSnapshot
import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.runtime.RunStatus
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import io.github.whywood.deepdeep.ui.chat.testing.FakeAgentRuntime
import io.github.whywood.deepdeep.ui.chat.testing.HOME_CWD
import io.github.whywood.deepdeep.ui.chat.testing.HOME_ID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssistantOverlayControllerTest {
    private fun session(id: String = "session-1") = ConversationSummary(
        id = id,
        title = "Title",
        updatedAt = 10L,
        filePath = "$id.jsonl",
        cwd = HOME_CWD,
    )

    @Test
    fun askStreamsAnswerUntilFinished() = runTest {
        val runtime = FakeAgentRuntime()
        runtime.sessions = listOf(session())
        runtime.onSend = { sessionId, _ ->
            runtime.emitFor(sessionId, RuntimeEvent.RunStarted("run-1", 0L))
            runtime.emitFor(sessionId, RuntimeEvent.TextDelta("run-1", "你好"))
            runtime.emitFor(sessionId, RuntimeEvent.RunFinished("run-1", "你好，世界", RunStatus.DONE, 0L))
        }
        val controller = AssistantOverlayController(runtime, { "session-1" }, HOME_ID, this)
        controller.ask("hi")
        advanceUntilIdle()
        assertEquals(listOf("hi"), runtime.sent)
        assertEquals(AssistantOverlayPhase.Done, controller.state.value.phase)
        assertEquals("你好，世界", controller.state.value.answer)
        controller.reset()
        assertEquals(AssistantOverlayPhase.Idle, controller.state.value.phase)
        assertEquals("", controller.state.value.answer)
    }

    @Test
    fun askAttachesToRunningSessionWithoutSending() = runTest {
        val runtime = FakeAgentRuntime()
        runtime.sessions = listOf(session())
        runtime.activeRuns.value = mapOf("session-1" to ActiveRunSnapshot("run-9", 0L))
        val controller = AssistantOverlayController(runtime, { "session-1" }, HOME_ID, this)
        controller.ask("hi")
        advanceUntilIdle()
        assertTrue(runtime.sent.isEmpty())
        assertEquals(AssistantOverlayPhase.Thinking, controller.state.value.phase)
        runtime.emitFor("session-1", RuntimeEvent.TextDelta("run-9", "进度"))
        runtime.emitFor("session-1", RuntimeEvent.RunFinished("run-9", "完成", RunStatus.DONE, 0L))
        advanceUntilIdle()
        assertEquals(AssistantOverlayPhase.Done, controller.state.value.phase)
        assertEquals("完成", controller.state.value.answer)
        controller.reset()
    }

    @Test
    fun askFallsBackToHistoryWhenEventsAreMissing() = runTest {
        val runtime = FakeAgentRuntime()
        runtime.sessions = listOf(session())
        runtime.histories = mapOf(
            "session-1" to listOf(
                AssistantMessage(
                    id = 1L,
                    role = AssistantRole.ASSISTANT,
                    text = "历史回答",
                    runId = "run-1",
                ),
            ),
        )
        val controller = AssistantOverlayController(runtime, { "session-1" }, HOME_ID, this)
        controller.ask("hi")
        advanceUntilIdle()
        assertEquals(AssistantOverlayPhase.Done, controller.state.value.phase)
        assertEquals("历史回答", controller.state.value.answer)
        controller.reset()
    }

    @Test
    fun askCreatesSessionWhenNoneExists() = runTest {
        val runtime = FakeAgentRuntime()
        runtime.onSend = { sessionId, _ ->
            runtime.emitFor(sessionId, RuntimeEvent.RunFinished("run-1", "新会话回答", RunStatus.DONE, 0L))
        }
        val controller = AssistantOverlayController(runtime, { null }, HOME_ID, this)
        controller.ask("hi")
        advanceUntilIdle()
        assertEquals(1, runtime.sent.size)
        assertEquals("session-1", controller.state.value.sessionId)
        assertEquals(AssistantOverlayPhase.Done, controller.state.value.phase)
        assertEquals("新会话回答", controller.state.value.answer)
        controller.reset()
    }

    @Test
    fun sendFailureSurfacesError() = runTest {
        val runtime = FakeAgentRuntime()
        runtime.sessions = listOf(session())
        runtime.onSend = { _, _ -> error("boom") }
        val controller = AssistantOverlayController(runtime, { "session-1" }, HOME_ID, this)
        controller.ask("hi")
        advanceUntilIdle()
        assertEquals(AssistantOverlayPhase.Error, controller.state.value.phase)
        assertEquals(AssistantOverlayError.SendFailed, controller.state.value.error)
        controller.reset()
    }

    @Test
    fun blankInputIsIgnored() = runTest {
        val runtime = FakeAgentRuntime()
        val controller = AssistantOverlayController(runtime, { "session-1" }, HOME_ID, this)
        controller.ask("   ")
        advanceUntilIdle()
        assertTrue(runtime.sent.isEmpty())
        assertEquals(AssistantOverlayPhase.Idle, controller.state.value.phase)
        controller.reset()
    }

    @Test
    fun stopMarksAnswerDone() = runTest {
        val runtime = FakeAgentRuntime()
        runtime.sessions = listOf(session())
        runtime.activeRuns.value = mapOf("session-1" to ActiveRunSnapshot("run-9", 0L))
        val controller = AssistantOverlayController(runtime, { "session-1" }, HOME_ID, this)
        controller.ask("hi")
        advanceUntilIdle()
        controller.stop()
        assertEquals(listOf("session-1"), runtime.stopped)
        assertEquals(AssistantOverlayPhase.Done, controller.state.value.phase)
        controller.reset()
    }
}
