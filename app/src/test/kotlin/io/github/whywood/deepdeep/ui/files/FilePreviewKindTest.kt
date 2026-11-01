package io.github.whywood.deepdeep.ui.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FilePreviewKindTest {
    @Test
    fun imageExtensionsMapToImage() {
        val names = listOf("a.png", "a.jpg", "a.jpeg", "a.webp", "a.gif", "a.bmp", "a.heic", "a.heif", "a.avif")
        names.forEach { name ->
            assertEquals(name, FilePreviewKind.IMAGE, filePreviewKind(name, "application/octet-stream"))
        }
    }

    @Test
    fun videoExtensionsMapToVideo() {
        val names = listOf("a.mp4", "a.m4v", "a.webm", "a.mkv", "a.mov", "a.3gp")
        names.forEach { name ->
            assertEquals(name, FilePreviewKind.VIDEO, filePreviewKind(name, "application/octet-stream"))
        }
    }

    @Test
    fun htmlExtensionsMapToHtml() {
        assertEquals(FilePreviewKind.HTML, filePreviewKind("page.html", "text/plain"))
        assertEquals(FilePreviewKind.HTML, filePreviewKind("page.htm", "application/octet-stream"))
        assertEquals(FilePreviewKind.HTML, filePreviewKind("page.xhtml", "application/octet-stream"))
    }

    @Test
    fun markdownExtensionsMapToMarkdown() {
        val names = listOf("a.md", "a.markdown", "a.mdown", "a.mkd")
        names.forEach { name ->
            assertEquals(name, FilePreviewKind.MARKDOWN, filePreviewKind(name, "application/octet-stream"))
        }
    }

    @Test
    fun codeExtensionsMapToCode() {
        val names = listOf(
            "a.txt", "a.log", "a.json", "a.xml", "a.yaml", "a.yml", "a.toml", "a.kt", "a.kts", "a.java",
            "a.py", "a.js", "a.ts", "a.tsx", "a.jsx", "a.c", "a.h", "a.cpp", "a.hpp", "a.css", "a.sh",
            "a.bash", "a.sql", "a.rs", "a.go", "a.swift", "a.rb", "a.csv", "a.ini", "a.conf",
        )
        names.forEach { name ->
            assertEquals(name, FilePreviewKind.CODE, filePreviewKind(name, "application/octet-stream"))
        }
    }

    @Test
    fun extensionsAreCaseInsensitive() {
        assertEquals(FilePreviewKind.IMAGE, filePreviewKind("A.PNG", ""))
        assertEquals(FilePreviewKind.MARKDOWN, filePreviewKind("README.MD", ""))
        assertEquals(FilePreviewKind.VIDEO, filePreviewKind("Clip.MP4", ""))
        assertEquals(FilePreviewKind.CODE, filePreviewKind("Main.KT", ""))
    }

    @Test
    fun mimeFallbackCoversImageAndVideo() {
        assertEquals(FilePreviewKind.IMAGE, filePreviewKind("noext", "image/png"))
        assertEquals(FilePreviewKind.VIDEO, filePreviewKind("noext", "video/mp4"))
    }

    @Test
    fun mimeFallbackCoversHtmlAndMarkdown() {
        assertEquals(FilePreviewKind.HTML, filePreviewKind("noext", "text/html"))
        assertEquals(FilePreviewKind.MARKDOWN, filePreviewKind("noext", "text/markdown"))
    }

    @Test
    fun mimeFallbackCodeTypes() {
        assertEquals(FilePreviewKind.CODE, filePreviewKind("noext", "text/plain"))
        assertEquals(FilePreviewKind.CODE, filePreviewKind("noext", "application/json"))
        assertEquals(FilePreviewKind.CODE, filePreviewKind("noext", "application/xml"))
        assertEquals(FilePreviewKind.CODE, filePreviewKind("noext", "application/javascript"))
        assertEquals(FilePreviewKind.CODE, filePreviewKind("noext", "application/x-yaml"))
    }

    @Test
    fun htmlMimeIsNotSwallowedByTextPrefix() {
        assertEquals(FilePreviewKind.HTML, filePreviewKind("noext", "text/html; charset=utf-8"))
        assertEquals(FilePreviewKind.MARKDOWN, filePreviewKind("noext", "text/markdown; charset=utf-8"))
    }

    @Test
    fun extensionWinsOverMime() {
        assertEquals(FilePreviewKind.MARKDOWN, filePreviewKind("notes.md", "text/plain"))
        assertEquals(FilePreviewKind.CODE, filePreviewKind("main.kt", "text/html"))
        assertEquals(FilePreviewKind.IMAGE, filePreviewKind("photo.png", "application/octet-stream"))
    }

    @Test
    fun unknownTypeIsUnsupported() {
        assertEquals(FilePreviewKind.UNSUPPORTED, filePreviewKind("archive.zip", "application/zip"))
        assertEquals(FilePreviewKind.UNSUPPORTED, filePreviewKind("noext", "application/octet-stream"))
        assertEquals(FilePreviewKind.UNSUPPORTED, filePreviewKind("noext", ""))
        assertEquals(FilePreviewKind.UNSUPPORTED, filePreviewKind("", ""))
    }

    @Test
    fun codeLanguageForKnownExtensions() {
        assertEquals("kotlin", codeLanguageFor("a.kt"))
        assertEquals("kotlin", codeLanguageFor("a.kts"))
        assertEquals("java", codeLanguageFor("a.java"))
        assertEquals("js", codeLanguageFor("a.js"))
        assertEquals("js", codeLanguageFor("a.jsx"))
        assertEquals("ts", codeLanguageFor("a.ts"))
        assertEquals("ts", codeLanguageFor("a.tsx"))
        assertEquals("python", codeLanguageFor("a.py"))
        assertEquals("bash", codeLanguageFor("a.sh"))
        assertEquals("bash", codeLanguageFor("a.bash"))
        assertEquals("json", codeLanguageFor("a.json"))
        assertEquals("xml", codeLanguageFor("a.xml"))
        assertEquals("html", codeLanguageFor("a.html"))
        assertEquals("html", codeLanguageFor("a.htm"))
        assertEquals("css", codeLanguageFor("a.css"))
        assertEquals("sql", codeLanguageFor("a.sql"))
        assertEquals("yaml", codeLanguageFor("a.yaml"))
        assertEquals("yaml", codeLanguageFor("a.yml"))
        assertEquals("toml", codeLanguageFor("a.toml"))
        assertEquals("go", codeLanguageFor("a.go"))
        assertEquals("rust", codeLanguageFor("a.rs"))
        assertEquals("c", codeLanguageFor("a.c"))
        assertEquals("c", codeLanguageFor("a.h"))
        assertEquals("cpp", codeLanguageFor("a.cpp"))
        assertEquals("cpp", codeLanguageFor("a.hpp"))
        assertEquals("swift", codeLanguageFor("a.swift"))
        assertEquals("ruby", codeLanguageFor("a.rb"))
    }

    @Test
    fun codeLanguageForIsCaseInsensitive() {
        assertEquals("kotlin", codeLanguageFor("A.KT"))
        assertEquals("cpp", codeLanguageFor("A.CPP"))
    }

    @Test
    fun codeLanguageForPlainTextIsNull() {
        assertNull(codeLanguageFor("a.md"))
        assertNull(codeLanguageFor("a.txt"))
        assertNull(codeLanguageFor("a.log"))
        assertNull(codeLanguageFor("a.csv"))
        assertNull(codeLanguageFor("a.ini"))
        assertNull(codeLanguageFor("a.conf"))
        assertNull(codeLanguageFor("a.zip"))
        assertNull(codeLanguageFor("noext"))
        assertNull(codeLanguageFor(""))
    }
}
