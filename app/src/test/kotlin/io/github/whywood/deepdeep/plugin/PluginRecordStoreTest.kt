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
    fun writesAreReadableBySecondStore() = runTest {
        val file = File(temporaryFolder.root, "plugins/plugins.json")
        PluginRecordStore(file).upsert(PluginRecord("a", "1", 1L))
        val reloaded = PluginRecordStore(file).load()
        assertFalse(reloaded.records.isEmpty())
        assertEquals("1", reloaded.records.getValue("a").version)
    }
}
