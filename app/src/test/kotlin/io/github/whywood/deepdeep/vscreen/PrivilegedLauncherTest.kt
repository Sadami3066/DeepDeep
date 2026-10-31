package io.github.whywood.deepdeep.vscreen

import io.github.whywood.deepdeep.privilege.PrivilegeLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivilegedLauncherTest {
    @Test
    fun selectedAdbTriesAdbThenRoot() {
        assertEquals(
            listOf(PrivilegedLauncher.BACKEND_ADB, PrivilegedLauncher.BACKEND_ROOT),
            orderedBackends(PrivilegedLauncher.BACKEND_ADB, adbAvailable = true, rootAvailable = true),
        )
    }

    @Test
    fun selectedRootTriesRootThenAdb() {
        assertEquals(
            listOf(PrivilegedLauncher.BACKEND_ROOT, PrivilegedLauncher.BACKEND_ADB),
            orderedBackends(PrivilegedLauncher.BACKEND_ROOT, adbAvailable = true, rootAvailable = true),
        )
    }

    @Test
    fun missingPreferredBackendFallsBackToAvailableOne() {
        assertEquals(
            listOf(PrivilegedLauncher.BACKEND_ROOT),
            orderedBackends(PrivilegedLauncher.BACKEND_ADB, adbAvailable = false, rootAvailable = true),
        )
        assertEquals(
            listOf(PrivilegedLauncher.BACKEND_ADB),
            orderedBackends(PrivilegedLauncher.BACKEND_ROOT, adbAvailable = true, rootAvailable = false),
        )
    }

    @Test
    fun noAvailableBackendYieldsEmptyList() {
        assertTrue(orderedBackends(PrivilegedLauncher.BACKEND_ADB, adbAvailable = false, rootAvailable = false).isEmpty())
    }

    @Test
    fun normalSelectionYieldsNoBackends() {
        assertTrue(orderedBackends(preferredBackend = null, adbAvailable = true, rootAvailable = true).isEmpty())
    }

    @Test
    fun privilegeLevelsMapToPreferredBackend() {
        assertNull(preferredBackendOf(PrivilegeLevel.NORMAL))
        assertEquals(PrivilegedLauncher.BACKEND_ROOT, preferredBackendOf(PrivilegeLevel.ROOT))
        val adbLevel = PrivilegeLevel.entries.first {
            it != PrivilegeLevel.NORMAL && it != PrivilegeLevel.ROOT
        }
        assertEquals(PrivilegedLauncher.BACKEND_ADB, preferredBackendOf(adbLevel))
    }
}
