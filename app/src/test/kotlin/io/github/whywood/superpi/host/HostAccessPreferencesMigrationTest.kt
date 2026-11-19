package io.github.whywood.superpi.host

import io.github.whywood.superpi.runtime.host.HostAccess
import org.junit.Assert.assertEquals
import org.junit.Test

class HostAccessPreferencesMigrationTest {
    @Test
    fun adbLevelMapsToAdb() {
        assertEquals(HostAccess.ADB, parseLegacyHostAccess("ADB"))
    }

    @Test
    fun legacyShizukuLevelMapsToAdb() {
        assertEquals(HostAccess.ADB, parseLegacyHostAccess("SHIZUKU"))
    }

    @Test
    fun rootLevelMapsToRoot() {
        assertEquals(HostAccess.ROOT, parseLegacyHostAccess("ROOT"))
    }

    @Test
    fun missingOrUnknownLevelFallsBackToLocal() {
        assertEquals(HostAccess.LOCAL, parseLegacyHostAccess(null))
        assertEquals(HostAccess.LOCAL, parseLegacyHostAccess("NORMAL"))
        assertEquals(HostAccess.LOCAL, parseLegacyHostAccess("garbage"))
    }
}
