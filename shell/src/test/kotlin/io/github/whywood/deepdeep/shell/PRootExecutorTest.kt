package io.github.whywood.deepdeep.shell

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PRootExecutorTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun buildCommandContainsSandboxArguments() {
        val fixture = fixture()

        val command = fixture.executor.buildCommand("echo hi")

        assertTrue(command.containsAll(listOf("-0", "--link2symlink", "-r", fixture.rootfs.absolutePath)))
        assertTrue(command.containsAll(listOf("-b", "/dev", "/proc", "/sys")))
        assertTrue(command.contains("${fixture.workspace.absolutePath}:/workspace"))
        assertTrue(command.containsAll(listOf("-w", "/workspace", "--kill-on-exit", "/bin/sh", "-c", "echo hi")))
    }

    @Test
    fun executeReturnsExitCodeAndMergedOutput() = runBlocking {
        val result = fixture().executor.execute("printf hi; printf err >&2; exit 7")

        assertEquals(7, result.exitCode)
        assertTrue(result.output.contains("hi"))
        assertTrue(result.output.contains("err"))
        assertTrue(!result.timedOut)
    }

    @Test
    fun truncatesLargeOutput() = runBlocking {
        val fixture = fixture(maxOutputBytes = 128)

        val result = fixture.executor.execute("head -c 5000 /dev/zero | tr '\\0' x")

        assertTrue(result.truncated)
        assertTrue(result.output.length <= 128)
    }

    @Test
    fun timeoutKillsCommand() = runBlocking {
        val result = fixture().executor.execute("sleep 5", timeoutMillis = 300)

        assertTrue(result.timedOut)
        assertTrue(result.durationMillis < 3000)
    }

    @Test
    fun cancelRightAfterStartBeforePidIsReady() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val fixture = fixture(pidReader = {
            started.countDown()
            release.await()
            0
        })
        val job = launch(Dispatchers.Default) {
            fixture.executor.execute("sleep 30", timeoutMillis = 60_000)
        }

        assertTrue(started.await(5, TimeUnit.SECONDS))
        val elapsed = kotlin.system.measureTimeMillis { job.cancelAndJoin() }
        release.countDown()

        assertTrue(job.isCancelled)
        assertTrue(elapsed < 5_000)
    }

    @Test
    fun cancellationStopsCommandPromptly() = runBlocking {
        val fixture = fixture()
        val job = launch(Dispatchers.Default) {
            fixture.executor.execute("sleep 30", timeoutMillis = 60_000)
        }
        delay(500)

        val elapsed = kotlin.system.measureTimeMillis { job.cancelAndJoin() }

        assertTrue(job.isCancelled)
        assertTrue(elapsed < 5000)
    }

    private fun fixture(
        maxOutputBytes: Int = 1024,
        pidReader: ((File) -> Long)? = null,
    ): Fixture {
        val rootfs = temp.newFolder("rootfs")
        val workspace = temp.newFolder("workspace")
        val tempDir = temp.newFolder("tmp")
        val loader = File(temp.root, "libproot-loader.so").apply { writeText("") }
        val proot = File(temp.root, "proot").apply {
            writeText(
                "#!/bin/sh\n" +
                    "last=\"\"\n" +
                    "for arg in \"\$@\"; do last=\"\$arg\"; done\n" +
                    "exec /bin/sh -c \"\$last\"\n",
            )
            setExecutable(true, false)
        }
        val executor = if (pidReader == null) {
            PRootExecutor(
                prootBinary = proot,
                prootLoader = loader,
                rootfsDir = rootfs,
                workspaceDir = workspace,
                tempDir = tempDir,
                maxOutputBytes = maxOutputBytes,
            )
        } else {
            PRootExecutor(
                prootBinary = proot,
                prootLoader = loader,
                rootfsDir = rootfs,
                workspaceDir = workspace,
                tempDir = tempDir,
                maxOutputBytes = maxOutputBytes,
                pidReader = pidReader,
            )
        }
        return Fixture(rootfs, workspace, executor)
    }

    private data class Fixture(
        val rootfs: File,
        val workspace: File,
        val executor: PRootExecutor,
    )
}
