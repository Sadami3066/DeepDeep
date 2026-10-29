package io.github.whywood.deepdeep.service

import io.github.whywood.deepdeep.runtime.InterruptedQuestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationCenterTest {
    @Test
    fun interruptedNotificationTargetsEarliestQuestion() {
        val questions = listOf(
            interrupted("session-b", createdAt = 3L),
            interrupted("session-a", createdAt = 2L),
            interrupted("session-c", createdAt = 5L),
        )
        assertEquals("session-a", interruptedNotificationSessionId(questions))
        assertNull(interruptedNotificationSessionId(emptyList()))
    }

    private fun interrupted(sessionId: String, createdAt: Long) = InterruptedQuestion(
        requestId = "req-$sessionId",
        sessionId = sessionId,
        toolCallId = "call-$sessionId",
        questions = emptyList(),
        createdAt = createdAt,
    )
}
