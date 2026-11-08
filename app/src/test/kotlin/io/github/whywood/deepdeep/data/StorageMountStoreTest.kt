package io.github.whywood.deepdeep.data

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageMountStoreTest {
    private val directory = File(System.getProperty("java.io.tmpdir"), "storage-mounts-${System.nanoTime()}")

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun normalizeDropsInvalidEntriesAndDuplicatePaths() {
        val normalized = normalizeMounts(
            listOf(
                StorageMount("", "A", "", "/storage/emulated/0/A"),
                StorageMount("", "notes", "", "/storage/emulated/0/B"),
                StorageMount("", "outside", "", "/data/notes"),
                StorageMount("", "root", "", "/storage/emulated/0"),
                StorageMount("", "notes", "", "/storage/emulated/0/A"),
                StorageMount("", "unique", "", "/storage/emulated/0/C"),
            ),
        )

        assertEquals(3, normalized.size)
        assertEquals("/storage/emulated/0/A", normalized[0].path)
        assertEquals("/storage/emulated/0/B", normalized[1].path)
        assertEquals("/storage/emulated/0/C", normalized[2].path)
    }

    @Test
    fun normalizeTrimsAndCapsTextFields() {
        val normalized = normalizeMounts(
            listOf(
                StorageMount(
                    "id",
                    "  ${"n".repeat(40)}  ",
                    "  ${"d".repeat(200)}  ",
                    "/storage/emulated/0/Notes/  ",
                ),
            ),
        )

        assertEquals(32, normalized.single().name.length)
        assertEquals(160, normalized.single().description.length)
        assertEquals("/storage/emulated/0/Notes", normalized.single().path)
    }

    @Test
    fun saveAndReloadRoundTripsMounts() = runTest {
        val file = File(directory, "storage-mounts.json")
        val store = StorageMountStore(file)
        store.save(
            listOf(
                StorageMount("m1", "Obsidian", "我的笔记", "/storage/emulated/0/Obsidian"),
            ),
        )

        val reloaded = StorageMountStore(file).cached()

        assertEquals(1, reloaded.size)
        assertEquals("m1", reloaded.single().id)
        assertEquals("Obsidian", reloaded.single().name)
        assertEquals("我的笔记", reloaded.single().description)
        assertEquals("/storage/emulated/0/Obsidian", reloaded.single().path)
    }

    @Test
    fun removeDropsMount() = runTest {
        val file = File(directory, "storage-mounts.json")
        val store = StorageMountStore(file)
        store.save(
            listOf(
                StorageMount("m1", "A", "", "/storage/emulated/0/A"),
                StorageMount("m2", "B", "", "/storage/emulated/0/B"),
            ),
        )
        store.remove("m1")

        assertEquals(listOf("m2"), store.cached().map { it.id })
    }

    @Test
    fun malformedJsonFallsBackToEmpty() {
        val file = File(directory, "storage-mounts.json")
        file.parentFile?.mkdirs()
        file.writeText("not-json")

        assertTrue(StorageMountStore(file).cached().isEmpty())
    }

    @Test
    fun mountablePathOnlyAcceptsSubdirectoriesOfPublicStorage() {
        assertTrue(isMountablePath("/storage/emulated/0/Obsidian"))
        assertFalse(isMountablePath("/storage/emulated/0"))
        assertFalse(isMountablePath("/storage/emulated/0/../data"))
        assertFalse(isMountablePath("/data/notes"))
    }

    @Test
    fun displayPathStripsPublicStoragePrefix() {
        assertEquals("Obsidian/Notes", mountDisplayPath("/storage/emulated/0/Obsidian/Notes"))
    }
}
