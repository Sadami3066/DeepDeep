package io.github.whywood.deepdeep.shell

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootfsBootstrapTest {
    @Test
    fun bootstrapScriptChecksBusyboxAndMarksReady() {
        val script = RootfsBootstrap.buildBootstrapScript(
            appRootfs = File("/data/user/0/app/files/linux/rootfs"),
            linuxDir = File("/data/adb/deepdeep/linux"),
            runScript = File("/data/user/0/app/cache/root-bash/run.sh"),
            profileFile = File("/data/user/0/app/cache/root-bash/profile.sh"),
        )

        assertTrue(script.contains("/data/adb/ap/bin/busybox"))
        assertTrue(script.contains("/data/adb/magisk/busybox"))
        assertTrue(script.contains("DEEPDEEP_NO_BUSYBOX"))
        assertTrue(script.contains("DEEPDEEP_READY"))
        assertTrue(script.contains(".deepdeep-ready"))
        assertTrue(script.contains("cp -a"))
        assertTrue(script.contains("ln -s /storage/emulated/0"))
        assertTrue(script.contains("\$TMP/data"))
        assertTrue(script.contains("\$TMP/vendor"))
        assertTrue(script.contains(".deepdeep-empty"))
        assertTrue(script.contains("cp \"\$RUN_SCRIPT\" \"\$LINUX/run.sh\""))
    }

    @Test
    fun runScriptPrivatizesMountsAndBindsAndroidData() {
        val script = RootfsBootstrap.buildRunScript(File("/data/adb/deepdeep/linux"))

        assertTrue(script.contains("unshare -m"))
        assertTrue(script.contains("mount --make-rprivate /"))
        assertTrue(script.contains("mount -t proc proc"))
        assertTrue(script.contains("mount --rbind /data"))
        assertTrue(script.contains("mount --bind \"\$ROOT/.deepdeep-empty\" \"\$ROOT/data/adb/deepdeep\""))
        assertTrue(script.contains("ln -s /storage/emulated/0"))
        assertTrue(script.contains("mount --bind /storage/emulated/0 \"\$ROOT/storage/emulated/0\""))
        assertFalse(script.contains("/android"))
        assertFalse(script.contains("android/data/user/0"))
        assertTrue(script.contains("getprop persist.sys.timezone"))
        assertTrue(script.contains("for d in system vendor product system_ext odm"))
        assertTrue(script.contains("chroot"))
        assertTrue(script.contains("/bin/bash -ls"))
        assertTrue(script.contains("DEEPDEEP_CWD=\"\$1\""))
        assertTrue(script.contains("mkdir -p \"\$ROOT\$1\""))
        assertTrue(script.contains("mount --rbind \"\$1\" \"\$ROOT\$1\""))
        assertFalse(script.contains("/work"))
    }

    @Test
    fun profileSetsPathAndWorkingDirectory() {
        val script = RootfsBootstrap.buildProfileScript()

        assertTrue(script.contains("export PATH="))
        assertTrue(script.contains("export HOME=/root"))
        assertFalse(script.contains("export TZ=UTC"))
        assertTrue(script.contains("export TZ"))
        assertTrue(script.contains("cd -- \"\${DEEPDEEP_CWD:-/}\""))
    }

    @Test
    fun ensureReadyPersistsReadyStatus() = runBlocking {
        val dir = kotlin.io.path.createTempDirectory("rootfs-bootstrap").toFile()
        try {
            val appRootfs = File(dir, "app-rootfs")
            File(appRootfs, "bin").mkdirs()
            File(appRootfs, "bin/bash").writeText("")
            val statusFile = File(dir, "state")
            val bootstrap = RootfsBootstrap(
                appRootfsDir = appRootfs,
                statusFile = statusFile,
                scratchDir = File(dir, "scratch"),
                linuxDir = File(dir, "linux"),
                runner = RootCommandRunner { command, _, _ ->
                    assertTrue(command.startsWith("sh "))
                    RootProcessResult(0, "DEEPDEEP_READY", "", false, true)
                },
                ioDispatcher = Dispatchers.Unconfined,
            )

            val status = bootstrap.ensureReady()

            assertEquals(RootfsState.Ready, status.state)
            assertEquals("ready", statusFile.readText())
            assertTrue(File(dir, "scratch").isDirectory)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun ensureReadyReportsMissingBusybox() = runBlocking {
        val dir = kotlin.io.path.createTempDirectory("rootfs-bootstrap-fail").toFile()
        try {
            val appRootfs = File(dir, "app-rootfs")
            File(appRootfs, "bin").mkdirs()
            File(appRootfs, "bin/bash").writeText("")
            val bootstrap = RootfsBootstrap(
                appRootfsDir = appRootfs,
                statusFile = File(dir, "state"),
                scratchDir = File(dir, "scratch"),
                linuxDir = File(dir, "linux"),
                runner = RootCommandRunner { _, _, _ ->
                    RootProcessResult(0, "DEEPDEEP_NO_BUSYBOX", "", false, true)
                },
                ioDispatcher = Dispatchers.Unconfined,
            )

            val status = bootstrap.ensureReady()

            assertEquals(RootfsState.Failed("busybox not found"), status.state)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun rebuildClearsRootfsThenPrepares() = runBlocking {
        val dir = kotlin.io.path.createTempDirectory("rootfs-rebuild").toFile()
        try {
            val appRootfs = File(dir, "app-rootfs")
            File(appRootfs, "bin").mkdirs()
            File(appRootfs, "bin/bash").writeText("")
            val commands = mutableListOf<String>()
            val statusFile = File(dir, "state")
            val bootstrap = RootfsBootstrap(
                appRootfsDir = appRootfs,
                statusFile = statusFile,
                scratchDir = File(dir, "scratch"),
                linuxDir = File(dir, "linux"),
                runner = RootCommandRunner { command, _, _ ->
                    commands += command
                    if (command.startsWith("rm -rf ")) {
                        RootProcessResult(0, "", "", false, true)
                    } else {
                        RootProcessResult(0, "DEEPDEEP_READY", "", false, true)
                    }
                },
                ioDispatcher = Dispatchers.Unconfined,
            )

            val status = bootstrap.rebuild()

            assertEquals(RootfsState.Ready, status.state)
            assertEquals(2, commands.size)
            assertTrue(commands[0].startsWith("rm -rf "))
            assertTrue(commands[0].contains("rootfs"))
            assertTrue(commands[1].startsWith("sh "))
        } finally {
            dir.deleteRecursively()
        }
    }
}
