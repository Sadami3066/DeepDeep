package io.github.whywood.deepdeep.ui.schedules

import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.runtime.NewSchedule
import io.github.whywood.deepdeep.runtime.RuntimeWorkspace
import io.github.whywood.deepdeep.runtime.Schedule
import io.github.whywood.deepdeep.runtime.SchedulePatch
import io.github.whywood.deepdeep.runtime.ScheduleTrigger
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
class SchedulesStateHolderTest {
    @Test
    fun loadPublishesItemsAndResolvesConversationTitles() = runTest {
        val source = FakeSchedulesSource()
        source.items.value = listOf(
            schedule("morning", "Morning brief", sessionId = "session-a"),
            schedule("clear", "Clear inbox", sessionId = "session-b"),
        )
        source.titles = mapOf("session-a" to "Weekly planning", "session-b" to null)
        val holder = SchedulesStateHolder(source, backgroundScope)

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
        val source = FakeSchedulesSource()
        source.listFailure = IOException("boom")
        val holder = SchedulesStateHolder(source, backgroundScope)

        holder.load()

        val state = holder.state.value
        assertFalse(state.loading)
        assertEquals(R.string.chat_reload_failed, state.errorRes)
        assertTrue(state.items.isEmpty())
    }

    @Test
    fun saveFailureKeepsEditorOpenAndSetsError() = runTest {
        val source = FakeSchedulesSource()
        source.createFailure = IOException("disk full")
        val holder = SchedulesStateHolder(source, backgroundScope)
        holder.load()
        holder.openNew()

        holder.save(draft(sessionId = "session-a"))

        val state = holder.state.value
        assertTrue(state.editorOpen)
        assertFalse(state.busy)
        assertEquals(R.string.schedule_error_save_failed, state.errorRes)
        assertEquals(1, source.createCalls)
    }

    @Test
    fun saveSuccessClosesEditor() = runTest {
        val source = FakeSchedulesSource()
        val holder = SchedulesStateHolder(source, backgroundScope)
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
    fun setEnabledWritesSchedulePatch() = runTest {
        val source = FakeSchedulesSource()
        source.items.value = listOf(schedule("morning", "Morning brief"))
        val holder = SchedulesStateHolder(source, backgroundScope)
        holder.load()

        holder.setEnabled("morning", false)
        runCurrent()

        assertEquals(listOf("morning" to SchedulePatch(enabled = false)), source.updates)
        assertFalse(holder.state.value.items.single().enabled)
        assertNull(holder.state.value.errorRes)
    }

    @Test
    fun setEnabledFailureSetsError() = runTest {
        val source = FakeSchedulesSource()
        source.items.value = listOf(schedule("morning", "Morning brief"))
        source.updateFailure = IOException("read-only")
        val holder = SchedulesStateHolder(source, backgroundScope)
        holder.load()

        holder.setEnabled("morning", false)

        assertEquals(R.string.schedule_error_save_failed, holder.state.value.errorRes)
    }

    @Test
    fun queryFiltersByNamePromptAndConversationTitle() = runTest {
        val source = FakeSchedulesSource()
        source.items.value = listOf(
            schedule("morning", "Morning brief", prompt = "Summarize today's news", sessionId = "session-a"),
            schedule("clear", "Clear inbox", prompt = "Triage unread mail", sessionId = "session-b"),
        )
        source.titles = mapOf("session-a" to "Weekly planning")
        val holder = SchedulesStateHolder(source, backgroundScope)
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
        val source = FakeSchedulesSource()
        val holder = SchedulesStateHolder(source, backgroundScope)
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

    private fun draft(sessionId: String = "session-a") = ScheduleDraft(
        name = "Morning brief",
        prompt = "Summarize today's news",
        sessionId = sessionId,
        sessionFile = "/work/session.jsonl",
        cwd = "/work",
    )

    private fun schedule(id: String, name: String, prompt: String = "prompt", sessionId: String = "") = Schedule(
        id = id,
        name = name,
        prompt = prompt,
        trigger = ScheduleTrigger.Daily(LocalTime.of(9, 0)),
        sessionId = sessionId,
        sessionFile = "/work/$id.jsonl",
        cwd = "/work",
    )

    private class FakeSchedulesSource : SchedulesDataSource {
        val items = MutableStateFlow<List<Schedule>>(emptyList())
        override val schedules: StateFlow<List<Schedule>> = items
        var listCalls = 0
        var createCalls = 0
        val updates = mutableListOf<Pair<String, SchedulePatch>>()
        var listFailure: Exception? = null
        var createFailure: Exception? = null
        var updateFailure: Exception? = null
        var listGate: CompletableDeferred<Unit>? = null
        var titles: Map<String, String?> = emptyMap()

        override suspend fun list(): List<Schedule> {
            listCalls++
            listGate?.await()
            listFailure?.let { throw it }
            return items.value
        }

        override suspend fun create(newSchedule: NewSchedule): Schedule {
            createCalls++
            createFailure?.let { throw it }
            val created = Schedule(
                id = "created",
                name = newSchedule.name,
                prompt = newSchedule.prompt,
                trigger = newSchedule.trigger,
                enabled = newSchedule.enabled,
                sessionId = newSchedule.sessionId,
                sessionFile = newSchedule.sessionFile,
                cwd = newSchedule.cwd,
            )
            items.value = items.value + created
            return created
        }

        override suspend fun update(id: String, patch: SchedulePatch): Schedule? {
            updates += id to patch
            updateFailure?.let { throw it }
            items.value = items.value.map { schedule ->
                if (schedule.id != id) {
                    schedule
                } else {
                    schedule.copy(
                        name = patch.name ?: schedule.name,
                        prompt = patch.prompt ?: schedule.prompt,
                        enabled = patch.enabled ?: schedule.enabled,
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
