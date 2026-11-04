package io.github.whywood.deepdeep.vscreen

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VirtualScreenIdleTest {
    @Test
    fun defaultIdleTimeoutIsFifteenMinutes() {
        assertEquals(15 * 60_000L, IdlePolicy.IDLE_TIMEOUT_MS)
        val policy = IdlePolicy()
        policy.onConnected(0L)
        assertFalse(policy.isIdle(14 * 60_000L))
        assertTrue(policy.isIdle(15 * 60_000L))
    }

    @Test
    fun onlineTimeIsNotIdleBeforeTheWindowEnds() {
        val policy = IdlePolicy(timeoutMs = 300_000L)
        policy.onConnected(1_000L)
        assertFalse(policy.isIdle(1_000L))
        assertFalse(policy.isIdle(300_999L))
        assertTrue(policy.isIdle(301_000L))
    }

    @Test
    fun activityPushesTheWindow() {
        val policy = IdlePolicy(timeoutMs = 300_000L)
        policy.onConnected(1_000L)
        policy.onActivity(200_000L)
        assertFalse(policy.isIdle(499_999L))
        assertTrue(policy.isIdle(500_000L))
    }

    @Test
    fun disconnectedScreenNeverReportsIdle() {
        val policy = IdlePolicy(timeoutMs = 300_000L)
        policy.onConnected(1_000L)
        policy.reset()
        assertFalse(policy.isIdle(10_000_000L))
    }

    @Test
    fun watchdogStopsOnceAfterTheWindow() = runTest {
        val policy = IdlePolicy(timeoutMs = 3_000L)
        policy.onConnected(0L)
        var stops = 0
        val job = launch {
            watchIdle(
                policy = policy,
                nowMs = { testScheduler.currentTime },
                isOnline = { true },
                onIdle = { stops++ },
                checkIntervalMs = 500L,
            )
        }
        advanceTimeBy(2_500L)
        runCurrent()
        assertEquals(0, stops)
        advanceUntilIdle()
        assertEquals(1, stops)
        assertTrue(job.isCompleted)
    }

    @Test
    fun watchdogLeavesWhenTheScreenGoesOffline() = runTest {
        val policy = IdlePolicy(timeoutMs = 3_000L)
        policy.onConnected(0L)
        var stops = 0
        val job = launch {
            watchIdle(
                policy = policy,
                nowMs = { testScheduler.currentTime },
                isOnline = { false },
                onIdle = { stops++ },
                checkIntervalMs = 500L,
            )
        }
        advanceUntilIdle()
        assertEquals(0, stops)
        assertTrue(job.isCompleted)
    }
}
