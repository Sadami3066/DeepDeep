package io.github.whywood.deepdeep.ui

import io.github.whywood.deepdeep.runtime.JobSnapshot
import io.github.whywood.deepdeep.runtime.JobStatus
import io.github.whywood.deepdeep.runtime.Timer
import io.github.whywood.deepdeep.runtime.TimerTrigger
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class DrawerSessionMarkersTest {
    @Test
    fun keepsOnlyEnabledTimersWithSession() {
        val timers = listOf(
            timer(id = "a", sessionId = "s1", enabled = true),
            timer(id = "b", sessionId = "s2", enabled = false),
            timer(id = "c", sessionId = "", enabled = true),
        )

        assertEquals(setOf("s1"), sessionIdsWithEnabledTimers(timers))
    }

    @Test
    fun keepsOnlyRunningJobsWithSession() {
        val jobs = listOf(
            job(id = "a", sessionId = "s1", status = JobStatus.RUNNING),
            job(id = "b", sessionId = "s1", status = JobStatus.DONE),
            job(id = "c", sessionId = "s2", status = JobStatus.RUNNING),
            job(id = "d", sessionId = "", status = JobStatus.RUNNING),
        )

        assertEquals(setOf("s1", "s2"), sessionIdsWithRunningJobs(jobs))
    }

    private fun timer(id: String, sessionId: String, enabled: Boolean): Timer = Timer(
        id = id,
        name = id,
        prompt = id,
        trigger = TimerTrigger.Daily(LocalTime.NOON),
        enabled = enabled,
        sessionId = sessionId,
        sessionFile = "$id.jsonl",
        cwd = "/tmp",
    )

    private fun job(id: String, sessionId: String, status: JobStatus): JobSnapshot = JobSnapshot(
        id = id,
        sessionId = sessionId,
        label = id,
        command = id,
        status = status,
        startedAt = 0L,
    )
}
