package io.github.whywood.deepdeep.graph

import io.github.whywood.deepdeep.privilege.PrivilegeLevel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenOpsActivationTest {
    @Test
    fun inactiveWhenScreenOpsOff() {
        assertFalse(isScreenOpsActive(false, PrivilegeLevel.ADB))
        assertFalse(isScreenOpsActive(false, PrivilegeLevel.ROOT))
    }

    @Test
    fun inactiveWithoutPrivilege() {
        assertFalse(isScreenOpsActive(true, PrivilegeLevel.NORMAL))
    }

    @Test
    fun activeWithAdbOrRoot() {
        assertTrue(isScreenOpsActive(true, PrivilegeLevel.ADB))
        assertTrue(isScreenOpsActive(true, PrivilegeLevel.ROOT))
    }
}
