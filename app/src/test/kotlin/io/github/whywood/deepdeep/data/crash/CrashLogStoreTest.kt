package io.github.whywood.deepdeep.data.crash

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashLogStoreTest {
    private val info = CrashInfo(
        versionName = "1.0.5",
        versionCode = 10005,
        device = "Test Device",
        androidSdk = 36,
    )

    private fun store(maxEntries: Int = 5): Pair<CrashLogStore, File> {
        val directory = Files.createTempDirectory("crash-store").toFile()
        return CrashLogStore(directory, maxEntries) to directory
    }

    @Test
    fun recordsCrashWithHeaderAndStack() {
        val (store, _) = store()

        val entry = store.record(Thread.currentThread(), IllegalStateException("boom"), info)

        assertTrue(entry != null)
        assertEquals(entry, store.latest())
        val text = store.read(entry!!)
        assertTrue(text.contains("version: 1.0.5 (10005)"))
        assertTrue(text.contains("device: Test Device"))
        assertTrue(text.contains("java.lang.IllegalStateException: boom"))
        assertTrue(text.contains("CrashLogStoreTest"))
    }

    @Test
    fun keepsNewestEntriesOnly() {
        val (store, directory) = store(maxEntries = 2)
        repeat(4) { index ->
            store.record(Thread.currentThread(), IllegalStateException("boom-$index"), info)
            Thread.sleep(2)
        }

        val entries = store.entries()
        assertEquals(2, entries.size)
        assertEquals(2, directory.listFiles()?.count { it.isFile })
        assertTrue(store.read(entries.first()).contains("boom-3"))
    }

    @Test
    fun clearsAllEntries() {
        val (store, _) = store()
        store.record(Thread.currentThread(), IllegalStateException("boom"), info)

        store.clear()

        assertNull(store.latest())
        assertTrue(store.entries().isEmpty())
    }

    @Test
    fun missingDirectoryYieldsNoEntries() {
        val directory = File(Files.createTempDirectory("crash-store").toFile(), "missing")
        val store = CrashLogStore(directory)

        assertNull(store.latest())
        assertTrue(store.entries().isEmpty())
    }
}
