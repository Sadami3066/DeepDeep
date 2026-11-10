package io.github.whywood.deepdeep.graph

import io.github.whywood.deepdeep.runtime.host.HostAccess
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenOpsActivationTest {
    @Test
    fun inactiveWhenScreenOpsOff() {
        assertFalse(isScreenOpsActive(false, HostAccess.ADB))
        assertFalse(isScreenOpsActive(false, HostAccess.ROOT))
    }

    @Test
    fun inactiveWithoutHostAccess() {
        assertFalse(isScreenOpsActive(true, HostAccess.LOCAL))
    }

    @Test
    fun activeWithAdbOrRoot() {
        assertTrue(isScreenOpsActive(true, HostAccess.ADB))
        assertTrue(isScreenOpsActive(true, HostAccess.ROOT))
    }
}
