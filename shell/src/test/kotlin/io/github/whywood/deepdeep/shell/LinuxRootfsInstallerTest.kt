package io.github.whywood.deepdeep.shell

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LinuxRootfsInstallerTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun installsOnceAndReportsSize() = runBlocking {
        val rootfs = File(temp.root, "rootfs")
        val installer = installer(rootfs, { ByteArrayInputStream(archive()) })

        val state = installer.install()

        assertTrue(state is LinuxInstallState.Installed)
        assertEquals("3.24.2", (state as LinuxInstallState.Installed).version)
        assertTrue(state.sizeBytes > 0)
        assertEquals("3.24.2\n", File(rootfs, "etc/alpine-release").readText())
        assertTrue(File(rootfs, LinuxRootfsInstaller.MARKER_FILE).isFile)
        assertTrue(File(rootfs, "workspace").isDirectory)
    }

    @Test
    fun secondInstallKeepsExistingRootfs() = runBlocking {
        val rootfs = File(temp.root, "rootfs")
        val installer = installer(rootfs, { ByteArrayInputStream(archive()) })
        installer.install()
        File(rootfs, "keep.txt").writeText("keep")

        installer.install()

        assertEquals("keep", File(rootfs, "keep.txt").readText())
    }

    @Test
    fun resetRemovesRootfsButKeepsWorkspace() = runBlocking {
        val rootfs = File(temp.root, "rootfs")
        val workspace = temp.newFolder("workspace")
        File(workspace, "probe.txt").writeText("hi")
        val installer = installer(rootfs, { ByteArrayInputStream(archive()) })
        installer.install()

        val state = installer.reset()

        assertEquals(LinuxInstallState.NotInstalled, state)
        assertFalse(rootfs.exists())
        assertEquals("hi", File(workspace, "probe.txt").readText())
    }

    @Test
    fun failedInstallCleansTempAndCanRetry() = runBlocking {
        val rootfs = File(temp.root, "rootfs")
        var bytes = "not a tarball".toByteArray()
        val installer = installer(rootfs, { ByteArrayInputStream(bytes) })

        assertThrows(Exception::class.java) {
            runBlocking { installer.install() }
        }
        assertFalse(rootfs.exists())
        assertTrue(File(temp.root, "rootfs").parentFile.listFiles().orEmpty().none {
            it.name.startsWith("rootfs.tmp-")
        })

        bytes = archive()
        val state = installer.install()

        assertTrue(state is LinuxInstallState.Installed)
        assertEquals("3.24.2\n", File(rootfs, "etc/alpine-release").readText())
    }

    @Test
    fun rejectsSha256Mismatch() = runBlocking {
        val rootfs = File(temp.root, "rootfs")
        val installer = LinuxRootfsInstaller(
            rootfsDir = rootfs,
            assetOpener = { ByteArrayInputStream(archive()) },
            expectedSha256 = "00",
        )

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { installer.install() }
        }
        assertFalse(rootfs.exists())
    }

    @Test
    fun reportsInstalledStateWithoutReextracting() = runBlocking {
        val rootfs = File(temp.root, "rootfs")
        val installer = installer(rootfs, { ByteArrayInputStream(archive()) })
        installer.install()
        val markerModified = File(rootfs, "extra.txt").apply { writeText("x") }.lastModified()

        val refreshed = installer.refreshState()

        assertTrue(refreshed is LinuxInstallState.Installed)
        assertTrue(Files.exists(rootfs.toPath().resolve("extra.txt")))
        assertEquals(markerModified, File(rootfs, "extra.txt").lastModified())
    }

    private fun installer(rootfs: File, opener: () -> ByteArrayInputStream) = LinuxRootfsInstaller(
        rootfsDir = rootfs,
        assetOpener = opener,
    )

    private fun archive(): ByteArray = TarFixtures.archive(
        listOf(
            TarFixtures.Entry.Directory("./etc/"),
            TarFixtures.Entry.File("./etc/alpine-release", "3.24.2\n".toByteArray()),
            TarFixtures.Entry.Directory("./bin/"),
            TarFixtures.Entry.File("./bin/busybox", "busybox".toByteArray(), mode = 0x1ED),
            TarFixtures.Entry.Symlink("./bin/sh", "busybox"),
        ),
    )
}
