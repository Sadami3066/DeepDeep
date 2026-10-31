package io.github.whywood.deepdeep.privilege

import io.github.whywood.deepdeep.adb.AdbBridgeState
import io.github.whywood.deepdeep.shell.RootStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivilegeResolutionTest {
    private val adbRunning = AdbBridgeState(paired = true, daemon = true)

    @Test
    fun availableBackendsFollowActualBackends() {
        assertEquals(setOf(PrivilegeLevel.ADB), resolveAvailableBackends(adbAvailable = true, rootAvailable = false))
        assertEquals(setOf(PrivilegeLevel.ROOT), resolveAvailableBackends(adbAvailable = false, rootAvailable = true))
        assertEquals(
            setOf(PrivilegeLevel.ADB, PrivilegeLevel.ROOT),
            resolveAvailableBackends(adbAvailable = true, rootAvailable = true),
        )
        assertEquals(emptySet<PrivilegeLevel>(), resolveAvailableBackends(adbAvailable = false, rootAvailable = false))
    }

    @Test
    fun selectedBackendWinsWhenAvailable() {
        assertEquals(
            PrivilegeLevel.ADB,
            resolveEffectiveLevel(PrivilegeLevel.ADB, adbAvailable = true, rootAvailable = false),
        )
        assertEquals(
            PrivilegeLevel.ADB,
            resolveEffectiveLevel(PrivilegeLevel.ADB, adbAvailable = true, rootAvailable = true),
        )
        assertEquals(
            PrivilegeLevel.ROOT,
            resolveEffectiveLevel(PrivilegeLevel.ROOT, adbAvailable = false, rootAvailable = true),
        )
        assertEquals(
            PrivilegeLevel.ROOT,
            resolveEffectiveLevel(PrivilegeLevel.ROOT, adbAvailable = true, rootAvailable = true),
        )
    }

    @Test
    fun selectedAdbFallsBackToRoot() {
        assertEquals(
            PrivilegeLevel.ROOT,
            resolveEffectiveLevel(PrivilegeLevel.ADB, adbAvailable = false, rootAvailable = true),
        )
    }

    @Test
    fun selectedRootFallsBackToAdb() {
        assertEquals(
            PrivilegeLevel.ADB,
            resolveEffectiveLevel(PrivilegeLevel.ROOT, adbAvailable = true, rootAvailable = false),
        )
    }

    @Test
    fun unavailableBackendsFallBackToNormal() {
        assertEquals(
            PrivilegeLevel.NORMAL,
            resolveEffectiveLevel(PrivilegeLevel.ADB, adbAvailable = false, rootAvailable = false),
        )
        assertEquals(
            PrivilegeLevel.NORMAL,
            resolveEffectiveLevel(PrivilegeLevel.ROOT, adbAvailable = false, rootAvailable = false),
        )
    }

    @Test
    fun normalStaysNormalEvenWhenBackendsAreAvailable() {
        assertEquals(
            PrivilegeLevel.NORMAL,
            resolveEffectiveLevel(PrivilegeLevel.NORMAL, adbAvailable = true, rootAvailable = true),
        )
    }

    @Test
    fun pairedWithoutRunningDaemonIsNotAvailable() {
        val state = PrivilegeState(
            selected = PrivilegeLevel.ADB,
            adb = AdbBridgeState(paired = true, daemon = false),
        )

        assertFalse(state.availableBackends.contains(PrivilegeLevel.ADB))
        assertEquals(PrivilegeLevel.NORMAL, state.resolveEffective().effective)
    }

    @Test
    fun stateResolvesEffectiveFromAvailableBackends() {
        assertEquals(
            PrivilegeLevel.ADB,
            PrivilegeState(selected = PrivilegeLevel.ADB, adb = adbRunning).resolveEffective().effective,
        )
        assertEquals(
            PrivilegeLevel.ROOT,
            PrivilegeState(selected = PrivilegeLevel.ADB, root = RootStatus.Granted).resolveEffective().effective,
        )
        assertEquals(
            PrivilegeLevel.ADB,
            PrivilegeState(selected = PrivilegeLevel.ROOT, adb = adbRunning).resolveEffective().effective,
        )
        assertEquals(
            PrivilegeLevel.ROOT,
            PrivilegeState(selected = PrivilegeLevel.ROOT, root = RootStatus.Granted).resolveEffective().effective,
        )
    }

    @Test
    fun virtualScreenRequiresSelectedTierAndAnyAvailableBackend() {
        assertFalse(PrivilegeState(selected = PrivilegeLevel.NORMAL, adb = adbRunning).virtualScreenAvailable)
        assertFalse(PrivilegeState(selected = PrivilegeLevel.ADB).virtualScreenAvailable)
        assertFalse(PrivilegeState(selected = PrivilegeLevel.ROOT).virtualScreenAvailable)
        assertTrue(PrivilegeState(selected = PrivilegeLevel.ADB, adb = adbRunning).virtualScreenAvailable)
        assertTrue(
            PrivilegeState(selected = PrivilegeLevel.ROOT, root = RootStatus.Granted).virtualScreenAvailable,
        )
        assertTrue(PrivilegeState(selected = PrivilegeLevel.ADB, root = RootStatus.Granted).virtualScreenAvailable)
        assertTrue(PrivilegeState(selected = PrivilegeLevel.ROOT, adb = adbRunning).virtualScreenAvailable)
    }
}
