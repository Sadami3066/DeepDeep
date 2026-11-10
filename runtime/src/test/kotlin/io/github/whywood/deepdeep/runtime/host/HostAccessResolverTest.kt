package io.github.whywood.deepdeep.runtime.host

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HostAccessResolverTest {
    @Test
    fun localSelectionNeverUpgrades() {
        val state = resolveHostAccess(HostAccess.LOCAL, adbReady = true, rootReady = true)
        assertEquals(HostAccess.LOCAL, state.selected)
        assertEquals(HostAccess.LOCAL, state.effective)
        assertNull(state.fallback)
    }

    @Test
    fun adbSelectionUsesAdbWhenReady() {
        val state = resolveHostAccess(HostAccess.ADB, adbReady = true, rootReady = false)
        assertEquals(HostAccess.ADB, state.effective)
        assertNull(state.fallback)
    }

    @Test
    fun adbSelectionFallsBackToLocal() {
        val state = resolveHostAccess(HostAccess.ADB, adbReady = false, rootReady = true)
        assertEquals(HostAccess.LOCAL, state.effective)
        assertEquals(true, state.rootReady)
        assertEquals(HostAccessFallback.ADB_UNAVAILABLE, state.fallback)
    }

    @Test
    fun rootSelectionUsesRootWhenReady() {
        val state = resolveHostAccess(HostAccess.ROOT, adbReady = false, rootReady = true)
        assertEquals(HostAccess.ROOT, state.effective)
        assertNull(state.fallback)
    }

    @Test
    fun rootSelectionFallsBackToAdb() {
        val state = resolveHostAccess(HostAccess.ROOT, adbReady = true, rootReady = false)
        assertEquals(HostAccess.ADB, state.effective)
        assertEquals(HostAccessFallback.ROOT_UNAVAILABLE, state.fallback)
    }

    @Test
    fun rootSelectionFallsBackToLocalWhenNothingAvailable() {
        val state = resolveHostAccess(HostAccess.ROOT, adbReady = false, rootReady = false)
        assertEquals(HostAccess.LOCAL, state.effective)
        assertEquals(HostAccessFallback.ROOT_UNAVAILABLE, state.fallback)
    }
}
