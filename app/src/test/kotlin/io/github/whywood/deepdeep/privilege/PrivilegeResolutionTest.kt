package io.github.whywood.deepdeep.privilege

import io.github.whywood.deepdeep.shell.RootStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivilegeResolutionTest {
    @Test
    fun selectedTierIsKeptWhenGranted() {
        assertEquals(PrivilegeLevel.NORMAL, resolveEffectiveLevel(PrivilegeLevel.NORMAL, PrivilegeLevel.ROOT))
        assertEquals(PrivilegeLevel.SHIZUKU, resolveEffectiveLevel(PrivilegeLevel.SHIZUKU, PrivilegeLevel.ROOT))
        assertEquals(PrivilegeLevel.ROOT, resolveEffectiveLevel(PrivilegeLevel.ROOT, PrivilegeLevel.ROOT))
    }

    @Test
    fun ungrantedSelectionFallsBackToGrantedTier() {
        assertEquals(PrivilegeLevel.NORMAL, resolveEffectiveLevel(PrivilegeLevel.SHIZUKU, PrivilegeLevel.NORMAL))
        assertEquals(PrivilegeLevel.NORMAL, resolveEffectiveLevel(PrivilegeLevel.ROOT, PrivilegeLevel.NORMAL))
        assertEquals(PrivilegeLevel.SHIZUKU, resolveEffectiveLevel(PrivilegeLevel.ROOT, PrivilegeLevel.SHIZUKU))
    }

    @Test
    fun highestGrantedLevelPrefersRootThenShizuku() {
        assertEquals(PrivilegeLevel.ROOT, highestGrantedLevel(shizukuGranted = true, rootGranted = true))
        assertEquals(PrivilegeLevel.SHIZUKU, highestGrantedLevel(shizukuGranted = true, rootGranted = false))
        assertEquals(PrivilegeLevel.ROOT, highestGrantedLevel(shizukuGranted = false, rootGranted = true))
        assertEquals(PrivilegeLevel.NORMAL, highestGrantedLevel(shizukuGranted = false, rootGranted = false))
    }

    @Test
    fun stateResolvesEffectiveFromShizukuGrant() {
        val state = PrivilegeState(
            selected = PrivilegeLevel.SHIZUKU,
            shizuku = ShizukuState(installed = true, running = true, granted = true, version = 13, uid = 2000),
        )

        assertEquals(PrivilegeLevel.SHIZUKU, state.resolveEffective().effective)
    }

    @Test
    fun stateRequiresRunningShizuku() {
        val state = PrivilegeState(
            selected = PrivilegeLevel.SHIZUKU,
            shizuku = ShizukuState(installed = true, running = false, granted = true),
        )

        assertEquals(PrivilegeLevel.NORMAL, state.resolveEffective().effective)
    }

    @Test
    fun stateResolvesRootGrant() {
        val state = PrivilegeState(selected = PrivilegeLevel.ROOT, root = RootStatus.Granted)

        assertEquals(PrivilegeLevel.ROOT, state.resolveEffective().effective)
    }

    @Test
    fun stateFallsBackWhenSelectedRootIsDenied() {
        val state = PrivilegeState(
            selected = PrivilegeLevel.ROOT,
            root = RootStatus.Denied,
            shizuku = ShizukuState(installed = true, running = true, granted = true),
        )

        assertEquals(PrivilegeLevel.SHIZUKU, state.resolveEffective().effective)
    }

    @Test
    fun virtualScreenFollowsEffectiveLevel() {
        assertFalse(PrivilegeState(effective = PrivilegeLevel.NORMAL).virtualScreenAvailable)
        assertTrue(PrivilegeState(effective = PrivilegeLevel.SHIZUKU).virtualScreenAvailable)
        assertTrue(PrivilegeState(effective = PrivilegeLevel.ROOT).virtualScreenAvailable)
    }
}
