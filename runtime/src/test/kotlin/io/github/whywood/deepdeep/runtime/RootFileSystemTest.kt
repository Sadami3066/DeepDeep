package io.github.whywood.deepdeep.runtime

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okio.Buffer
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RootFileSystemTest {
    private val fs = RootFileSystem(RootFileRunner { command -> ProcessBuilder("sh", "-c", command).start() })

    @Test
    fun readsWritesListsAndMoves() {
        val dir = tempDir()
        try {
            fs.createDirectories(dir)
            assertTrue(fs.metadata(dir).isDirectory)

            val file = dir / "hello.txt"
            fs.write(file) { writeUtf8("hi root") }
            assertEquals("hi root", fs.read(file) { readUtf8() })
            assertEquals(7L, fs.metadata(file).size)
            assertEquals(listOf(file), fs.list(dir))

            val moved = dir / "moved.txt"
            fs.atomicMove(file, moved)
            assertFalse(fs.exists(file))
            assertTrue(fs.exists(moved))

            fs.delete(moved, mustExist = true)
            assertFalse(fs.exists(moved))
        } finally {
            cleanup(dir)
        }
    }

    @Test
    fun readsStreamsWithoutDeadlockWhenStderrIsReadFirst() {
        val stderrRead = CountDownLatch(1)
        val process = FakeProcess(
            stdout = StderrDependentStream(stderrRead, "regular file|5\n".toByteArray()),
            stderr = SignalingStream(stderrRead),
            exitCode = 0,
        )
        val metadata = RootFileSystem(RootFileRunner { process }).metadataOrNull("/tmp/rootfs-deadlock".toPath())
        assertNotNull(metadata)
        assertEquals(5L, metadata!!.size)
    }

    @Test
    fun deletesDirectoriesAndHonorsMustExist() {
        val dir = tempDir()
        try {
            val nested = dir / "a" / "b"
            fs.createDirectories(nested)
            fs.write(nested / "file.txt") { writeUtf8("x") }
            fs.delete(dir, mustExist = true)
            assertFalse(fs.exists(dir))

            fs.delete(dir / "missing", mustExist = false)
            assertIoException { fs.delete(dir / "missing", mustExist = true) }
        } finally {
            cleanup(dir)
        }
    }

    @Test
    fun sinkHonorsMustCreate() {
        val dir = tempDir()
        try {
            fs.createDirectories(dir)
            val existing = dir / "existing.txt"
            fs.write(existing) { writeUtf8("one") }
            assertIoException {
                fs.sink(existing, mustCreate = true).use { it.write(Buffer().writeUtf8("two"), 3) }
            }
            assertEquals("one", fs.read(existing) { readUtf8() })

            val fresh = dir / "fresh.txt"
            fs.sink(fresh, mustCreate = true).use { it.write(Buffer().writeUtf8("new"), 3) }
            assertEquals("new", fs.read(fresh) { readUtf8() })
        } finally {
            cleanup(dir)
        }
    }

    @Test
    fun createDirectoriesAndListOrNullUseRootRunner() {
        val commands = mutableListOf<String>()
        val rootFs = RootFileSystem(RootFileRunner { command ->
            commands += command
            ProcessBuilder("sh", "-c", command).start()
        })
        val dir = tempDir()
        try {
            val nested = dir / "x" / "y"
            rootFs.createDirectories(nested)
            assertTrue(rootFs.metadata(nested).isDirectory)
            assertTrue(commands.any { it.startsWith("mkdir -p -- ") })
            assertTrue(commands.any { it.startsWith("stat -c ") })

            assertNull(rootFs.listOrNull(dir / "missing"))
            assertTrue(rootFs.listOrNull(nested)!!.isEmpty())
            assertTrue(commands.any { it.startsWith("ls -1A -- ") })
        } finally {
            cleanup(dir)
        }
    }

    @Test
    fun listsRecursivelyThroughRootRunner() {
        val commands = mutableListOf<String>()
        val rootFs = RootFileSystem(RootFileRunner { command ->
            commands += command
            ProcessBuilder("sh", "-c", command).start()
        })
        val dir = tempDir()
        try {
            val nested = dir / "a" / "b"
            rootFs.createDirectories(nested)
            rootFs.write(nested / "file.txt") { writeUtf8("x") }
            rootFs.write(dir / "top.txt") { writeUtf8("y") }

            val expected = setOf(dir / "top.txt", dir / "a", nested, nested / "file.txt")
            assertEquals(expected.map { it.toString() }.toSet(), rootFs.listRecursively(dir).map { it.toString() }.toSet())
            assertFalse(rootFs.listRecursively(dir).map { it.toString() }.contains(dir.toString()))
            assertTrue(commands.any { it.startsWith("ls -1A -- ") })

            val walked = rootFs.listRecursively(dir, followSymlinks = true).map { it.toString() }.toSet()
            assertTrue(walked.containsAll(expected.map { it.toString() }))
            assertFalse(walked.contains(dir.toString()))
            assertTrue(commands.any { it.startsWith("find -L ") })
        } finally {
            cleanup(dir)
        }
    }

    @Test
    fun reportsSymlinksWithoutFollowingThem() {
        val dir = tempDir()
        try {
            fs.createDirectories(dir)
            val target = dir / "target.txt"
            fs.write(target) { writeUtf8("data") }
            val link = dir / "link"
            assertTrue(ProcessBuilder("ln", "-s", target.toString(), link.toString()).start().waitFor() == 0)

            val metadata = fs.metadata(link)
            assertFalse(metadata.isDirectory)
            assertFalse(metadata.isRegularFile)
            assertEquals(target.toString(), metadata.symlinkTarget.toString())
            assertNull(metadata.size)
            assertTrue(fs.exists(link))
        } finally {
            cleanup(dir)
        }
    }

    @Test
    fun chownsWrittenFilesWhenAppIdsAreProvided() {
        val commands = mutableListOf<String>()
        val rootFs = RootFileSystem(
            runner = RootFileRunner { command ->
                commands += command
                FakeProcess(ByteArrayInputStream(ByteArray(0)), ByteArrayInputStream(ByteArray(0)), 0)
            },
            appUid = 10123,
            appGid = 10123,
        )
        val file = "/data/data/io.github.whywood.deepdeep/files/owned.txt".toPath()
        rootFs.write(file) { writeUtf8("x") }
        assertTrue(commands.contains("chown 10123:10123 -- '$file'"))
    }

    @Test
    fun createDirectoryChownsNewAppOwnedDirectoryTree() {
        val commands = mutableListOf<String>()
        val rootFs = RootFileSystem(
            runner = RootFileRunner { command ->
                commands += command
                FakeProcess(ByteArrayInputStream(ByteArray(0)), ByteArrayInputStream(ByteArray(0)), 0)
            },
            appUid = 10123,
            appGid = 10123,
        )
        rootFs.createDirectories("/data/data/io.github.whywood.deepdeep/files/owned/nested".toPath())
        assertTrue(commands.joinToString("\n"), commands.any { it.contains("chown -R 10123:10123") })
    }

    @Test
    fun chownsWithSeparateUidAndGid() {
        val commands = mutableListOf<String>()
        val rootFs = RootFileSystem(
            runner = RootFileRunner { command ->
                commands += command
                FakeProcess(ByteArrayInputStream(ByteArray(0)), ByteArrayInputStream(ByteArray(0)), 0)
            },
            appUid = 10123,
        )
        rootFs.write("/data/data/io.github.whywood.deepdeep/files/uid-only.txt".toPath()) { writeUtf8("x") }
        assertTrue(commands.any { it.startsWith("chown 10123 -- ") })
    }

    @Test
    fun listRecursivelyExcludesRootDirectory() {
        val dir = tempDir()
        try {
            val nested = dir / "a" / "b"
            fs.createDirectories(nested)
            fs.write(nested / "file.txt") { writeUtf8("x") }

            val walked = fs.listRecursively(dir).map { it.toString() }.toList()
            assertFalse(walked.contains(dir.toString()))
            assertTrue(walked.contains((dir / "a").toString()))
            assertTrue(walked.contains(nested.toString()))
            assertTrue(walked.contains((nested / "file.txt").toString()))

            val followed = fs.listRecursively(dir, followSymlinks = true).map { it.toString() }.toList()
            assertFalse(followed.contains(dir.toString()))
            assertTrue(followed.contains((nested / "file.txt").toString()))
        } finally {
            cleanup(dir)
        }
    }

    @Test
    fun chownsOnlyPathsInsideAppPrivateDirectories() {
        val commands = mutableListOf<String>()
        val rootFs = RootFileSystem(
            runner = RootFileRunner { command ->
                commands += command
                FakeProcess(ByteArrayInputStream(ByteArray(0)), ByteArrayInputStream(ByteArray(0)), 0)
            },
            appUid = 10123,
            appGid = 10123,
        )
        rootFs.sink("/sdcard/Download/pic.txt".toPath()).use { it.write(Buffer().writeUtf8("x"), 1) }
        assertFalse(commands.any { it.startsWith("chown") })

        rootFs.sink("/data/data/io.github.whywood.deepdeep/files/doc.txt".toPath()).use {
            it.write(Buffer().writeUtf8("x"), 1)
        }
        assertTrue(commands.contains("chown 10123:10123 -- '/data/data/io.github.whywood.deepdeep/files/doc.txt'"))
    }

    @Test
    fun createDirectoryChownsOnlyPathsInsideAppPrivateDirectories() {
        val commands = mutableListOf<String>()
        val rootFs = RootFileSystem(
            runner = RootFileRunner { command ->
                commands += command
                FakeProcess(ByteArrayInputStream(ByteArray(0)), ByteArrayInputStream(ByteArray(0)), 0)
            },
            appUid = 10123,
            appGid = 10123,
        )
        rootFs.createDirectory("/sdcard/Download/newdir".toPath())
        val publicCommand = commands.first { it.startsWith("mkdir -p -- ") }
        assertFalse(publicCommand.contains("chown"))

        rootFs.createDirectory("/data/data/io.github.whywood.deepdeep/files/newdir".toPath())
        val privateCommand = commands.last { it.startsWith("t=") }
        assertTrue(privateCommand.contains("chown -R 10123:10123"))
    }

    @Test
    fun appendingSinkAppendsAndHonorsMustExist() {
        val dir = tempDir()
        try {
            fs.createDirectories(dir)
            val log = dir / "log.txt"
            fs.write(log) { writeUtf8("one\n") }
            fs.appendingSink(log).use { it.write(Buffer().writeUtf8("two\n"), 4) }
            assertEquals("one\ntwo\n", fs.read(log) { readUtf8() })

            assertIoException { fs.appendingSink(dir / "missing.txt", mustExist = true).use { } }
            fs.appendingSink(dir / "fresh.txt").use { it.write(Buffer().writeUtf8("x"), 1) }
            assertEquals("x", fs.read(dir / "fresh.txt") { readUtf8() })
        } finally {
            cleanup(dir)
        }
    }

    @Test
    fun copiesAndCreatesSymlinksThroughRootRunner() {
        val dir = tempDir()
        try {
            fs.createDirectories(dir)
            val source = dir / "source.txt"
            fs.write(source) { writeUtf8("data") }
            val target = dir / "target.txt"
            fs.copy(source, target)
            assertEquals("data", fs.read(target) { readUtf8() })
            assertIoException { fs.copy(source, target) }
            assertIoException { fs.copy(dir / "missing.txt", dir / "other.txt") }

            val link = dir / "link"
            fs.createSymlink(link, source)
            assertEquals(source.toString(), fs.metadata(link).symlinkTarget.toString())
            assertIoException { fs.createSymlink(link, source) }
        } finally {
            cleanup(dir)
        }
    }

    @Test
    fun canonicalizeResolvesPathsThroughRootRunner() {
        val dir = tempDir()
        try {
            fs.createDirectories(dir)
            val file = dir / "file.txt"
            fs.write(file) { writeUtf8("x") }
            val link = dir / "link"
            fs.createSymlink(link, file)

            assertEquals(File(file.toString()).canonicalPath, fs.canonicalize(link).toString())
        } finally {
            cleanup(dir)
        }
    }

    @Test
    fun randomAccessFileHandlesAreUnsupported() {
        val path = "/data/data/io.github.whywood.deepdeep/files/x".toPath()
        assertUnsupported { fs.openReadOnly(path) }
        assertUnsupported { fs.openReadWrite(path) }
    }

    private fun tempDir(): Path {
        val file = File.createTempFile("rootfs-test-", "")
        file.delete()
        return file.toOkioPath()
    }

    private fun cleanup(path: Path) {
        ProcessBuilder("rm", "-rf", path.toString()).start().waitFor()
    }

    private fun assertIoException(block: () -> Unit) {
        try {
            block()
            fail("expected IOException")
        } catch (_: IOException) {
        }
    }

    private fun assertUnsupported(block: () -> Unit) {
        try {
            block()
            fail("expected UnsupportedOperationException")
        } catch (_: UnsupportedOperationException) {
        }
    }
}

private class FakeProcess(
    private val stdout: InputStream,
    private val stderr: InputStream,
    private val exitCode: Int,
) : Process() {
    override fun getOutputStream(): OutputStream = ByteArrayOutputStream()

    override fun getInputStream(): InputStream = stdout

    override fun getErrorStream(): InputStream = stderr

    override fun waitFor(): Int = exitCode

    override fun exitValue(): Int = exitCode

    override fun destroy() = Unit
}

private class StderrDependentStream(
    private val latch: CountDownLatch,
    private val data: ByteArray,
) : InputStream() {
    private var index = 0

    override fun read(): Int {
        if (index == 0 && data.isNotEmpty() && !latch.await(5, TimeUnit.SECONDS)) {
            throw IOException("stdout was read before stderr")
        }
        return if (index < data.size) data[index++].toInt() and 0xff else -1
    }
}

private class SignalingStream(private val latch: CountDownLatch) : InputStream() {
    override fun read(): Int {
        latch.countDown()
        return -1
    }
}
