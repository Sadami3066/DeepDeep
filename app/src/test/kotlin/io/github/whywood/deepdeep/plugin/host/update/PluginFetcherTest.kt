package io.github.whywood.deepdeep.plugin.host.update

import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PluginFetcherTest {
    private lateinit var dir: File
    private lateinit var fetcher: HttpPluginFetcher

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("plugin-fetcher").toFile()
        fetcher = HttpPluginFetcher()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun readsTextFromFileUrl() = runBlocking {
        val file = File(dir, "index.json").apply { writeText(UpdateFixtures.indexJson(UpdateFixtures.entryJson())) }

        val text = fetcher.readText(file.toURI().toString(), PluginUpdateDefaults.MAX_INDEX_BYTES)

        assertEquals(file.readText(), text)
    }

    @Test
    fun rejectsOversizeFileText() = runBlocking {
        val file = File(dir, "index.json").apply { writeText("x".repeat(256)) }

        val failure = runCatching { fetcher.readText(file.toURI().toString(), maxBytes = 16L) }

        assertTrue(failure.exceptionOrNull() is IOException)
    }

    @Test
    fun downloadsFileAndReportsProgress() = runBlocking {
        val source = File(dir, "plugin.ddp").apply { writeBytes(ByteArray(4096) { it.toByte() }) }
        val target = File(dir, "downloads/plugin.ddp")
        val progress = mutableListOf<Int?>()

        fetcher.download(source.toURI().toString(), target, PluginUpdateDefaults.MAX_PACKAGE_BYTES) {
            progress += it
        }

        assertTrue(target.isFile)
        assertEquals(4096L, target.length())
        assertTrue(progress.isNotEmpty())
    }

    @Test
    fun rejectsOversizeDownload() = runBlocking {
        val source = File(dir, "plugin.ddp").apply { writeBytes(ByteArray(64)) }
        val target = File(dir, "downloads/plugin.ddp")

        val failure = runCatching {
            fetcher.download(source.toURI().toString(), target, maxBytes = 8L) { }
        }

        assertTrue(failure.exceptionOrNull() is IOException)
    }

    @Test
    fun rejectsUnsupportedScheme() = runBlocking {
        val failure = runCatching { fetcher.readText("ftp://example.test/index.json", 1024L) }

        assertTrue(failure.exceptionOrNull() is IOException)
    }
}
