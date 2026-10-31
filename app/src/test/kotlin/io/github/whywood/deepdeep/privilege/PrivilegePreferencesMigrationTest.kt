package io.github.whywood.deepdeep.privilege

import org.junit.Assert.assertEquals
import org.junit.Test

class PrivilegePreferencesMigrationTest {
    @Test
    fun legacyRootEnabledMigratesToRoot() {
        assertEquals(PrivilegeLevel.ROOT, resolveMigratedLevel(stored = null, legacyRootEnabled = true))
    }

    @Test
    fun legacyRootDisabledMigratesToNormal() {
        assertEquals(PrivilegeLevel.NORMAL, resolveMigratedLevel(stored = null, legacyRootEnabled = false))
    }

    @Test
    fun storedLevelWinsOverLegacyFlag() {
        assertEquals(PrivilegeLevel.NORMAL, resolveMigratedLevel(PrivilegeLevel.NORMAL, legacyRootEnabled = true))
        assertEquals(PrivilegeLevel.SHIZUKU, resolveMigratedLevel(PrivilegeLevel.SHIZUKU, legacyRootEnabled = false))
        assertEquals(PrivilegeLevel.ROOT, resolveMigratedLevel(PrivilegeLevel.ROOT, legacyRootEnabled = false))
    }

    @Test
    fun parseKnownLevels() {
        assertEquals(PrivilegeLevel.NORMAL, parsePrivilegeLevel("NORMAL"))
        assertEquals(PrivilegeLevel.SHIZUKU, parsePrivilegeLevel("SHIZUKU"))
        assertEquals(PrivilegeLevel.ROOT, parsePrivilegeLevel("ROOT"))
    }

    @Test
    fun parseUnknownLevelsFallsBackToNormal() {
        assertEquals(PrivilegeLevel.NORMAL, parsePrivilegeLevel("ROOTED"))
        assertEquals(PrivilegeLevel.NORMAL, parsePrivilegeLevel(""))
    }
}
