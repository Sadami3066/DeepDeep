package io.github.whywood.deepdeep.ui.mcp

import dev.pi.mcp.config.McpServerConfig
import io.github.whywood.deepdeep.runtime.McpProbeResult
import io.github.whywood.deepdeep.runtime.McpProbeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class McpProbeCacheTest {
    private val config = McpServerConfig.Http(url = "https://mcp.example.com/sse")
    private val result = McpProbeResult(state = McpProbeState.OK, toolCount = 3)

    @Test
    fun lookupMissReturnsNull() {
        val cache = McpProbeCache()
        assertNull(cache.lookup("server", config, now = 1000L))
    }

    @Test
    fun freshWithinTtlAndStaleAfterTtl() {
        val cache = McpProbeCache(ttlMs = 60_000L)
        cache.store("server", config, result, now = 1000L)
        val fresh = cache.lookup("server", config, now = 60_000L)
        assertEquals(result, fresh?.result)
        assertTrue(fresh?.fresh == true)
        val stale = cache.lookup("server", config, now = 61_001L)
        assertEquals(result, stale?.result)
        assertFalse(stale?.fresh == true)
    }

    @Test
    fun configChangeInvalidatesEntry() {
        val cache = McpProbeCache()
        cache.store("server", config, result, now = 1000L)
        val edited = McpServerConfig.Http(url = "https://other.example.com/sse")
        assertNull(cache.lookup("server", edited, now = 2000L))
        assertNull(cache.lookup("server", config, now = 2000L))
    }

    @Test
    fun invalidateRetainAndClear() {
        val cache = McpProbeCache()
        cache.store("a", config, result, now = 1000L)
        cache.store("b", config, result, now = 1000L)
        cache.invalidate("a")
        assertNull(cache.lookup("a", config, now = 2000L))
        assertEquals(result, cache.lookup("b", config, now = 2000L)?.result)
        cache.retain(setOf("c"))
        assertNull(cache.lookup("b", config, now = 2000L))
        cache.store("c", config, result, now = 1000L)
        cache.clear()
        assertNull(cache.lookup("c", config, now = 2000L))
    }
}
