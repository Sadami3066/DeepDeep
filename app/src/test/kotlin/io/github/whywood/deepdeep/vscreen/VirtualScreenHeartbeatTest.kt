package io.github.whywood.deepdeep.vscreen

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualScreenHeartbeatTest {
    @Test
    fun pingIsDueEveryFiveSeconds() {
        val policy = HeartbeatPolicy()
        policy.onConnected(nowMs = 0L)
        assertFalse(policy.isPingDue(4_999L))
        assertTrue(policy.isPingDue(5_000L))
        policy.onPingSent(nowMs = 5_000L)
        assertFalse(policy.isPingDue(9_999L))
        assertTrue(policy.isPingDue(10_000L))
    }

    @Test
    fun transportIsLostAfterFifteenSecondsWithoutPong() {
        val policy = HeartbeatPolicy()
        policy.onConnected(nowMs = 0L)
        assertFalse(policy.isTransportLost(14_999L))
        assertTrue(policy.isTransportLost(15_000L))
    }

    @Test
    fun transportLossIsPausedWhileCommandInFlight() {
        val policy = HeartbeatPolicy()
        policy.onConnected(nowMs = 0L)
        assertFalse(policy.isTransportLost(30_000L, commandInFlight = true))
        assertTrue(policy.isTransportLost(30_000L, commandInFlight = false))
        assertFalse(policy.isTransportLost(14_999L, commandInFlight = false))
    }

    @Test
    fun pongResetsTransportLossWindow() {
        val policy = HeartbeatPolicy()
        policy.onConnected(nowMs = 0L)
        policy.onPong(nowMs = 10_000L)
        assertFalse(policy.isTransportLost(24_999L))
        assertTrue(policy.isTransportLost(25_000L))
    }

    @Test
    fun connectedStateStartsThePongWindow() {
        val policy = HeartbeatPolicy()
        policy.onConnected(nowMs = 1_000L)
        assertFalse(policy.isTransportLost(15_999L))
        assertTrue(policy.isTransportLost(16_000L))
    }

    @Test
    fun healthPingSucceedsWhenPongArrives() = runTest {
        val gate = PongGate()
        gate.arm()
        var healthy = false
        val job = launch { healthy = gate.await(2_000L) }
        advanceTimeBy(500L)
        assertTrue(gate.onPong())
        job.join()
        assertTrue(healthy)
    }

    @Test
    fun healthPingTimesOutAndRebuilds() = runTest {
        val gate = PongGate()
        gate.arm()
        var healthy = true
        val job = launch { healthy = gate.await(2_000L) }
        advanceTimeBy(2_001L)
        job.join()
        assertFalse(healthy)
    }

    @Test
    fun pongAfterResetIsIgnored() = runTest {
        val gate = PongGate()
        gate.arm()
        gate.reset()
        assertFalse(gate.onPong())
        assertFalse(gate.await(2_000L))
    }
}
