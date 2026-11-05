package io.github.whywood.deepdeep.ui.timers

import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.runtime.NewTimer
import io.github.whywood.deepdeep.runtime.RuntimeWorkspace
import io.github.whywood.deepdeep.runtime.Timer
import io.github.whywood.deepdeep.runtime.TimerPatch
import io.github.whywood.deepdeep.runtime.TimerTrigger
import java.io.IOException
import java.time.LocalTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimersStateHolderTest {
    @Test
    fun loadPublishesItemsAndResolvesConversationTitles() = runTest {
        val source = FakeTimersSource()
        source.items.value = listOf(
            timer("morning", "Morning brief", sessionId = "session-a"),
            timer("clear", "Clear inbox", sessionId = "session-b"),
        )
        source.titles = mapOf("session-a" to "Weekly planning", "session-b" to null)
        val holder = TimersStateHolder(source, backgroundScope)

        holder.load()
        runCurrent()

        val state = holder.state.value
        assertFalse(state.loading)
        assertNull(state.errorRes)
        assertEquals(listOf("morning", "clear"), state.items.map { it.id })
        assertEquals(mapOf("session-a" to "Weekly planning", "session-b" to null), state.titles)
    }

    @Test
    fun loadFailureSetsErrorAndStopsLoading() = runTest {
        val source = FakeTimersSource()
        source.listFailure = IOException("boom")
        val holder = TimersStateHolder(source, backgroundScope)

        holder.load()

        val state = holder.state.value
        assertFalse(state.loading)
        assertEquals(R.string.chat_reload_failed, state.errorRes)
        assertTrue(state.items.isEmpty())
    }

    @Test
    fun saveFailureKeepsEditorOpenAndSetsError() = runTest {
        val source = FakeTimersSource()
        source.createFailure = IOException("disk full")
        val holder = TimersStateHolder(source, backgroundScope)
        holder.load()
        holder.openNew()

        holder.save(draft(sessionId = "session-a"))

        val state = holder.state.value
        assertTrue(state.editorOpen)
        assertFalse(state.busy)
        assertEquals(R.string.timer_error_save_failed, state.errorRes)
        assertEquals(1, source.createCalls)
    }

    @Test
    fun saveSuccessClosesEditor() = runTest {
        val source = FakeTimersSource()
        val holder = TimersStateHolder(source, backgroundScope)
        holder.load()
        holder.openNew()

        holder.save(draft(sessionId = "session-a"))
        runCurrent()

        val state = holder.state.value
        assertFalse(state.editorOpen)
        assertNull(state.errorRes)
        assertEquals(listOf("created"), state.items.map { it.id })
    }

    @Test
    fun setEnabledWritesTimerPatch() = runTest {
        val source = FakeTimersSource()
        source.items.value = listOf(timer("morning", "Morning brief"))
        val holder = TimersStateHolder(source, backgroundScope)
        holder.load()

        holder.setEnabled("morning", false)
        runCurrent()

        assertEquals(listOf("morning" to TimerPatch(enabled = false)), source.updates)
        assertFalse(holder.state.value.items.single().enabled)
        assertNull(holder.state.value.errorRes)
    }

    @Test
    fun setEnabledFailureSetsError() = runTest {
        val source = FakeTimersSource()
        source.items.value = listOf(timer("morning", "Morning brief"))
        source.updateFailure = IOException("read-only")
        val holder = TimersStateHolder(source, backgroundScope)
        holder.load()

        holder.setEnabled("morning", false)

        assertEquals(R.string.timer_error_save_failed, holder.state.value.errorRes)
    }

    @Test
    fun queryFiltersByNamePromptAndConversationTitle() = runTest {
        val source = FakeTimersSource()
        source.items.value = listOf(
            timer("morning", "Morning brief", prompt = "Summarize today's news", sessionId = "session-a"),
            timer("clear", "Clear inbox", prompt = "Triage unread mail", sessionId = "session-b"),
        )
        source.titles = mapOf("session-a" to "Weekly planning")
        val holder = TimersStateHolder(source, backgroundScope)
        holder.load()

        holder.setQuery("planning")
        assertEquals(listOf("morning"), holder.state.value.filtered.map { it.id })

        holder.setQuery("triage")
        assertEquals(listOf("clear"), holder.state.value.filtered.map { it.id })

        holder.setQuery("  ")
        assertEquals(2, holder.state.value.filtered.size)
    }

    @Test
    fun concurrentRefreshCoalescesIntoOneLoad() = runTest {
        val source = FakeTimersSource()
        val holder = TimersStateHolder(source, backgroundScope)
        holder.load()
        assertEquals(1, source.listCalls)

        val gate = CompletableDeferred<Unit>()
        source.listGate = gate
        launch { holder.refresh() }
        runCurrent()
        launch { holder.refresh() }
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(2, source.listCalls)
        assertNull(holder.state.value.errorRes)
    }

    private fun draft(sessionId: String = "session-a") = TimerDraft(
        name = "Morning brief",
        prompt = "Summarize today's news",
        sessionId = sessionId,
        sessionFile = "/work/session.jsonl",
        cwd = "/work",
    )

    private fun timer(id: String, name: String, prompt: String = "prompt", sessionId: String = "") = Timer(
        id = id,
        name = name,
        prompt = prompt,
        trigger = TimerTrigger.Daily(LocalTime.of(9, 0)),
        sessionId = sessionId,
        sessionFile = "/work/$id.jsonl",
        cwd = "/work",
    )

    private class FakeTimersSource : TimersDataSource {
        val items = MutableStateFlow<List<Timer>>(emptyList())
        override val timers: StateFlow<List<Timer>> = items
        var listCalls = 0
        var createCalls = 0
        val updates = mutableListOf<Pair<String, TimerPatch>>()
        var listFailure: Exception? = null
        var createFailure: Exception? = null
        var updateFailure: Exception? = null
        var listGate: CompletableDeferred<Unit>? = null
        var titles: Map<String, String?> = emptyMap()

        override suspend fun list(): List<Timer> {
            listCalls++
            listGate?.await()
            listFailure?.let { throw it }
            return items.value
        }

        override suspend fun create(newTimer: NewTimer): Timer {
            createCalls++
            createFailure?.let { throw it }
            val created = Timer(
                id = "created",
                name = newTimer.name,
                prompt = newTimer.prompt,
                trigger = newTimer.trigger,
                enabled = newTimer.enabled,
                sessionId = newTimer.sessionId,
                sessionFile = newTimer.sessionFile,
                cwd = newTimer.cwd,
            )
            items.value = items.value + created
            return created
        }

        override suspend fun update(id: String, patch: TimerPatch): Timer? {
            updates += id to patch
            updateFailure?.let { throw it }
            items.value = items.value.map { timer ->
                if (timer.id != id) {
                    timer
                } else {
                    timer.copy(
                        name = patch.name ?: timer.name,
                        prompt = patch.prompt ?: timer.prompt,
                        enabled = patch.enabled ?: timer.enabled,
                    )
                }
            }
            return items.value.firstOrNull { it.id == id }
        }

        override suspend fun delete(id: String): Boolean {
            items.value = items.value.filterNot { it.id == id }
            return true
        }

        override suspend fun runNow(id: String): Result<Unit> = Result.success(Unit)

        override suspend fun sessionTitle(sessionId: String): String? = titles[sessionId]

        override suspend fun conversations(): List<ConversationSummary> = emptyList()

        override suspend fun workspaces(): List<RuntimeWorkspace> = emptyList()
    }
}
