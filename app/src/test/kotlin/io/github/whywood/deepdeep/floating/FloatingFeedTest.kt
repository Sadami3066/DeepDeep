package io.github.whywood.deepdeep.floating

import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.runtime.RunStatus
import io.github.whywood.deepdeep.runtime.RuntimeActivity
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import io.github.whywood.deepdeep.service.ActiveRunView
import io.github.whywood.deepdeep.ui.chat.testing.FakeAgentRuntime
import io.github.whywood.deepdeep.ui.chat.testing.HOME_CWD
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun foregroundCompletionKeepsPanelEntryWithoutCard() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime().apply {
            sessions = listOf(session("session-1", "会话"))
        }
        val feed = feed(runtime) { true }
        feed.start()
        runCurrent()

        runtime.emitFor("session-1", RuntimeEvent.RunFinished("run-1", "答复内容", RunStatus.DONE, 100L))
        runCurrent()

        val state = feed.state.value
        assertEquals("答复内容", state.completed.single().text)
        assertNull(floatingEventCard(state))
        assertEquals(1, floatingPendingCount(state))
        feed.stop()
    }

    @Test
    fun autoSubscribesLatestRunningSession() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val runs = MutableStateFlow(
            mapOf(
                "session-1" to ActiveRunView("session-1", "第一个", RuntimeActivity.Thinking, 100L),
                "session-2" to ActiveRunView("session-2", "第二个", RuntimeActivity.Thinking, 200L),
            ),
        )
        val feed = floatingFeed(runtime, runs)
        feed.start()
        runCurrent()

        val subscription = feed.state.value.subscription!!
        assertEquals("session-2", subscription.sessionId)
        assertEquals("第二个", subscription.title)
        assertFalse(feed.state.value.manualSubscription)
        feed.stop()
    }

    @Test
    fun manualSubscriptionIsNotReplacedByNewerRun() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val runs = MutableStateFlow(
            mapOf("session-1" to ActiveRunView("session-1", "第一个", RuntimeActivity.Thinking, 100L)),
        )
        val feed = floatingFeed(runtime, runs)
        feed.start()
        runCurrent()

        feed.subscribe("session-1")
        runCurrent()
        assertNull(feed.state.value.subscription)
        assertTrue(feed.state.value.manualSubscription)

        feed.subscribe("session-1")
        runCurrent()
        assertEquals("session-1", feed.state.value.subscription?.sessionId)
        assertTrue(feed.state.value.manualSubscription)

        runs.value = mapOf(
            "session-1" to ActiveRunView("session-1", "第一个", RuntimeActivity.Thinking, 100L),
            "session-2" to ActiveRunView("session-2", "第二个", RuntimeActivity.Thinking, 200L),
        )
        runCurrent()

        assertEquals("session-1", feed.state.value.subscription?.sessionId)
        assertTrue(feed.state.value.manualSubscription)
        feed.stop()
    }

    @Test
    fun clickingSubscribedSessionStopsTracking() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val runs = MutableStateFlow(
            mapOf("session-1" to ActiveRunView("session-1", "会话", RuntimeActivity.Thinking, 100L)),
        )
        val feed = floatingFeed(runtime, runs)
        feed.start()
        runCurrent()
        assertEquals("session-1", feed.state.value.subscription?.sessionId)

        feed.subscribe("session-1")
        runCurrent()
        assertNull(feed.state.value.subscription)
        assertTrue(feed.state.value.manualSubscription)
        assertNull(floatingEventCard(feed.state.value))

        runs.value = mapOf(
            "session-1" to ActiveRunView("session-1", "会话", RuntimeActivity.RunningTool("bash"), 100L),
        )
        runCurrent()
        assertNull(feed.state.value.subscription)
        feed.stop()
    }

    @Test
    fun trackingTextFollowsSubscribedSession() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val runs = MutableStateFlow(
            mapOf("session-1" to ActiveRunView("session-1", "会话", RuntimeActivity.Thinking, 100L)),
        )
        val feed = floatingFeed(runtime, runs)
        feed.start()
        runCurrent()

        runtime.emitFor("session-2", RuntimeEvent.TextDelta("run-2", "别的会话"))
        runtime.emitFor("session-1", RuntimeEvent.TextDelta("run-1", "你好"))
        runCurrent()
        assertEquals("", feed.state.value.subscription?.text)

        advanceTimeBy(FLOATING_TRACKING_TEXT_THROTTLE_MS + 1)
        runCurrent()
        assertEquals("你好", feed.state.value.subscription?.text)
        feed.stop()
    }

    @Test
    fun trackingTextIsThrottledAndMerged() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val runs = MutableStateFlow(
            mapOf("session-1" to ActiveRunView("session-1", "会话", RuntimeActivity.Thinking, 100L)),
        )
        val feed = floatingFeed(runtime, runs)
        feed.start()
        runCurrent()

        runtime.emitFor("session-1", RuntimeEvent.TextDelta("run-1", "你"))
        runtime.emitFor("session-1", RuntimeEvent.TextDelta("run-1", "好"))
        runCurrent()
        assertEquals("", feed.state.value.subscription?.text)

        advanceTimeBy(FLOATING_TRACKING_TEXT_THROTTLE_MS + 1)
        runCurrent()
        assertEquals("你好", feed.state.value.subscription?.text)

        runtime.emitFor("session-1", RuntimeEvent.TextDelta("run-1", "！"))
        runCurrent()
        assertEquals("你好", feed.state.value.subscription?.text)

        advanceTimeBy(FLOATING_TRACKING_TEXT_THROTTLE_MS + 1)
        runCurrent()
        assertEquals("你好！", feed.state.value.subscription?.text)
        feed.stop()
    }

    @Test
    fun runStartedAndUserInputResetTrackedText() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val runs = MutableStateFlow(
            mapOf("session-1" to ActiveRunView("session-1", "会话", RuntimeActivity.Thinking, 100L)),
        )
        val feed = floatingFeed(runtime, runs)
        feed.start()
        runCurrent()

        runtime.emitFor("session-1", RuntimeEvent.TextDelta("run-1", "你好"))
        advanceTimeBy(FLOATING_TRACKING_TEXT_THROTTLE_MS + 1)
        runCurrent()
        assertEquals("你好", feed.state.value.subscription?.text)

        runtime.emitFor("session-1", RuntimeEvent.RunStarted("run-2", 500L))
        runCurrent()
        assertEquals("", feed.state.value.subscription?.text)

        runtime.emitFor("session-1", RuntimeEvent.TextDelta("run-2", "再来"))
        advanceTimeBy(FLOATING_TRACKING_TEXT_THROTTLE_MS + 1)
        runCurrent()
        assertEquals("再来", feed.state.value.subscription?.text)

        runtime.emitFor("session-1", RuntimeEvent.UserInputAdmitted("run-2", "继续"))
        runCurrent()
        assertEquals("", feed.state.value.subscription?.text)
        feed.stop()
    }

    @Test
    fun trackingActivityFollowsSubscribedSession() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val runs = MutableStateFlow(
            mapOf("session-1" to ActiveRunView("session-1", "会话", RuntimeActivity.Thinking, 100L)),
        )
        val feed = floatingFeed(runtime, runs)
        feed.start()
        runCurrent()

        runtime.emitFor(
            "session-1",
            RuntimeEvent.ToolStarted("run-1", "call-1", "bash", emptyMap(), 10L),
        )
        runCurrent()
        assertEquals(RuntimeActivity.RunningTool("bash"), feed.state.value.subscription?.activity)

        runtime.emitFor("session-1", RuntimeEvent.ActivityChanged("run-1", RuntimeActivity.Compacting))
        runCurrent()
        assertEquals(RuntimeActivity.Compacting, feed.state.value.subscription?.activity)

        runtime.emitFor("session-2", RuntimeEvent.ActivityChanged("run-2", RuntimeActivity.CompactionFailed))
        runCurrent()
        assertEquals(RuntimeActivity.Compacting, feed.state.value.subscription?.activity)
        feed.stop()
    }

    @Test
    fun resubscribingSessionDropsStaleTrackedText() = runTest(dispatcher) {
        val runtime = FakeAgentRuntime()
        val runs = MutableStateFlow(
            mapOf("session-1" to ActiveRunView("session-1", "会话一", RuntimeActivity.Thinking, 100L)),
        )
        val feed = floatingFeed(runtime, runs)
        feed.start()
        runCurrent()

        runtime.emitFor("session-1", RuntimeEvent.TextDelta("run-1", "旧"))
        advanceTimeBy(FLOATING_TRACKING_TEXT_THROTTLE_MS + 1)
        runCurrent()
        assertEquals("旧", feed.state.value.subscription?.text)

        runs.value = mapOf(
            "session-1" to ActiveRunView("session-1", "会话一", RuntimeActivity.Thinking, 100L),
            "session-2" to ActiveRunView("session-2", "会话二", RuntimeActivity.Thinking, 200L),
        )
        runCurrent()
        assertEquals("session-2", feed.state.value.subscription?.sessionId)

        runs.value = mapOf(
            "session-1" to ActiveRunView("session-1", "会话一", RuntimeActivity.Thinking, 100L),
        )
        runCurrent()
        assertEquals("session-1", feed.state.value.subscription?.sessionId)

        runtime.emitFor("session-1", RuntimeEvent.TextDelta("run-1", "新"))
        advanceTimeBy(FLOATING_TRACKING_TEXT_THROTTLE_MS + 1)
        runCurrent()
        assertEquals("新", feed.state.value.subscription?.text)
        feed.stop()
    }

    private fun feed(runtime: FakeAgentRuntime, isForeground: () -> Boolean = { false }): FloatingFeed = FloatingFeed(
        runtime = runtime,
        activeRuns = MutableStateFlow<Map<String, ActiveRunView>>(emptyMap()),
        pendingQuestions = MutableStateFlow(emptyList()),
        scope = kotlinx.coroutines.CoroutineScope(dispatcher),
        isForeground = isForeground,
    )

    private fun floatingFeed(
        runtime: FakeAgentRuntime,
        runs: MutableStateFlow<Map<String, ActiveRunView>>,
    ): FloatingFeed = FloatingFeed(
        runtime = runtime,
        activeRuns = runs,
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
