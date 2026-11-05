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

        val marker = guard.detect("plugin.detail.section:stats")
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

        val markers = guard.detectAll()
        assertEquals(listOf("plugin.detail.section:one", "tool.card.content:two"), markers.map { it.key })
        assertEquals(2, guard.clearAll())
        assertTrue(guard.detectAll().isEmpty())
    }

    @Test
    fun keyCharactersAreSafeForFileNames() {
        val guard = PluginFailureGuard(dir)
        guard.mark("plugin.detail.section:my-section", "boom", 7L)
        val marker = guard.detect("plugin.detail.section:my-section")
        assertEquals("plugin.detail.section:my-section", marker?.key)
        assertFalse(dir.listFiles()!!.single().name.contains(':'))
    }

    @Test
    fun corruptMarkerIsStillDetected() {
        val file = File(dir, "guard_corrupt.json")
        file.writeText("{not json")
        file.setLastModified(9000L)

        val marker = PluginFailureGuard(dir).detect("corrupt")
        assertEquals("corrupt", marker?.key)
        assertNull(marker?.detail)
        assertEquals(9000L, marker?.markedAt)
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
