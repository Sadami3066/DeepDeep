package io.github.whywood.deepdeep.plugin.host.extensions.data

import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PluginSettingsStoreTest {
    private lateinit var directory: File
    private lateinit var file: File

    @Before
    fun setUp() {
        directory = java.nio.file.Files.createTempDirectory("plugin-settings").toFile()
        file = File(directory, "host/settings.json")
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun storesValuesAndPersistsAtomically() = runBlocking {
        val store = PluginSettingsStore(file)
        store.put("theme", "dark")
        store.put("limit", "10")

        assertTrue(file.isFile)
        assertFalse(File(file.parentFile, "${file.name}.tmp").exists())
        val reopened = PluginSettingsStore(file)
        assertEquals("dark", reopened.get("theme"))
        assertEquals(setOf("limit", "theme"), reopened.keys())
    }

    @Test
    fun overlaysValuesAfterReload() = runBlocking {
        PluginSettingsStore(file).put("first", "1")
        val store = PluginSettingsStore(file)
        assertEquals("1", store.get("first"))

        store.put("second", "2")
        assertEquals(mapOf("first" to "1", "second" to "2"), store.snapshot())
    }

    @Test
    fun removesAndClears() = runBlocking {
        val store = PluginSettingsStore(file)
        store.put("a", "1")
        store.put("b", "2")

        store.remove("a")
        assertNull(store.get("a"))
        assertEquals(mapOf("b" to "2"), store.snapshot())

        store.clear()
        assertEquals(emptyMap<String, String>(), store.snapshot())
        assertEquals(emptyMap<String, String>(), PluginSettingsStore(file).snapshot())
    }

    @Test
    fun roundTripsJsonValues() = runBlocking {
        val store = PluginSettingsStore(file)
        store.putJson("profile", buildJsonObject {
            put("name", "demo")
            put("pages", 3)
        })

        val reopened = PluginSettingsStore(file)
        val json = reopened.getJson("profile")
        assertEquals("demo", json?.get("name")?.jsonPrimitive?.content)
        assertEquals("3", json?.get("pages")?.jsonPrimitive?.content)
        assertNull(reopened.getJson("missing"))
    }

    @Test
    fun observeEmitsSnapshotAndFiltersKey() = runBlocking {
        val store = PluginSettingsStore(file)
        store.put("a", "1")
        store.put("b", "2")

        assertEquals(mapOf("a" to "1", "b" to "2"), store.observe().first())
        assertEquals(mapOf("a" to "1"), store.observe("a").first())
        assertEquals(emptyMap<String, String>(), store.observe("missing").first())
    }

    @Test
    fun corruptedFileFallsBackToEmpty() = runBlocking {
        file.parentFile.mkdirs()
        file.writeText("{not json")
        val store = PluginSettingsStore(file)
        assertEquals(emptyMap<String, String>(), store.snapshot())
        store.put("ok", "yes")
        assertEquals("yes", PluginSettingsStore(file).get("ok"))
    }
}
