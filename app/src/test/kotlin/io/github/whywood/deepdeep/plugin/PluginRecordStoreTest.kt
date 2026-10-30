package io.github.whywood.deepdeep.plugin

import java.io.File
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PluginRecordStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun loadMissingFileReturnsEmpty() = runTest {
        val store = PluginRecordStore(File(temporaryFolder.root, "plugins/plugins.json"))
        val snapshot = store.load()
        assertTrue(snapshot.records.isEmpty())
        assertNull(snapshot.error)
    }

    @Test
    fun upsertAndRemoveRoundTrip() = runTest {
        val store = PluginRecordStore(File(temporaryFolder.root, "plugins/plugins.json"))
        store.upsert(PluginRecord("feishu", "1.0.98", 123L, listOf("deepdeep-feishu")))
        val loaded = store.load()
        assertNotNull(loaded.records["feishu"])
        assertEquals("1.0.98", loaded.records.getValue("feishu").version)
        assertEquals(listOf("deepdeep-feishu"), loaded.records.getValue("feishu").skillIds)

        store.remove("feishu")
        assertTrue(store.load().records.isEmpty())
    }

    @Test
    fun corruptFileIsPreservedAndReported() = runTest {
        val file = File(temporaryFolder.root, "plugins/plugins.json")
        file.parentFile.mkdirs()
        file.writeText("{not json")
        val store = PluginRecordStore(file)
        val snapshot = store.load()
        assertNotNull(snapshot.error)
        assertTrue(file.isFile)
        assertTrue(file.readText().startsWith("{not json"))
    }

    @Test
    fun backupCorruptMovesDataAsideAndLeavesWritableFile() = runTest {
        val file = File(temporaryFolder.root, "plugins/plugins.json")
        file.parentFile.mkdirs()
        file.writeText("{not json")
        val store = PluginRecordStore(file)

        assertTrue(store.backupCorrupt())
        assertEquals("{not json", File(file.parentFile, "plugins.json.corrupt").readText())
        val rewritten = store.load()
        assertNull(rewritten.error)
        assertTrue(rewritten.records.isEmpty())

        file.writeText("{not json")
        assertTrue(store.backupCorrupt())
        assertNull(store.load().error)

        file.writeText("{second")
        assertTrue(store.backupCorrupt())
        assertEquals("{not json", File(file.parentFile, "plugins.json.corrupt").readText())
        assertEquals("{second", File(file.parentFile, "plugins.json.corrupt-1").readText())
        assertNull(store.load().error)
    }

    @Test
    fun writesAreReadableBySecondStore() = runTest {
        val file = File(temporaryFolder.root, "plugins/plugins.json")
        PluginRecordStore(file).upsert(PluginRecord("a", "1", 1L))
        val reloaded = PluginRecordStore(file).load()
        assertFalse(reloaded.records.isEmpty())
        assertEquals("1", reloaded.records.getValue("a").version)
    }

    @Test
    fun oldSchemaWithoutEnabledDefaultsToTrue() = runTest {
        val file = File(temporaryFolder.root, "plugins/plugins.json")
        file.parentFile.mkdirs()
        file.writeText(
            """{"schemaVersion":1,"plugins":[{"id":"feishu","version":"1.0.98","installedAt":5,"skillIds":["s"]}]}""",
        )
        val record = PluginRecordStore(file).load().records.getValue("feishu")
        assertTrue(record.enabled)
        assertEquals(listOf("s"), record.skillIds)
    }

    @Test
    fun setEnabledPersistsAndUpgradesSchema() = runTest {
        val file = File(temporaryFolder.root, "plugins/plugins.json")
        val store = PluginRecordStore(file)
        store.upsert(PluginRecord("feishu", "2.0", 7L, listOf("s")))
        store.setEnabled("feishu", false)

        val record = PluginRecordStore(file).load().records.getValue("feishu")
        assertFalse(record.enabled)
        assertEquals("2.0", record.version)
        assertEquals(listOf("s"), record.skillIds)
        assertTrue(file.readText().contains("\"schemaVersion\":2"))
        assertTrue(file.readText().contains("\"enabled\":false"))

        store.setEnabled("feishu", true)
        assertTrue(PluginRecordStore(file).load().records.getValue("feishu").enabled)
    }

    @Test
    fun setEnabledIgnoresMissingRecord() = runTest {
        val file = File(temporaryFolder.root, "plugins/plugins.json")
        PluginRecordStore(file).setEnabled("missing", false)
        assertTrue(PluginRecordStore(file).load().records.isEmpty())
    }
}
