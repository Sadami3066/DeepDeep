package io.github.whywood.deepdeep.privilege

import org.junit.Assert.assertEquals
import org.junit.Test

class ShellBackendSelectionTest {
    @Test
    fun normalTierRegistersNoShellBackend() {
        assertEquals(emptyList<PrivilegeLevel>(), shellBackendsFor(PrivilegeLevel.NORMAL))
    }

    @Test
    fun adbTierRegistersOnlyAdbBackend() {
        assertEquals(listOf(PrivilegeLevel.ADB), shellBackendsFor(PrivilegeLevel.ADB))
    }

    @Test
    fun rootTierRegistersOnlyRootBackend() {
        assertEquals(listOf(PrivilegeLevel.ROOT), shellBackendsFor(PrivilegeLevel.ROOT))
    }
}
