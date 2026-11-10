package io.github.whywood.deepdeep.graph

import io.github.whywood.deepdeep.runtime.host.HostCapabilities
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenOpsActivationTest {
    @Test
    fun inactiveWhenScreenOpsOff() {
        assertFalse(isScreenOpsActive(false, HostCapabilities(hasScreenControl = true)))
        assertFalse(isScreenOpsActive(false, HostCapabilities()))
    }

    @Test
    fun inactiveWithoutScreenControl() {
        assertFalse(isScreenOpsActive(true, HostCapabilities(bashAvailable = true)))
    }

    @Test
    fun activeWithScreenControl() {
        assertTrue(isScreenOpsActive(true, HostCapabilities(hasSystemShell = true, hasScreenControl = true)))
        assertTrue(isScreenOpsActive(true, HostCapabilities(isRoot = true, hasScreenControl = true)))
    }
}
