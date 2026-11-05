package io.github.whywood.deepdeep.plugin.host.update

import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PluginIndexClientTest {
    private lateinit var dir: File
    private lateinit var cacheFile: File
    private lateinit var fetcher: FakePluginFetcher

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("plugin-index-client").toFile()
        cacheFile = File(dir, PluginUpdateDefaults.CACHE_FILE_NAME)
        fetcher = FakePluginFetcher()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun client(maxIndexBytes: Long = PluginUpdateDefaults.MAX_INDEX_BYTES): PluginIndexClient =
        PluginIndexClient(cacheFile = cacheFile, fetcher = fetcher, maxIndexBytes = maxIndexBytes)

    @Test
    fun fetchParsesIndexAndWritesCache() = runBlocking {
        fetcher.texts[UpdateFixtures.INDEX_URL] = UpdateFixtures.indexJson(UpdateFixtures.entryJson())

        val result = client().fetch(UpdateFixtures.INDEX_URL)

        assertTrue(result is PluginIndexFetchResult.Loaded)
        val loaded = result as PluginIndexFetchResult.Loaded
        assertFalse(loaded.fromCache)
        assertEquals(1, loaded.index.plugins.size)
        assertEquals(UpdateFixtures.PLUGIN_ID, loaded.index.plugins.single().id)
        assertTrue(cacheFile.isFile)
    }

    @Test
    fun fallsBackToCacheWhenNetworkFails() = runBlocking {
        fetcher.texts[UpdateFixtures.INDEX_URL] = UpdateFixtures.indexJson(UpdateFixtures.entryJson())
        val first = client().fetch(UpdateFixtures.INDEX_URL)
        assertTrue(first is PluginIndexFetchResult.Loaded)

        fetcher.textFailure = IOException("offline")
        val cached = client().fetch(UpdateFixtures.INDEX_URL)

        assertTrue(cached is PluginIndexFetchResult.Loaded)
        val loaded = cached as PluginIndexFetchResult.Loaded
        assertTrue(loaded.fromCache)
        assertNotNull(loaded.note)
        assertEquals(1, loaded.index.plugins.size)
    }

    @Test
    fun cacheOfAnotherSourceIsIgnored() = runBlocking {
        fetcher.texts[UpdateFixtures.INDEX_URL] = UpdateFixtures.indexJson(UpdateFixtures.entryJson())
        client().fetch(UpdateFixtures.INDEX_URL)

        fetcher.textFailure = IOException("offline")
        val other = client().fetch("https://other.example.test/index.json")

        assertTrue(other is PluginIndexFetchResult.Unavailable)
    }

    @Test
    fun invalidIndexWithoutCacheIsUnavailable() = runBlocking {
        fetcher.texts[UpdateFixtures.INDEX_URL] = "not json"

        val result = client().fetch(UpdateFixtures.INDEX_URL)

        assertTrue(result is PluginIndexFetchResult.Unavailable)
    }

    @Test
    fun oversizeIndexIsRejected() = runBlocking {
        fetcher.texts[UpdateFixtures.INDEX_URL] = UpdateFixtures.indexJson(UpdateFixtures.entryJson())

        val result = client(maxIndexBytes = 32L).fetch(UpdateFixtures.INDEX_URL)

        assertTrue(result is PluginIndexFetchResult.Unavailable)
    }
}
