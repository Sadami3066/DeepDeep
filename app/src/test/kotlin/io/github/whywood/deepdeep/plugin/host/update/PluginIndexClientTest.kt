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

    @Test
    fun updatesForSelectsNewerSamePublisherOnly() = runBlocking {
        val index = UpdateFixtures.index(
            UpdateFixtures.entry(id = "io.github.whywood.deepdeep.a", version = "1.1.0"),
            UpdateFixtures.entry(
                id = "io.github.whywood.deepdeep.b",
                version = "1.1.0",
                publisherKeyId = UpdateFixtures.OTHER_KEY_ID,
            ),
            UpdateFixtures.entry(id = "io.github.whywood.deepdeep.c", version = "1.0.0"),
            UpdateFixtures.entry(id = "io.github.whywood.deepdeep.d", version = "0.9.0"),
            UpdateFixtures.entry(id = "io.github.whywood.deepdeep.e", version = "1.1.0"),
        )
        val installed = mapOf(
            "io.github.whywood.deepdeep.a" to UpdateFixtures.record(id = "io.github.whywood.deepdeep.a"),
            "io.github.whywood.deepdeep.b" to UpdateFixtures.record(id = "io.github.whywood.deepdeep.b"),
            "io.github.whywood.deepdeep.c" to UpdateFixtures.record(id = "io.github.whywood.deepdeep.c"),
            "io.github.whywood.deepdeep.d" to UpdateFixtures.record(id = "io.github.whywood.deepdeep.d"),
            "io.github.whywood.deepdeep.e" to UpdateFixtures.record(
                id = "io.github.whywood.deepdeep.e",
                pendingVersion = "1.1.0",
            ),
            "io.github.whywood.deepdeep.f" to UpdateFixtures.record(id = "io.github.whywood.deepdeep.f"),
            "io.github.whywood.deepdeep.g" to UpdateFixtures.record(id = "io.github.whywood.deepdeep.g"),
        )
        val policy = UpdatePolicy(hostApiLevel = 1, hostVersion = "0.2.0")
        val indexWithPolicy = PluginIndex(
            schemaVersion = 1,
            plugins = index.plugins + listOf(
                UpdateFixtures.entry(
                    id = "io.github.whywood.deepdeep.f",
                    version = "1.1.0",
                    apiLevel = 2,
                ),
                UpdateFixtures.entry(
                    id = "io.github.whywood.deepdeep.g",
                    version = "1.1.0",
                    minHostVersion = "9.0.0",
                ),
                UpdateFixtures.entry(
                    id = "io.github.whywood.deepdeep.h",
                    version = "1.1.0",
                    minHostVersion = "0.2.0",
                ),
            ),
        )
        val installedWithPolicy = installed + ("io.github.whywood.deepdeep.h" to UpdateFixtures.record(id = "io.github.whywood.deepdeep.h"))

        val candidates = client().updatesFor(indexWithPolicy, installedWithPolicy, policy)

        assertEquals(listOf("io.github.whywood.deepdeep.a", "io.github.whywood.deepdeep.h"), candidates.map { it.entry.id })
        assertEquals("1.0.0", candidates.first().installedVersion)
        assertEquals(UpdateFixtures.PUBLISHER_KEY_ID, candidates.first().installedKeyId)
    }

    @Test
    fun updatesForUsesCurrentIndexAfterFetch() = runBlocking {
        fetcher.texts[UpdateFixtures.INDEX_URL] = UpdateFixtures.indexJson(UpdateFixtures.entryJson())
        val client = client()
        client.fetch(UpdateFixtures.INDEX_URL)

        val candidates = client.updatesFor(mapOf(UpdateFixtures.PLUGIN_ID to UpdateFixtures.record()))

        assertEquals(1, candidates.size)
        assertEquals(UpdateFixtures.PLUGIN_ID, candidates.single().entry.id)
    }
}
