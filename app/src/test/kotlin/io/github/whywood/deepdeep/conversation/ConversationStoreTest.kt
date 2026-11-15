package io.github.whywood.deepdeep.conversation

import io.github.whywood.deepdeep.runtime.ActiveRunSnapshot
import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.runtime.RunStatus
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import io.github.whywood.deepdeep.runtime.SessionModelSnapshot
import io.github.whywood.deepdeep.runtime.ThinkingLevel
import io.github.whywood.deepdeep.ui.chat.testing.FakeAgentRuntime
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationStoreTest {
    @Test
    fun openLoadsHistoryModelAndLocalizesError() = runTest {
        val runtime = FakeAgentRuntime()
        runtime.histories = mapOf(
            "s" to listOf(
                AssistantMessage(
                    id = 1,
                    role = AssistantRole.ASSISTANT,
                    text = "hi",
                    error = "401",
                    rawError = "401",
                ),
            ),
        )
        runtime.sessionModels["s"] = SessionModelSnapshot("p", "m", ThinkingLevel.LOW)
        val store = newStore(runtime)

        val state = store.open("s")

        assertTrue(state.loaded)
        assertEquals("hi", state.messages.single().text)
        assertEquals(ConversationStrings.fallback().runError("401"), state.messages.single().error)
        assertEquals("m", state.sessionModel?.modelId)
    }

    @Test
    fun observersShareOneProjection() = runTest {
        val runtime = FakeAgentRuntime()
        val store = newStore(runtime)
        store.open("s")
        val first = store.observe("s")
        val second = store.observe("s")

        runtime.emitFor("s", RuntimeEvent.RunStarted("r1", 1L, "hello"))
        runCurrent()
        advanceUntilIdle()

        assertEquals(first.value, second.value)
        assertTrue(first.value.sending)
        assertTrue(first.value.messages.any { it.text == "hello" })
    }

    @Test
    fun eventsDuringLoadAreReplayed() = runTest {
        val runtime = FakeAgentRuntime()
        runtime.historyDelay = 100
        val store = newStore(runtime)

        val opening = async { store.open("s") }
        runCurrent()
        runtime.emitFor("s", RuntimeEvent.RunStarted("r1", 1L, "hi"))
        runCurrent()
        advanceUntilIdle()
        val state = opening.await()

        assertTrue(state.sending)
        assertTrue(state.messages.any { it.text == "hi" })
    }

    @Test
    fun concurrentOpensLoadOnce() = runTest {
        val runtime = FakeAgentRuntime()
        runtime.historyDelay = 50
        val store = newStore(runtime)

        val first = async { store.open("s") }
        val second = async { store.open("s") }
        advanceUntilIdle()

        assertTrue(first.await().loaded)
        assertTrue(second.await().loaded)
        assertEquals(1, runtime.historyCalls["s"])
    }

    @Test
    fun deltaBatchFlushesBeforeTerminalEvent() = runTest {
        val runtime = FakeAgentRuntime()
        val store = newStore(runtime)
        store.open("s")

        runtime.emitFor("s", RuntimeEvent.RunStarted("r1", 1L, "go"))
        runtime.emitFor("s", RuntimeEvent.TextDelta("r1", "a"))
        runtime.emitFor("s", RuntimeEvent.TextDelta("r1", "b"))
        runtime.emitFor("s", RuntimeEvent.RunFinished("r1", "ab", RunStatus.DONE, 2L))
        runCurrent()
        advanceUntilIdle()

        val state = store.peek("s")
        assertNotNull(state)
        assertEquals("ab", state!!.messages.last().text)
        assertFalse(state.sending)
    }

    @Test
    fun activeRunSeedsAndCompletes() = runTest {
        val runtime = FakeAgentRuntime()
        runtime.sessions = listOf(ConversationSummary("s", "Session", 1L, "/s.jsonl"))
        runtime.activeRuns.value = mapOf("s" to ActiveRunSnapshot("r1", 10L))
        val store = newStore(runtime)

        val completion = async { store.runCompletions.first() }
        advanceUntilIdle()
        assertEquals("Session", store.activeRuns.value.getValue("s").title)

        runtime.emitFor("s", RuntimeEvent.RunFinished("r1", "done", RunStatus.DONE, 20L))
        val result = completion.await()

        assertEquals("done", result.text)
        assertEquals(RunStatus.DONE, result.status)
        assertFalse(store.activeRuns.value.containsKey("s"))
    }

    @Test
    fun sendStartsOptimisticallyAndRunsInStoreScope() = runTest {
        val runtime = FakeAgentRuntime()
        val store = newStore(runtime)
        store.open("s")

        assertTrue(store.send("s", "hello", title = "T"))
        assertTrue(store.peek("s")!!.sending)
        assertEquals("T", store.peek("s")!!.title)
        runCurrent()
        advanceUntilIdle()

        assertEquals(listOf("hello"), runtime.sent)
        assertFalse(store.send("s", "again"))
    }

    @Test
    fun stopReconcilesWhenNoRunIsActive() = runTest {
        val runtime = FakeAgentRuntime()
        val store = newStore(runtime)
        store.open("s")
        store.send("s", "hello")
        advanceUntilIdle()

        store.stop("s")
        advanceTimeBy(600)
        advanceUntilIdle()

        assertEquals(listOf("s"), runtime.stopped)
        assertFalse(store.peek("s")!!.sending)
    }

    @Test
    fun reconcileAdoptsActiveRun() = runTest {
        val runtime = FakeAgentRuntime()
        val store = newStore(runtime)
        store.open("s")
        store.send("s", "hello")
        advanceUntilIdle()
        runtime.activeRuns.value = mapOf("s" to ActiveRunSnapshot("r9", 42L))

        store.reconcile("s")

        assertEquals("r9", store.peek("s")!!.messages.last().runId)
    }

    @Test
    fun steerFailureIsReported() = runTest {
        val runtime = FakeAgentRuntime()
        runtime.steerError = IllegalStateException("boom")
        val store = newStore(runtime)
        store.open("s")
        val failure = async { store.failures.first() }
        runCurrent()

        store.steer("s", "now")
        advanceUntilIdle()

        assertEquals("boom", failure.await().message)
    }

    @Test
    fun lruKeepsPinnedSessions() = runTest {
        val runtime = FakeAgentRuntime()
        val store = newStore(runtime, maxSessions = 2)

        store.open("a")
        store.pin("a")
        store.open("b")
        store.open("c")

        assertNotNull(store.peek("a"))
        assertNotNull(store.peek("c"))
        assertNull(store.peek("b"))
    }

    @Test
    fun removedSessionDropsBufferedState() = runTest {
        val runtime = FakeAgentRuntime()
        val store = newStore(runtime)
        store.open("s")

        runtime.emitFor("s", RuntimeEvent.SessionRemoved())
        runCurrent()
        advanceUntilIdle()

        assertNull(store.peek("s"))
    }

    private fun TestScope.newStore(
        runtime: FakeAgentRuntime,
        maxSessions: Int = 8,
    ): ConversationStore {
        val store = ConversationStore(
            runtime = runtime,
            scope = backgroundScope,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            maxSessions = maxSessions,
        )
        store.start()
        runCurrent()
        return store
    }
}
