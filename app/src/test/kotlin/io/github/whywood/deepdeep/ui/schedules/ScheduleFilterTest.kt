package io.github.whywood.deepdeep.ui.schedules

import io.github.whywood.deepdeep.runtime.Schedule
import io.github.whywood.deepdeep.runtime.ScheduleTrigger
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleFilterTest {
    private fun schedule(id: String, name: String, prompt: String, sessionId: String = "") = Schedule(
        id = id,
        name = name,
        prompt = prompt,
        trigger = ScheduleTrigger.Daily(LocalTime.of(9, 0)),
        sessionId = sessionId,
        sessionFile = "/work/$id.jsonl",
        cwd = "/work",
    )

    private val schedules = listOf(
        schedule("morning", "Morning brief", "Summarize today's news", "session-a"),
        schedule("clear", "Clear inbox", "Triage unread mail", "session-b"),
    )

    @Test
    fun filtersByNamePromptAndLoadedSessionTitle() {
        assertEquals(listOf("morning"), filterSchedules(schedules, emptyMap(), "brief").map { it.id })
        assertEquals(listOf("clear"), filterSchedules(schedules, emptyMap(), "triage").map { it.id })
        assertEquals(
            listOf("morning"),
            filterSchedules(schedules, mapOf("session-a" to "Weekly planning"), "planning").map { it.id },
        )
        assertEquals(schedules, filterSchedules(schedules, emptyMap(), ""))
        assertEquals(emptyList<Schedule>(), filterSchedules(schedules, emptyMap(), "missing"))
    }
}
