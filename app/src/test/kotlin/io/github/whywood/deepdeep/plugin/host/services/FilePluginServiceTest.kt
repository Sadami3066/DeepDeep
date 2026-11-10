package io.github.whywood.deepdeep.plugin.host.services

import java.io.File
import okio.FileSystem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FilePluginServiceTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun readsWritesListsAndDeletes() = runTest {
        val root = temporary.newFolder("data")
        val workspace = temporary.newFolder("workspace")
        val service = FilePluginService(root = root, workspace = workspace, sharedStorage = null, files = { FileSystem.SYSTEM })

        assertEquals(root.absolutePath, service.rootDir)
        assertEquals(workspace.absolutePath, service.workspaceDir)

        service.writeText("notes/a.txt", "hello")
        assertEquals("hello", service.readText("notes/a.txt"))

        service.writeText("notes/a.txt", " world", append = true)
        assertEquals("hello world", service.readText("notes/a.txt"))

        val entries = service.list("notes")
        assertEquals(listOf("a.txt"), entries.map { it.name })
        assertEquals("notes/a.txt", entries.single().path)
        assertFalse(entries.single().directory)
        assertTrue(entries.single().size > 0)
        assertTrue(service.list("missing").isEmpty())

        assertTrue(service.exists("notes/a.txt"))
        assertTrue(service.delete("notes/a.txt"))
        assertFalse(service.exists("notes/a.txt"))
    }

    @Test
    fun handlesBytesAndAbsolutePaths() = runTest {
        val root = temporary.newFolder("data")
        val service = FilePluginService(root = root, workspace = null, sharedStorage = null, files = { FileSystem.SYSTEM })

        service.writeBytes("bin/x.bin", byteArrayOf(1, 2, 3))
        assertArrayEquals(byteArrayOf(1, 2, 3), service.readBytes("bin/x.bin"))
        assertEquals(File(root, "bin/x.bin").absolutePath, service.resolve("bin/x.bin"))

        val outside = File(root, "abs.txt")
        outside.writeText("absolute")
        assertEquals("absolute", service.readText(outside.absolutePath))
        assertNotNull(service.resolve(outside.absolutePath))
    }
}
