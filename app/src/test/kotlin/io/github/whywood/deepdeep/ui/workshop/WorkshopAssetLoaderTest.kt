package io.github.whywood.deepdeep.ui.workshop

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class WorkshopAssetLoaderTest {
    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("workshop-assets").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun resolvesEntryInsideAppDirectory() {
        val target = file("index.html")
        assertEquals(target.canonicalFile, WorkshopAssets.resolveFile(root, "/index.html"))
    }

    @Test
    fun resolvesNestedFile() {
        val target = file("assets/app.js")
        assertEquals(target.canonicalFile, WorkshopAssets.resolveFile(root, "/assets/app.js"))
    }

    @Test
    fun rejectsParentTraversal() {
        file("index.html")
        file("assets/app.js")
        assertNull(WorkshopAssets.resolveFile(root, "/../index.html"))
        assertNull(WorkshopAssets.resolveFile(root, "/assets/../index.html"))
        assertNull(WorkshopAssets.resolveFile(root, "../" + root.name + "/index.html"))
    }

    @Test
    fun rejectsAbsolutePathEscape() {
        assertNull(WorkshopAssets.resolveFile(root, "/etc/passwd"))
    }

    @Test
    fun rejectsSymlinkEscape() {
        val outside = Files.createTempFile("workshop-outside", ".txt").toFile()
        try {
            outside.writeText("secret")
            Files.createSymbolicLink(File(root, "escape.txt").toPath(), outside.toPath())
            File(root, "points").mkdirs()
            Files.createSymbolicLink(File(root, "points/out.txt").toPath(), outside.toPath())
            assertNull(WorkshopAssets.resolveFile(root, "/escape.txt"))
            assertNull(WorkshopAssets.resolveFile(root, "/points/out.txt"))
        } finally {
            outside.delete()
        }
    }

    @Test
    fun rejectsMissingAndDirectoryPaths() {
        File(root, "folder").mkdirs()
        assertNull(WorkshopAssets.resolveFile(root, "/missing.html"))
        assertNull(WorkshopAssets.resolveFile(root, "/folder"))
        assertNull(WorkshopAssets.resolveFile(root, "/"))
        assertNull(WorkshopAssets.resolveFile(root, ""))
    }

    @Test
    fun mapsKnownMimeTypes() {
        val expected = mapOf(
            "index.html" to "text/html",
            "style.css" to "text/css",
            "app.js" to "text/javascript",
            "mod.mjs" to "text/javascript",
            "data.json" to "application/json",
            "icon.svg" to "image/svg+xml",
            "pixel.png" to "image/png",
            "photo.jpg" to "image/jpeg",
            "photo.jpeg" to "image/jpeg",
            "photo.webp" to "image/webp",
            "anim.gif" to "image/gif",
            "favicon.ico" to "image/x-icon",
            "font.woff2" to "font/woff2",
            "font.woff" to "font/woff",
            "font.ttf" to "font/ttf",
            "font.otf" to "font/otf",
            "clip.mp4" to "video/mp4",
            "clip.webm" to "video/webm",
            "sound.mp3" to "audio/mpeg",
            "sound.wav" to "audio/wav",
            "module.wasm" to "application/wasm",
        )
        expected.forEach { (name, mime) ->
            assertEquals(name, mime, WorkshopAssets.mimeTypeFor(name))
        }
        assertEquals(WorkshopAssets.FALLBACK_MIME, WorkshopAssets.mimeTypeFor("archive.bin"))
        assertEquals(WorkshopAssets.FALLBACK_MIME, WorkshopAssets.mimeTypeFor("noextension"))
        assertEquals("text/html", WorkshopAssets.mimeTypeFor("PAGE.HTML"))
        assertEquals("text/html", WorkshopAssets.mimeTypeFor("/nested/path/index.html"))
    }

    @Test
    fun usesUtf8OnlyForTextTypes() {
        assertEquals("utf-8", WorkshopAssets.encodingFor("text/html"))
        assertEquals("utf-8", WorkshopAssets.encodingFor("text/javascript"))
        assertEquals("utf-8", WorkshopAssets.encodingFor("application/json"))
        assertEquals("utf-8", WorkshopAssets.encodingFor("image/svg+xml"))
        assertNull(WorkshopAssets.encodingFor("image/png"))
        assertNull(WorkshopAssets.encodingFor("application/wasm"))
        assertNull(WorkshopAssets.encodingFor("application/octet-stream"))
    }

    @Test
    fun parsesClosedRange() {
        assertEquals(WorkshopByteRange(0, 99), WorkshopAssets.parseRange("bytes=0-99", 1000))
        assertEquals(WorkshopByteRange(0, 0), WorkshopAssets.parseRange("bytes=0-0", 1000))
        assertEquals(WorkshopByteRange(900, 999), WorkshopAssets.parseRange("bytes=900-5000", 1000))
    }

    @Test
    fun parsesOpenEndedRange() {
        assertEquals(WorkshopByteRange(500, 999), WorkshopAssets.parseRange("bytes=500-", 1000))
        assertEquals(WorkshopByteRange(999, 999), WorkshopAssets.parseRange("bytes=999-", 1000))
    }

    @Test
    fun parsesSuffixRange() {
        assertEquals(WorkshopByteRange(900, 999), WorkshopAssets.parseRange("bytes=-100", 1000))
        assertEquals(WorkshopByteRange(0, 999), WorkshopAssets.parseRange("bytes=-5000", 1000))
    }

    @Test
    fun rejectsInvalidRanges() {
        assertNull(WorkshopAssets.parseRange(null, 1000))
        assertNull(WorkshopAssets.parseRange("", 1000))
        assertNull(WorkshopAssets.parseRange("items=0-10", 1000))
        assertNull(WorkshopAssets.parseRange("bytes=abc", 1000))
        assertNull(WorkshopAssets.parseRange("bytes=0-1,5-6", 1000))
        assertNull(WorkshopAssets.parseRange("bytes=-0", 1000))
        assertNull(WorkshopAssets.parseRange("bytes=1000-", 1000))
        assertNull(WorkshopAssets.parseRange("bytes=200-100", 1000))
        assertNull(WorkshopAssets.parseRange("bytes=0-10", 0))
    }

    private fun file(path: String, content: String = "content"): File {
        val target = File(root, path)
        target.parentFile?.mkdirs()
        target.writeText(content)
        return target
    }
}
