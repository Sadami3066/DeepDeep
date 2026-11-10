package io.github.whywood.deepdeep.data

import io.github.whywood.deepdeep.runtime.host.HostAccess
import io.github.whywood.deepdeep.runtime.host.HostCapabilities
import io.github.whywood.deepdeep.runtime.host.HostEnvironment
import io.github.whywood.deepdeep.runtime.host.HostEnvironmentProvider
import io.github.whywood.deepdeep.sdk.coding.tools.ShellExecutor
import java.io.ByteArrayInputStream
import java.io.File
import okio.FileSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HostFilesTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun listsAndClassifiesEntriesThroughHostEnvironment() {
        val directory = temporaryFolder.newFolder("browse")
        File(directory, "child").mkdirs()
        File(directory, "note.txt").writeText("hello")

        val hostFiles = hostFiles(temporaryFolder.newFolder("staging"))

        val children = hostFiles.list(directory)
        assertNotNull(children)
        assertEquals(setOf("child", "note.txt"), children!!.map { it.name }.toSet())
        assertTrue(hostFiles.isDirectory(File(directory, "child")))
        assertTrue(hostFiles.isFile(File(directory, "note.txt")))
        assertEquals(5L, hostFiles.length(File(directory, "note.txt")))
    }

    @Test
    fun readTextMarksTruncatedContent() {
        val file = temporaryFolder.newFile("long.txt").apply { writeText("abcdefghij") }
        val hostFiles = hostFiles(temporaryFolder.newFolder("staging"))

        val content = hostFiles.readText(file, limitBytes = 4)

        assertEquals("abcd", content!!.text)
        assertTrue(content.truncated)
    }

    @Test
    fun writeStreamCreatesMissingParentsAndEnforcesLimit() {
        val staging = temporaryFolder.newFolder("staging")
        val hostFiles = hostFiles(staging)
        val target = File(temporaryFolder.root, "nested/dir/file.bin")

        assertTrue(hostFiles.write(target, ByteArrayInputStream(ByteArray(8)), limitBytes = 16))
        assertEquals(8L, target.length())
        assertFalse(hostFiles.write(target, ByteArrayInputStream(ByteArray(32)), limitBytes = 16))
    }

    @Test
    fun stagesFileIntoStagingRootAndDeletes() {
        val staging = temporaryFolder.newFolder("staging")
        val hostFiles = hostFiles(staging)
        val source = temporaryFolder.newFile("image.png").apply { writeBytes(ByteArray(16) { it.toByte() }) }

        val staged = hostFiles.stage(source, "image.png")

        assertNotNull(staged)
        assertTrue(staged!!.isFile)
        assertEquals(16L, staged.length())
        assertTrue(staged.absolutePath.startsWith(staging.absolutePath))
        assertTrue(hostFiles.delete(staged))
        assertFalse(staged.exists())
    }

    @Test
    fun unavailableEnvironmentYieldsNullFileSystemAccess() {
        val hostFiles = HostFiles(
            hostEnvironment = {
                object : HostEnvironmentProvider {
                    override fun current(): HostEnvironment = UnavailableHostEnvironment
                }
            },
            stagingRoot = temporaryFolder.newFolder("staging"),
        )

        assertFalse(hostFiles.available())
        assertNull(hostFiles.list(temporaryFolder.root))
        assertEquals(HostCapabilities(), hostFiles.capabilities())
    }

    private fun hostFiles(staging: File): HostFiles = HostFiles(
        hostEnvironment = {
            object : HostEnvironmentProvider {
                override fun current(): HostEnvironment = FakeHostEnvironment
            }
        },
        stagingRoot = staging,
    )

    private object FakeHostEnvironment : HostEnvironment {
        override val access: HostAccess = HostAccess.LOCAL
        override val capabilities: HostCapabilities = HostCapabilities()
        override val sharedStorageEnabled: Boolean = false
        override val files: FileSystem = FileSystem.SYSTEM
        override val system: ShellExecutor? = null
        override val promptLines: List<String> = emptyList()

        override fun bash(cwd: String): ShellExecutor = error("unused")
    }

    private object UnavailableHostEnvironment : HostEnvironment {
        override val access: HostAccess = HostAccess.LOCAL
        override val capabilities: HostCapabilities = HostCapabilities()
        override val sharedStorageEnabled: Boolean = false
        override val files: FileSystem
            get() = error("unavailable")
        override val system: ShellExecutor? = null
        override val promptLines: List<String> = emptyList()

        override fun bash(cwd: String): ShellExecutor = error("unused")
    }
}
