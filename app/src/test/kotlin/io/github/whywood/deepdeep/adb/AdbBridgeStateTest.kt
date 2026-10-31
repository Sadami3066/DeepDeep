package io.github.whywood.deepdeep.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbBridgeStateTest {
    @Test
    fun failureOnFreshStateDoesNotClaimPairing() {
        val state = AdbBridgeState().working().failed(AdbBridgeError.PAIRING_FAILED)
        assertFalse(state.paired)
        assertFalse(state.daemon)
        assertFalse(state.busy)
        assertEquals(AdbBridgeError.PAIRING_FAILED, state.error)
    }

    @Test
    fun pairingSuccessMarksPairedButNotDaemon() {
        val state = AdbBridgeState().working().pairingSucceeded()
        assertTrue(state.paired)
        assertFalse(state.daemon)
        assertTrue(state.busy)
        assertEquals(null, state.error)
    }

    @Test
    fun daemonStartMarksPairedAndRunning() {
        val state = AdbBridgeState().working().pairingSucceeded().daemonStarted()
        assertTrue(state.paired)
        assertTrue(state.daemon)
        assertFalse(state.busy)
        assertEquals(null, state.error)
    }

    @Test
    fun daemonFailureAfterPairingKeepsPaired() {
        val state = AdbBridgeState().working().pairingSucceeded()
            .failed(AdbBridgeError.DAEMON_START_FAILED)
        assertTrue(state.paired)
        assertFalse(state.daemon)
        assertFalse(state.busy)
        assertEquals(AdbBridgeError.DAEMON_START_FAILED, state.error)
    }

    @Test
    fun workingClearsPreviousError() {
        val state = AdbBridgeState().failed(AdbBridgeError.PAIRING_FAILED).working()
        assertEquals(null, state.error)
        assertTrue(state.busy)
    }

    @Test
    fun refreshDetectsRunningDaemonAndImpliesPairing() {
        val state = AdbBridgeState().refreshed(daemonAlive = true)
        assertTrue(state.paired)
        assertTrue(state.daemon)
    }

    @Test
    fun refreshWithoutDaemonKeepsPreviousPairing() {
        val state = AdbBridgeState(paired = true, daemon = true).refreshed(daemonAlive = false)
        assertTrue(state.paired)
        assertFalse(state.daemon)
    }

    @Test
    fun trustRevokedClearsPairingAndDaemon() {
        val state = AdbBridgeState(paired = true, daemon = true).working().trustRevoked()
        assertFalse(state.paired)
        assertFalse(state.daemon)
        assertFalse(state.busy)
        assertEquals(AdbBridgeError.NOT_PAIRED, state.error)
    }
}
