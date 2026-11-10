package io.github.whywood.deepdeep.runtime

import java.io.File
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootFileSystemTest {
    private val fs = RootFileSystem(RootFileRunner { command -> ProcessBuilder("sh", "-c", command).start() })

    @Test
    fun readsWritesListsAndMoves() {
        val dir = File.createTempFile("rootfs-test-", "").also { it.delete() }.toOkioPath()
        try {
            fs.createDirectories(dir)
            assertTrue(fs.metadata(dir)!!.isDirectory)

            val file = dir / "hello.txt"
            fs.write(file) { writeUtf8("hi root") }
            assertEquals("hi root", fs.read(file) { readUtf8() })
            assertEquals(7L, fs.metadata(file)!!.size)
            assertEquals(listOf(file), fs.list(dir))

            val moved = dir / "moved.txt"
            fs.atomicMove(file, moved)
            assertFalse(fs.exists(file))
            assertTrue(fs.exists(moved))

            fs.delete(moved, mustExist = true)
            assertFalse(fs.exists(moved))
        } finally {
            ProcessBuilder("rm", "-rf", dir.toString()).start().waitFor()
        }
    }
}
