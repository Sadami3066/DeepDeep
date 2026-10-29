package io.github.whywood.deepdeep.service

import io.github.whywood.deepdeep.runtime.InterruptedQuestion
import io.github.whywood.deepdeep.runtime.InterruptedRun
import io.github.whywood.deepdeep.runtime.TurnSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationCenterTest {
    @Test
    fun interruptedNotificationCountMergesQuestionsAndRuns() {
        val questions = listOf(interruptedQuestion("session-a", 3L), interruptedQuestion("session-b", 4L))
        val runs = listOf(interruptedRun("session-c", 2L))
        assertEquals(3, interruptedNotificationCount(questions, runs))
        assertEquals(2, interruptedNotificationCount(questions, emptyList()))
        assertEquals(1, interruptedNotificationCount(emptyList(), runs))
        assertEquals(0, interruptedNotificationCount(emptyList(), emptyList()))
    }

    @Test
    fun interruptedNotificationTargetsEarliestItemAcrossQuestionsAndRuns() {
        val questions = listOf(interruptedQuestion("session-b", 3L), interruptedQuestion("session-a", 5L))
        val runs = listOf(interruptedRun("session-c", 2L), interruptedRun("session-d", 4L))
        assertEquals("session-c", interruptedNotificationTarget(questions, runs))
        assertEquals("session-a", interruptedNotificationTarget(listOf(interruptedQuestion("session-a", 5L)), emptyList()))
        assertEquals("session-b", interruptedNotificationTarget(emptyList(), listOf(interruptedRun("session-b", 1L))))
        assertNull(interruptedNotificationTarget(emptyList(), emptyList()))
    }

    private fun interruptedQuestion(sessionId: String, createdAt: Long) = InterruptedQuestion(
        requestId = "req-$sessionId",
        sessionId = sessionId,
        toolCallId = "call-$sessionId",
        questions = emptyList(),
        createdAt = createdAt,
    )

    private fun interruptedRun(sessionId: String, startedAt: Long) = InterruptedRun(
        sessionId = sessionId,
        runId = "run-$sessionId",
        startedAt = startedAt,
        source = TurnSource.USER,
    )
}
