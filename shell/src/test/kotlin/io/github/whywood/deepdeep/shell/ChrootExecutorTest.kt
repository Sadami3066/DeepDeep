package io.github.whywood.deepdeep.shell

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChrootExecutorTest {
    @Test
    fun buildCommandQuotesEveryArgument() {
        val command = ChrootExecutor.buildCommand(
            script = File("/data/adb/deepdeep/linux/run.sh"),
            workspaceDir = File("/data/user/0/app/files/workspace with space"),
            sharedStorage = true,
            resolvConf = File("/data/user/0/app/cache/root-bash/resolv.conf"),
        )

        assertEquals(
            "'/data/adb/deepdeep/linux/run.sh' '/data/data/app/files/workspace with space' 1 '/data/data/app/cache/root-bash/resolv.conf'",
            command,
        )
    }

    @Test
    fun buildCommandUsesEmptyResolvWhenMissing() {
        val command = ChrootExecutor.buildCommand(
            script = File("/tmp/run.sh"),
            workspaceDir = File("/tmp/work"),
            sharedStorage = false,
            resolvConf = null,
        )

        assertEquals("'/tmp/run.sh' '/tmp/work' 0 ''", command)
    }

    @Test
    fun suPathRewritesAppViewToDataView() {
        assertEquals("/data/data/app/files/home", ChrootExecutor.suPath("/data/user/0/app/files/home"))
        assertEquals("/data/data", ChrootExecutor.suPath("/data/data"))
        assertEquals("/storage/emulated/0", ChrootExecutor.suPath("/storage/emulated/0"))
    }

    @Test
    fun shellQuoteEscapesSingleQuotes() {
        assertEquals("'a'\\''b'", ChrootExecutor.shellQuote("a'b"))
    }

    @Test
    fun mergeOutputCombinesStreams() {
        assertEquals("out\nerr", ChrootExecutor.mergeOutput("out\n", "err"))
        assertEquals("err", ChrootExecutor.mergeOutput("", "err"))
        assertEquals("out", ChrootExecutor.mergeOutput("out", ""))
    }

    @Test
    fun executePassesCommandOnStdinAndMergesOutput() = runBlocking {
        val dir = kotlin.io.path.createTempDirectory("chroot-test").toFile()
        try {
            var seenStdin: String? = null
            var seenCommand: String? = null
            val executor = ChrootExecutor(
                linuxDir = File(dir, "linux"),
                workspaceDir = File(dir, "workspace"),
                logDir = File(dir, "logs"),
                sharedStorageEnabled = { true },
                resolvConf = { null },
                maxOutputBytes = 1024,
                runner = RootCommandRunner { command, _, stdin ->
                    seenCommand = command
                    seenStdin = stdin
                    RootProcessResult(0, "hello\n", "", false, true)
                },
            )

            val result = executor.execute("echo hello", 5_000)

            assertEquals("echo hello", seenStdin)
            assertTrue(seenCommand.orEmpty().contains("'${File(dir, "linux").absolutePath}/run.sh'"))
            assertTrue(seenCommand.orEmpty().contains(" 1 "))
            assertEquals("hello\n", result.output)
            assertEquals(0, result.exitCode)
            assertEquals(false, result.truncated)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun executeWritesFullLogAndKeepsTailWhenOutputIsLarge() = runBlocking {
        val dir = kotlin.io.path.createTempDirectory("chroot-tail").toFile()
        try {
            val executor = ChrootExecutor(
                linuxDir = File(dir, "linux"),
                workspaceDir = File(dir, "workspace"),
                logDir = File(dir, "logs"),
                sharedStorageEnabled = { false },
                resolvConf = { null },
                maxOutputBytes = 16,
                runner = RootCommandRunner { _, _, _ ->
                    RootProcessResult(0, "abcdefghijklmnopqrstuvwxyz", "", false, true)
                },
            )

            val result = executor.execute("cat", 5_000)

            assertTrue(result.truncated)
            assertEquals("klmnopqrstuvwxyz", result.output)
            assertTrue(File(result.fullOutputPath!!).isFile)
        } finally {
            dir.deleteRecursively()
        }
    }
}
