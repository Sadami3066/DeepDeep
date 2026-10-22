package io.github.whywood.deepdeep.shell

import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RootfsExtractorTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun extractsFilesDirectoriesAndRewritesAbsoluteSymlinks() {
        val root = temp.newFolder("root").toPath()
        val archive = TarFixtures.archive(
            listOf(
                TarFixtures.Entry.Directory("./bin/"),
                TarFixtures.Entry.File("./bin/busybox", "busybox".toByteArray(), mode = 0x1ED),
                TarFixtures.Entry.Directory("./usr/"),
                TarFixtures.Entry.Directory("./usr/bin/"),
                TarFixtures.Entry.Symlink("./usr/bin/yes", "/bin/busybox"),
            ),
        )

        extract(archive, root)

        assertEquals("busybox", Files.readAllBytes(root.resolve("bin/busybox")).toString(Charsets.UTF_8))
        assertTrue(Files.isExecutable(root.resolve("bin/busybox")))
        val link = root.resolve("usr/bin/yes")
        assertTrue(Files.isSymbolicLink(link))
        val target = Files.readSymbolicLink(link)
        assertFalse(target.isAbsolute)
        assertEquals(root.resolve("bin/busybox").normalize(), link.parent.resolve(target).normalize())
    }

    private fun extract(archive: ByteArray, root: java.nio.file.Path) {
        GZIPInputStream(ByteArrayInputStream(archive)).use { RootfsExtractor().extract(it, root) }
    }

    @Test
    fun rejectsTraversalEntry() {
        val root = temp.newFolder("root").toPath()
        val archive = TarFixtures.archive(listOf(TarFixtures.Entry.File("../evil", "x".toByteArray())))

        assertThrows(IllegalArgumentException::class.java) {
            extract(archive, root)
        }
        assertFalse(Files.exists(root.parent.resolve("evil"), LinkOption.NOFOLLOW_LINKS))
    }

    @Test
    fun rejectsAbsoluteEntryName() {
        assertThrows(IllegalArgumentException::class.java) {
            RootfsExtractor().safeRelative("/evil")
        }
    }

    @Test
    fun rejectsEscapingSymlink() {
        val root = temp.newFolder("root").toPath()
        val archive = TarFixtures.archive(
            listOf(
                TarFixtures.Entry.Directory("./usr/"),
                TarFixtures.Entry.Directory("./usr/bin/"),
                TarFixtures.Entry.Symlink("./usr/bin/evil", "../../../outside"),
            ),
        )

        assertThrows(IllegalArgumentException::class.java) {
            extract(archive, root)
        }
    }

    @Test
    fun rejectsSymlinkInParentPath() {
        val root = temp.newFolder("root").toPath()
        val archive = TarFixtures.archive(
            listOf(
                TarFixtures.Entry.Directory("./sub/"),
                TarFixtures.Entry.Symlink("./link", "sub"),
                TarFixtures.Entry.File("./link/evil", "x".toByteArray()),
            ),
        )

        assertThrows(IllegalArgumentException::class.java) {
            extract(archive, root)
        }
        assertFalse(Files.exists(root.resolve("sub/evil"), LinkOption.NOFOLLOW_LINKS))
    }

    @Test
    fun rejectsHardlinkOutsideRoot() {
        val root = temp.newFolder("root").toPath()
        val archive = TarFixtures.archive(
            listOf(TarFixtures.Entry.Hardlink("./evil", "../../outside")),
        )

        assertThrows(IllegalArgumentException::class.java) {
            extract(archive, root)
        }
    }
}
