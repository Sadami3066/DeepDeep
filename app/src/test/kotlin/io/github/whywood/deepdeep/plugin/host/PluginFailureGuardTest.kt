package io.github.whywood.deepdeep.plugin.host

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PluginFailureGuardTest {
    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("plugin-failure-guard").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun markAndDetectRoundTrip() {
        val guard = PluginFailureGuard(dir)
        guard.mark("plugin.detail.section:stats", "render crashed", 1234L)

        val marker = guard.detect("plugin.detail.section:stats", now = 1234L + 1000L)
        assertEquals("plugin.detail.section:stats", marker?.key)
        assertEquals("render crashed", marker?.detail)
        assertEquals(1234L, marker?.markedAt)
        assertTrue(dir.listFiles()!!.single().name.startsWith("guard_"))
    }

    @Test
    fun detectWithoutMarkReturnsNull() {
        assertNull(PluginFailureGuard(dir).detect("chat.message:custom"))
    }

    @Test
    fun clearRemovesMarker() {
        val guard = PluginFailureGuard(dir)
        guard.mark("setup.content")
        assertTrue(guard.clear("setup.content"))
        assertNull(guard.detect("setup.content"))
        assertFalse(guard.clear("setup.content"))
    }

    @Test
    fun detectAllAndClearAllHandleEveryMarker() {
        val guard = PluginFailureGuard(dir)
        guard.mark("plugin.detail.section:one", "first", 1L)
        guard.mark("tool.card.content:two", "second", 2L)

        val markers = guard.detectAll(now = 3L)
        assertEquals(listOf("plugin.detail.section:one", "tool.card.content:two"), markers.map { it.key })
        assertEquals(2, guard.clearAll())
        assertTrue(guard.detectAll().isEmpty())
    }

    @Test
    fun detectForListsOnlyPluginMarkers() {
        val guard = PluginFailureGuard(dir)
        guard.mark("alpha@1.0.0:setup.content", "crash", 1L)
        guard.mark("alpha@1.0.0:tool.card.content:card", "crash", 2L)
        guard.mark("beta@1.0.0:setup.content", "crash", 3L)

        assertEquals(
            listOf("alpha@1.0.0:setup.content", "alpha@1.0.0:tool.card.content:card"),
            guard.detectFor("alpha", now = 4L).map { it.key },
        )
        assertTrue(guard.detectFor("missing", now = 4L).isEmpty())
    }

    @Test
    fun clearForRemovesOnlyPluginMarkers() {
        val guard = PluginFailureGuard(dir)
        guard.mark("alpha@1.0.0:setup.content")
        guard.mark("alpha@1.0.0:tool.card.content:card")
        guard.mark("beta@1.0.0:setup.content")

        assertEquals(2, guard.clearFor("alpha"))
        assertNull(guard.detect("alpha@1.0.0:setup.content"))
        assertNull(guard.detect("alpha@1.0.0:tool.card.content:card"))
        assertEquals("beta@1.0.0:setup.content", guard.detect("beta@1.0.0:setup.content")?.key)
    }

    @Test
    fun keyCharactersAreSafeForFileNames() {
        val guard = PluginFailureGuard(dir)
        guard.mark("plugin.detail.section:my-section", "boom", 7L)
        val marker = guard.detect("plugin.detail.section:my-section", now = 8L)
        assertEquals("plugin.detail.section:my-section", marker?.key)
        assertFalse(dir.listFiles()!!.single().name.contains(':'))
    }

    @Test
    fun corruptMarkerIsStillDetected() {
        val file = File(dir, "guard_corrupt.json")
        file.writeText("{not json")
        file.setLastModified(9000L)

        val marker = PluginFailureGuard(dir).detect("corrupt", now = 9001L)
        assertEquals("corrupt", marker?.key)
        assertNull(marker?.detail)
        assertEquals(9000L, marker?.markedAt)
    }

    @Test
    fun staleMarkerExpiresAndIsRemoved() {
        val guard = PluginFailureGuard(dir)
        guard.mark("tool.card.content:crash", "boom", 1L)

        assertNull(guard.detect("tool.card.content:crash", now = 1L + PluginFailureGuard.MARKER_TTL_MS + 1))
        assertTrue(dir.listFiles()!!.isEmpty())
    }

    @Test
    fun longKeysStayWithinFileBufferLimits() {
        val guard = PluginFailureGuard(dir)
        val key = "plugin.detail.section:" + "abcdefghij".repeat(20)
        guard.mark(key)

        val marker = guard.detect(key)
        assertEquals(key, marker?.key)
        assertTrue(dir.listFiles()!!.single().name.length < 120)
    }
}
