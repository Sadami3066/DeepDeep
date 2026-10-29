package io.github.whywood.deepdeep.plugin

import java.io.File
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PluginSettingsStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun settingsFile() = File(temporaryFolder.root, "plugins/settings.json")

    @Test
    fun missingFileReadsEmptyPenDefaults() = runTest {
        val store = PluginSettingsStore(settingsFile(), StandardTestDispatcher(testScheduler))

        assertTrue(store.readAll("goal").isEmpty())
        assertEquals(7, store.int("goal", "max_rounds", 7))
        assertTrue(store.bool("goal", "enabled", true))
        assertEquals("x", store.string("goal", "mode", "x"))
        assertEquals(5, store.cachedInt("goal", "max_rounds", 5))
    }

    @Test
    fun setPersistsAndUpdatesCache() = runTest {
        val store = PluginSettingsStore(settingsFile(), StandardTestDispatcher(testScheduler))

        store.set("goal", "max_rounds", "5")
        store.set("jobs", "max_running", "3")

        assertEquals(mapOf("max_rounds" to "5"), store.readAll("goal"))
        assertEquals(5, store.cachedInt("goal", "max_rounds", 0))
        assertEquals(3, store.cachedInt("jobs", "max_running", 10))
        val persisted = PluginSettingsStore(settingsFile())
        assertEquals("5", persisted.readAll("goal")["max_rounds"])
        val text = settingsFile().readText()
        assertTrue(text.contains("\"schemaVersion\":1"))
        assertTrue(text.contains("\"max_rounds\":\"5\""))
    }

    @Test
    fun brokenFileFallsBackAndThenRecovers() = runTest {
        settingsFile().parentFile?.mkdirs()
        settingsFile().writeText("not json")
        val store = PluginSettingsStore(settingsFile(), StandardTestDispatcher(testScheduler))

        assertTrue(store.readAll("goal").isEmpty())
        store.set("goal", "max_rounds", "2")
        assertEquals(mapOf("max_rounds" to "2"), store.readAll("goal"))
    }

    @Test
    fun unknownKeysAndPluginsAreIgnored() = runTest {
        settingsFile().parentFile?.mkdirs()
        settingsFile().writeText(
            """{"schemaVersion":1,"plugins":{"goal":{"max_rounds":"4"},"ghost":{"a":"b"}}}""",
        )
        val store = PluginSettingsStore(settingsFile(), StandardTestDispatcher(testScheduler))

        assertEquals(mapOf("max_rounds" to "4"), store.readAll("goal"))
        assertEquals(mapOf("a" to "b"), store.readAll("ghost"))
        assertEquals(9, store.cachedInt("missing", "max_rounds", 9))
    }
}
