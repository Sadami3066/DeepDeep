package io.github.whywood.deepdeep.floating

import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.runtime.RunStatus
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import io.github.whywood.deepdeep.service.ActiveRunView
import io.github.whywood.deepdeep.ui.chat.testing.FakeAgentRuntime
import io.github.whywood.deepdeep.ui.chat.testing.HOME_CWD
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FloatingFeedTest {
    private val dispatcher = StandardTestDispatcher()

    @Test
    fun finishedRunEntersCompletedWithReplyText() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime().apply {
            sessions = listOf(session("session-1", "会话"))
        }
        val feed = feed(runtime)
        feed.start()
        runCurrent()

        runtime.emitFor(
            "session-1",
            RuntimeEvent.RunFinished("run-1", "答复内容", RunStatus.DONE, 100L),
        )
        runCurrent()

        val entry = feed.state.value.completed.single()
        assertEquals("session-1", entry.sessionId)
        assertEquals("会话", entry.title)
        assertEquals("答复内容", entry.text)
        assertFalse(entry.hasDelivery)
        feed.stop()
    }

    @Test
    fun failedRunEntersCompletedWithError() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime().apply {
            sessions = listOf(session("session-1", "会话"))
        }
        val feed = feed(runtime)
        feed.start()
        runCurrent()

        runtime.emitFor("session-1", RuntimeEvent.RunFailed("run-1", "网络错误"))
        runCurrent()

        val entry = feed.state.value.completed.single()
        assertEquals(RunStatus.FAILED, entry.status)
        assertEquals("网络错误", entry.error)
        feed.stop()
    }

    @Test
    fun presentBeforeFinishAttachesDelivery() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime().apply {
            sessions = listOf(session("session-1", "会话"))
        }
        val feed = feed(runtime)
        feed.start()
        runCurrent()

        runtime.emitFor(
            "session-1",
            RuntimeEvent.ToolFinished(
                "run-1",
                "call-1",
                "present",
                """{"items":[{"type":"file","path":"/tmp/report.md","name":"report.md","title":"季度报告"}]}""",
                isError = false,
                durationMs = 12L,
            ),
        )
        runCurrent()
        assertTrue(feed.state.value.completed.isEmpty())

        runtime.emitFor("session-1", RuntimeEvent.RunFinished("run-1", "已交付", RunStatus.DONE, 200L))
        runCurrent()

        val entry = feed.state.value.completed.single()
        assertTrue(entry.hasDelivery)
        assertEquals("季度报告", entry.deliveryLabel)
        assertTrue(feed.state.value.stagedDeliveries.isEmpty())
        feed.stop()
    }

    @Test
    fun runningSessionIsListedAndItsCompletionHidden() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime().apply {
            sessions = listOf(session("session-1", "会话"))
        }
        val runs = MutableStateFlow<Map<String, ActiveRunView>>(emptyMap())
        val feed = FloatingFeed(
            runtime = runtime,
            activeRuns = runs,
            pendingQuestions = MutableStateFlow(emptyList()),
            scope = this,
        )
        feed.start()
        runCurrent()

        runtime.emitFor("session-1", RuntimeEvent.RunFinished("run-1", "答复", RunStatus.DONE, 100L))
        runCurrent()
        runs.value = mapOf(
            "session-1" to ActiveRunView("session-1", "会话", null, 300L),
        )
        runCurrent()

        assertEquals(1, feed.state.value.runs.size)
        assertTrue(feed.state.value.completed.isNotEmpty())
        assertTrue(floatingVisibleCompletions(feed.state.value).isEmpty())
        assertEquals(0, floatingPendingCount(feed.state.value))
        feed.stop()
    }

    private fun feed(runtime: FakeAgentRuntime): FloatingFeed = FloatingFeed(
        runtime = runtime,
        activeRuns = MutableStateFlow<Map<String, ActiveRunView>>(emptyMap()),
        pendingQuestions = MutableStateFlow(emptyList()),
        scope = kotlinx.coroutines.CoroutineScope(dispatcher),
    )

    private fun session(id: String, title: String): ConversationSummary = ConversationSummary(
        id = id,
        title = title,
        updatedAt = 1L,
        filePath = "$id.jsonl",
        cwd = HOME_CWD,
    )
}
