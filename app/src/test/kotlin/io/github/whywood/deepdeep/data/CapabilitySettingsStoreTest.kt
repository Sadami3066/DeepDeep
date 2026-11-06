package io.github.whywood.deepdeep.data

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CapabilitySettingsStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val file: File get() = File(temporaryFolder.root, "capabilities.json")

    @Test
    fun defaultsToScreenOpsDisabled() = runTest {
        val store = CapabilitySettingsStore(file)

        assertFalse(store.screenOps.value)
        assertFalse(file.exists())
    }

    @Test
    fun setPersistsAcrossReload() = runTest {
        val store = CapabilitySettingsStore(file)
        store.setScreenOps(true)

        assertTrue(store.screenOps.value)
        assertTrue(file.isFile)
        assertTrue(CapabilitySettingsStore(file).screenOps.value)
    }

    @Test
    fun toggleFlipsAndPersists() = runTest {
        val store = CapabilitySettingsStore(file)

        assertTrue(store.toggleScreenOps())
        assertTrue(store.screenOps.value)
        assertFalse(store.toggleScreenOps())
        assertFalse(store.screenOps.value)
        assertFalse(CapabilitySettingsStore(file).screenOps.value)
    }

    @Test
    fun invalidFileFallsBackToDefault() = runTest {
        file.writeText("not json")

        assertFalse(CapabilitySettingsStore(file).screenOps.value)
    }
}
