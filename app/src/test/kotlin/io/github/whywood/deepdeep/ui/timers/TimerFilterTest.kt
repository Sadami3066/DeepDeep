package io.github.whywood.deepdeep.ui.timers

import io.github.whywood.deepdeep.runtime.Timer
import io.github.whywood.deepdeep.runtime.TimerTrigger
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class TimerFilterTest {
    private fun timer(id: String, name: String, prompt: String, sessionId: String = "") = Timer(
        id = id,
        name = name,
        prompt = prompt,
        trigger = TimerTrigger.Daily(LocalTime.of(9, 0)),
        sessionId = sessionId,
        sessionFile = "/work/$id.jsonl",
        cwd = "/work",
    )

    private val timers = listOf(
        timer("morning", "Morning brief", "Summarize today's news", "session-a"),
        timer("clear", "Clear inbox", "Triage unread mail", "session-b"),
    )

    @Test
    fun filtersByNamePromptAndLoadedSessionTitle() {
        assertEquals(listOf("morning"), filterTimers(timers, emptyMap(), "brief").map { it.id })
        assertEquals(listOf("clear"), filterTimers(timers, emptyMap(), "triage").map { it.id })
        assertEquals(
            listOf("morning"),
            filterTimers(timers, mapOf("session-a" to "Weekly planning"), "planning").map { it.id },
        )
        assertEquals(timers, filterTimers(timers, emptyMap(), ""))
        assertEquals(emptyList<Timer>(), filterTimers(timers, emptyMap(), "missing"))
    }
}
