package io.github.whywood.deepdeep.plugin.host.update

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PluginSourceSettingsTest {
    private lateinit var dir: File
    private lateinit var file: File
    private lateinit var store: PluginSourceSettingsStore

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("plugin-sources").toFile()
        file = PluginUpdateDefaults.settingsFile(dir)
        store = PluginSourceSettingsStore(file)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun loadsDefaultsWhenFileIsMissing() = runBlocking {
        assertEquals(PluginSourceSettings(), store.load())
        assertFalse(file.exists())
    }

    @Test
    fun savesAndReloadsNormalizedSettings() = runBlocking {
        val saved = store.save(
            PluginSourceSettings(
                indexUrl = "  https://example.test/plugins/index.json  ",
                autoCheck = false,
                intervalHours = 0,
            ),
        )

        assertEquals("https://example.test/plugins/index.json", saved.indexUrl)
        assertFalse(saved.autoCheck)
        assertEquals(PluginUpdateDefaults.MIN_INTERVAL_HOURS, saved.intervalHours)
        assertEquals(saved, store.load())
    }

    @Test
    fun clampsIntervalUpperBound() = runBlocking {
        val saved = store.save(PluginSourceSettings(intervalHours = 10_000))

        assertEquals(PluginUpdateDefaults.MAX_INTERVAL_HOURS, saved.intervalHours)
    }

    @Test
    fun dropsUnsupportedIndexUrlScheme() = runBlocking {
        val saved = store.save(PluginSourceSettings(indexUrl = "ftp://example.test/index.json"))

        assertNull(saved.indexUrl)
        assertNull(store.load().indexUrl)
    }

    @Test
    fun backsUpCorruptFileAndResetsToDefaults() = runBlocking {
        file.parentFile?.mkdirs()
        file.writeText("{ this is not json")

        val loaded = store.load()

        assertEquals(PluginSourceSettings(), loaded)
        assertTrue(File(file.parentFile, file.name + PluginUpdateDefaults.CORRUPT_SUFFIX).isFile)
        assertEquals(PluginSourceSettings(), store.load())
    }

    @Test
    fun resetsOnUnsupportedSchemaVersion() = runBlocking {
        file.parentFile?.mkdirs()
        file.writeText("""{"schemaVersion":2,"indexUrl":"https://example.test/index.json"}""")

        val loaded = store.load()

        assertEquals(PluginSourceSettings(), loaded)
        assertTrue(File(file.parentFile, file.name + PluginUpdateDefaults.CORRUPT_SUFFIX).isFile)
    }
}
