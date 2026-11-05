package io.github.whywood.deepdeep.plugin.host.external

import io.github.whywood.deepdeep.plugin.api.PluginSource
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

class ExternalPluginRecordStoreTest {
    private lateinit var dir: File
    private lateinit var file: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("external-records").toFile()
        file = File(dir, "plugins.json")
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun missingFileLoadsEmpty() = runBlocking {
        val store = ExternalPluginRecordStore(file)

        assertTrue(store.load().isEmpty())
        assertNull(store.get("missing"))
        assertFalse(file.exists())
    }

    @Test
    fun destructiveResetFromOldSchema() = runBlocking {
        val legacy = """{"schemaVersion":2,"plugins":[{"id":"old","version":"1.0.0","installedAt":1,"skillIds":[],"enabled":true}]}"""
        file.writeText(legacy)

        val store = ExternalPluginRecordStore(file)

        assertTrue(store.load().isEmpty())
        val backup = File(dir, "plugins.json.v2.bak")
        assertTrue(backup.isFile)
        assertEquals(legacy, backup.readText())
        val rewritten = JsonFiles.read(file)
        assertEquals("4", rewritten!!["schemaVersion"].toString())
    }

    @Test
    fun corruptFileIsBackedUpAndReset() = runBlocking {
        file.writeText("{broken")

        val store = ExternalPluginRecordStore(file)

        assertTrue(store.load().isEmpty())
        assertTrue(File(dir, "plugins.json.v2.bak").isFile)
        assertEquals("4", JsonFiles.read(file)!!["schemaVersion"].toString())
    }

    @Test
    fun roundTripsRecordFields() = runBlocking {
        val record = record()
        ExternalPluginRecordStore(file).upsert(record)

        val loaded = ExternalPluginRecordStore(file).get(record.id)

        assertEquals(record, loaded)
    }

    @Test
    fun upsertAndRemoveKeepOtherRecords() = runBlocking {
        val store = ExternalPluginRecordStore(file)
        val first = record("io.github.whywood.deepdeep.first")
        val second = record("io.github.whywood.deepdeep.second")
        store.upsert(first)
        store.upsert(second)

        assertTrue(store.remove(first.id))
        assertEquals(listOf(second), store.records())
        assertFalse(store.remove(first.id))
    }

    @Test
    fun setEnabledUpdatesRecord() = runBlocking {
        val store = ExternalPluginRecordStore(file)
        val record = record()
        store.upsert(record)

        assertTrue(store.setEnabled(record.id, false))
        assertEquals(false, store.get(record.id)?.enabled)
        assertFalse(store.setEnabled("missing", true))
    }

    @Test
    fun recordsAreSortedById() = runBlocking {
        val store = ExternalPluginRecordStore(file)
        store.upsert(record("io.github.whywood.deepdeep.zzz"))
        store.upsert(record("io.github.whywood.deepdeep.aaa"))

        assertEquals(
            listOf("io.github.whywood.deepdeep.aaa", "io.github.whywood.deepdeep.zzz"),
            store.records().map { it.id },
        )
    }

    @Test
    fun mutateReplacesWholeStoreAtomically() = runBlocking {
        val store = ExternalPluginRecordStore(file)
        val record = record()
        store.upsert(record)

        store.mutate { current ->
            current.mapValues { (_, value) -> value.copy(lastError = "boom") }
        }

        val updated = store.get(record.id)!!
        assertEquals("boom", updated.lastError)
    }

    @Test
    fun persistsPendingFields() = runBlocking {
        val store = ExternalPluginRecordStore(file)
        val record = record().copy(
            pendingVersion = "2.0.0",
            pendingBundleSha256 = "pending-sha",
        )
        store.upsert(record)

        assertEquals(record, ExternalPluginRecordStore(file).get(record.id))
    }

    @Test
    fun persistsPreviousFields() = runBlocking {
        val store = ExternalPluginRecordStore(file)
        val base = record()
        val record = base.copy(
            previousVersion = "0.9.0",
            previousInstallPath = "/tmp/plugins/${base.id}/0.9.0",
            previousBundleSha256 = "previous-sha",
        )
        store.upsert(record)

        assertEquals(record, ExternalPluginRecordStore(file).get(record.id))
    }

    @Test
    fun loadsV4RecordWithoutPreviousFields() = runBlocking {
        file.writeText(
            """{"schemaVersion":4,"plugins":[{"id":"old","version":"1.0.0","installedAt":1,"enabled":true,""" +
                """"skillIds":[],"source":"EXTERNAL","installPath":"/tmp/plugins/old/1.0.0",""" +
                """"activeBundleSha256":"abc","size":1,"publisherKeyId":"ed25519:aa","apiLevel":1,"uiApiLevel":1}]}""",
        )

        val loaded = ExternalPluginRecordStore(file).get("old")

        assertEquals("1.0.0", loaded?.version)
        assertNull(loaded?.previousVersion)
        assertNull(loaded?.previousInstallPath)
        assertNull(loaded?.previousBundleSha256)
        assertNull(loaded?.pendingVersion)
    }

    private fun record(id: String = "io.github.whywood.deepdeep.plugintest"): ExternalPluginRecord = ExternalPluginRecord(
        id = id,
        version = "1.0.0",
        installedAt = 1000L,
        enabled = true,
        skillIds = listOf("skill_a"),
        source = PluginSource.EXTERNAL,
        installPath = "/tmp/plugins/$id/1.0.0",
        activeBundleSha256 = "abc123",
        size = 4096L,
        publisherKeyId = "ed25519:deadbeef",
        apiLevel = 1,
        uiApiLevel = 1,
    )
}
