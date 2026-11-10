package io.github.whywood.deepdeep.ui.common.chat

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownImageTest {
    @Test
    fun unreadableFileIsStagedBeforeDecoding() = runBlocking {
        val local = File("/data/data/io.github.whywood.deepdeep/files/photo.gif")
        val staged = File("/cache/handoff/ab12cd34/photo.gif")
        val stagedFrom = mutableListOf<File>()
        val resolved = readableMarkdownLocalFile(
            localFile = local,
            isHostFile = { true },
            canRead = { false },
            stage = { file ->
                stagedFrom += file
                staged
            },
        )
        assertEquals(listOf(local), stagedFrom)
        assertEquals(staged, resolved)
    }

    @Test
    fun readableFileIsUsedDirectly() = runBlocking {
        val local = File("/sdcard/Pictures/photo.gif")
        var staged = false
        val resolved = readableMarkdownLocalFile(
            localFile = local,
            isHostFile = { true },
            canRead = { true },
            stage = {
                staged = true
                null
            },
        )
        assertFalse(staged)
        assertEquals(local, resolved)
    }

    @Test
    fun missingHostFileIsNotStaged() = runBlocking {
        var staged = false
        val resolved = readableMarkdownLocalFile(
            localFile = File("/sdcard/Pictures/missing.gif"),
            isHostFile = { false },
            canRead = { false },
            stage = {
                staged = true
                File("/cache/staged.gif")
            },
        )
        assertFalse(staged)
        assertNull(resolved)
    }

    @Test
    fun missingLocalFileReturnsNull() = runBlocking {
        val resolved = readableMarkdownLocalFile(
            localFile = null,
            isHostFile = { true },
            canRead = { true },
            stage = { File("/cache/staged.gif") },
        )
        assertNull(resolved)
    }

    @Test
    fun failedStagingReturnsNull() = runBlocking {
        val resolved = readableMarkdownLocalFile(
            localFile = File("/data/data/io.github.whywood.deepdeep/files/photo.gif"),
            isHostFile = { true },
            canRead = { false },
            stage = { null },
        )
        assertNull(resolved)
    }

    @Test
    fun animatedImageNamesCoverSupportedExtensions() {
        assertTrue(isAnimatedImageName("photo.gif"))
        assertTrue(isAnimatedImageName("photo.GIF"))
        assertTrue(isAnimatedImageName("photo.webp"))
        assertTrue(isAnimatedImageName("photo.apng"))
        assertTrue(isAnimatedImageName("photo.avif"))
        assertFalse(isAnimatedImageName("photo.png"))
        assertFalse(isAnimatedImageName("photo.jpg"))
        assertFalse(isAnimatedImageName("photo"))
    }
}
