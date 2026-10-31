package io.github.whywood.deepdeep.vscreen

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualScreenRecoveryTest {
    @Test
    fun backoffSequenceIsOneThreeAndTenSeconds() = runTest {
        val attemptTimes = mutableListOf<Long>()
        val scheduler = ReconnectScheduler()
        val recovered = scheduler.run { attempt ->
            assertEquals(attemptTimes.size + 1, attempt)
            attemptTimes += currentTime
            RecoveryOutcome.RETRY
        }
        assertFalse(recovered)
        assertEquals(listOf(1_000L, 4_000L, 14_000L), attemptTimes)
        assertEquals(3, scheduler.maxAttempts)
    }

    @Test
    fun recoveredAttemptStopsFurtherBackoff() = runTest {
        val attempts = mutableListOf<Int>()
        val scheduler = ReconnectScheduler()
        val recovered = scheduler.run { attempt ->
            attempts += attempt
            if (attempt == 2) RecoveryOutcome.RECOVERED else RecoveryOutcome.RETRY
        }
        assertTrue(recovered)
        assertEquals(listOf(1, 2), attempts)
    }

    @Test
    fun backendUnavailableAbortsWithoutRetrying() = runTest {
        val attempts = mutableListOf<Int>()
        val scheduler = ReconnectScheduler()
        val recovered = scheduler.run { attempt ->
            attempts += attempt
            RecoveryOutcome.ABORT
        }
        assertFalse(recovered)
        assertEquals(listOf(1), attempts)
    }

    @Test
    fun cancellingRecoveryStopsRetries() = runTest {
        val attempts = mutableListOf<Int>()
        val scheduler = ReconnectScheduler()
        val job = launch {
            scheduler.run { attempt ->
                attempts += attempt
                RecoveryOutcome.RETRY
            }
        }
        advanceTimeBy(1_001L)
        assertEquals(listOf(1), attempts)
        job.cancelAndJoin()
        advanceTimeBy(20_000L)
        assertEquals(listOf(1), attempts)
    }

    @Test
    fun stopClearsDesiredOnlineSoNoRecoveryStarts() {
        val lifecycle = SessionLifecycle()
        lifecycle.onStarted()
        assertTrue(lifecycle.shouldRecover())
        lifecycle.onStopped()
        assertFalse(lifecycle.shouldRecover())
    }

    @Test
    fun healthyOnlineScreenIsReused() {
        assertEquals(StartPlan.REUSE, planStart(online = true, starting = false, healthOk = true))
    }

    @Test
    fun unhealthyOnlineScreenIsRebuilt() {
        assertEquals(StartPlan.REBUILD, planStart(online = true, starting = false, healthOk = false))
    }

    @Test
    fun startWhileStartingIsRejectedAsBusy() {
        assertEquals(StartPlan.BUSY, planStart(online = false, starting = true, healthOk = null))
    }

    @Test
    fun offlineScreenStartsFresh() {
        assertEquals(StartPlan.START, planStart(online = false, starting = false, healthOk = null))
    }

    @Test
    fun failureCategoriesSeparateTransportAndBackend() {
        val transport = VirtualScreenFailureInfo(
            category = VirtualScreenFailureCategory.TRANSPORT,
            message = "连接断开",
            recoverable = true,
            reason = TransportFailureKind.HEARTBEAT.code,
        )
        val backend = VirtualScreenFailureInfo(
            category = VirtualScreenFailureCategory.BACKEND,
            message = "需要无线调试（ADB）或 Root 权限",
            recoverable = false,
        )
        assertTrue(transport.recoverable)
        assertFalse(backend.recoverable)
        assertEquals("heartbeat", transport.reason)
    }
}
