package io.github.whywood.deepdeep.ui.miniapp

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class MiniappAssetLoaderTest {
    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("miniapp-assets").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun resolvesEntryInsideAppDirectory() {
        val target = file("index.html")
        assertEquals(target.canonicalFile, MiniappAssets.resolveFile(root, "/index.html"))
    }

    @Test
    fun resolvesNestedFile() {
        val target = file("assets/app.js")
        assertEquals(target.canonicalFile, MiniappAssets.resolveFile(root, "/assets/app.js"))
    }

    @Test
    fun rejectsParentTraversal() {
        file("index.html")
        file("assets/app.js")
        assertNull(MiniappAssets.resolveFile(root, "/../index.html"))
        assertNull(MiniappAssets.resolveFile(root, "/assets/../index.html"))
        assertNull(MiniappAssets.resolveFile(root, "../" + root.name + "/index.html"))
    }

    @Test
    fun rejectsAbsolutePathEscape() {
        assertNull(MiniappAssets.resolveFile(root, "/etc/passwd"))
    }

    @Test
    fun rejectsSymlinkEscape() {
        val outside = Files.createTempFile("miniapp-outside", ".txt").toFile()
        try {
            outside.writeText("secret")
            Files.createSymbolicLink(File(root, "escape.txt").toPath(), outside.toPath())
            File(root, "points").mkdirs()
            Files.createSymbolicLink(File(root, "points/out.txt").toPath(), outside.toPath())
            assertNull(MiniappAssets.resolveFile(root, "/escape.txt"))
            assertNull(MiniappAssets.resolveFile(root, "/points/out.txt"))
        } finally {
            outside.delete()
        }
    }

    @Test
    fun rejectsMissingAndDirectoryPaths() {
        File(root, "folder").mkdirs()
        assertNull(MiniappAssets.resolveFile(root, "/missing.html"))
        assertNull(MiniappAssets.resolveFile(root, "/folder"))
        assertNull(MiniappAssets.resolveFile(root, "/"))
        assertNull(MiniappAssets.resolveFile(root, ""))
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
            assertEquals(name, mime, MiniappAssets.mimeTypeFor(name))
        }
        assertEquals(MiniappAssets.FALLBACK_MIME, MiniappAssets.mimeTypeFor("archive.bin"))
        assertEquals(MiniappAssets.FALLBACK_MIME, MiniappAssets.mimeTypeFor("noextension"))
        assertEquals("text/html", MiniappAssets.mimeTypeFor("PAGE.HTML"))
        assertEquals("text/html", MiniappAssets.mimeTypeFor("/nested/path/index.html"))
    }

    @Test
    fun usesUtf8OnlyForTextTypes() {
        assertEquals("utf-8", MiniappAssets.encodingFor("text/html"))
        assertEquals("utf-8", MiniappAssets.encodingFor("text/javascript"))
        assertEquals("utf-8", MiniappAssets.encodingFor("application/json"))
        assertEquals("utf-8", MiniappAssets.encodingFor("image/svg+xml"))
        assertNull(MiniappAssets.encodingFor("image/png"))
        assertNull(MiniappAssets.encodingFor("application/wasm"))
        assertNull(MiniappAssets.encodingFor("application/octet-stream"))
    }

    @Test
    fun parsesClosedRange() {
        assertEquals(MiniappByteRange(0, 99), MiniappAssets.parseRange("bytes=0-99", 1000))
        assertEquals(MiniappByteRange(0, 0), MiniappAssets.parseRange("bytes=0-0", 1000))
        assertEquals(MiniappByteRange(900, 999), MiniappAssets.parseRange("bytes=900-5000", 1000))
    }

    @Test
    fun parsesOpenEndedRange() {
        assertEquals(MiniappByteRange(500, 999), MiniappAssets.parseRange("bytes=500-", 1000))
        assertEquals(MiniappByteRange(999, 999), MiniappAssets.parseRange("bytes=999-", 1000))
    }

    @Test
    fun parsesSuffixRange() {
        assertEquals(MiniappByteRange(900, 999), MiniappAssets.parseRange("bytes=-100", 1000))
        assertEquals(MiniappByteRange(0, 999), MiniappAssets.parseRange("bytes=-5000", 1000))
    }

    @Test
    fun rejectsInvalidRanges() {
        assertNull(MiniappAssets.parseRange(null, 1000))
        assertNull(MiniappAssets.parseRange("", 1000))
        assertNull(MiniappAssets.parseRange("items=0-10", 1000))
        assertNull(MiniappAssets.parseRange("bytes=abc", 1000))
        assertNull(MiniappAssets.parseRange("bytes=0-1,5-6", 1000))
        assertNull(MiniappAssets.parseRange("bytes=-0", 1000))
        assertNull(MiniappAssets.parseRange("bytes=1000-", 1000))
        assertNull(MiniappAssets.parseRange("bytes=200-100", 1000))
        assertNull(MiniappAssets.parseRange("bytes=0-10", 0))
    }

    private fun file(path: String, content: String = "content"): File {
        val target = File(root, path)
        target.parentFile?.mkdirs()
        target.writeText(content)
        return target
    }
}
