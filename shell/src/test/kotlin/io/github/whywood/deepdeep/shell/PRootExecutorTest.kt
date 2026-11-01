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
import org.junit.Assert.assertFalse
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
    fun sharedStorageDisabledOmitsBindMounts() {
        val command = fixture().executor.buildCommand("echo hi")

        assertFalse(command.contains("/storage/emulated/0:/storage/emulated/0"))
        assertFalse(command.contains("/storage/emulated/0:/sdcard"))
    }

    @Test
    fun sharedStorageEnabledAddsBindMountsAndTargetDirectories() = runBlocking {
        val fixture = fixture(sharedStorageEnabled = { true })

        val command = fixture.executor.buildCommand("echo hi")

        assertTrue(command.contains("/storage/emulated/0:/storage/emulated/0"))
        assertTrue(command.contains("/storage/emulated/0:/sdcard"))
        val result = fixture.executor.execute("echo hi")
        assertEquals(0, result.exitCode)
        assertTrue(File(fixture.rootfs, "storage/emulated/0").isDirectory)
        assertTrue(File(fixture.rootfs, "sdcard").isDirectory)
    }

    @Test
    fun extraBindsAddMountArgumentsAndTargetDirectories() = runBlocking {
        val skillsHost = File(temp.root, "agent-skills")
        val repoHost = File(temp.root, "skill-repo")
        val fixture = fixture(
            extraBinds = listOf(
                skillsHost to "/skills",
                repoHost to "/skill-repo",
            ),
        )

        val command = fixture.executor.buildCommand("echo hi")

        assertTrue(command.contains("${skillsHost.absolutePath}:/skills"))
        assertTrue(command.contains("${repoHost.absolutePath}:/skill-repo"))
        val result = fixture.executor.execute("echo hi")
        assertEquals(0, result.exitCode)
        assertTrue(File(fixture.rootfs, "skills").isDirectory)
        assertTrue(File(fixture.rootfs, "skill-repo").isDirectory)
        assertTrue(skillsHost.isDirectory)
        assertTrue(repoHost.isDirectory)
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
    fun capsUnterminatedOutputChunks() = runBlocking {
        val fixture = fixture(maxOutputBytes = 64 * 1024)
        val chunks = mutableListOf<String>()

        val result = fixture.executor.executeStreaming(
            "head -c 8388608 /dev/zero | tr '\\0' x",
            60_000,
            { line -> chunks += line },
        )

        assertTrue(result.truncated)
        assertTrue(result.output.toByteArray(Charsets.UTF_8).size <= 64 * 1024)
        assertTrue(chunks.isNotEmpty())
        assertTrue(chunks.all { it.toByteArray(Charsets.UTF_8).size <= 8 * 1024 })
        assertEquals(8 * 1024 * 1024, chunks.sumOf { it.toByteArray(Charsets.UTF_8).size })
    }

    @Test
    fun streamsCompleteLinesSeparately() = runBlocking {
        val fixture = fixture()
        val lines = mutableListOf<String>()

        val result = fixture.executor.executeStreaming("printf 'one\\ntwo\\nthree'", 60_000, { line -> lines += line })

        assertEquals(0, result.exitCode)
        assertEquals(listOf("one", "two", "three"), lines)
    }

    @Test
    fun longLineFlushDoesNotEmitEmptyLine() = runBlocking {
        val fixture = fixture()
        val chunks = mutableListOf<String>()

        val result = fixture.executor.executeStreaming(
            "head -c 8192 /dev/zero | tr '\\0' x; printf '\\ntail\\n'",
            60_000,
            { line -> chunks += line },
        )

        assertEquals(0, result.exitCode)
        assertEquals(2, chunks.size)
        assertEquals(8192, chunks[0].length)
        assertEquals("tail", chunks[1])
    }

    @Test
    fun splitsUnterminatedMultibyteOutputOnCharacterBoundaries() = runBlocking {
        val fixture = fixture(maxOutputBytes = 64 * 1024)
        val chunks = mutableListOf<String>()

        val result = fixture.executor.executeStreaming(
            "i=0; while [ \$i -lt 3000 ]; do printf '\\344\\270\\255'; i=\$((i+1)); done",
            60_000,
            { line -> chunks += line },
        )

        assertEquals(0, result.exitCode)
        assertTrue(chunks.all { it.toByteArray(Charsets.UTF_8).size <= 8 * 1024 })
        assertTrue(chunks.none { it.contains('\uFFFD') })
        assertEquals("中".repeat(3000), chunks.joinToString(""))
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

    @Test
    fun cancellationDoesNotRunRemainingCommands() = runBlocking {
        val fixture = fixture()
        val job = launch(Dispatchers.Default) {
            fixture.executor.execute("trap '' TERM; sleep 30; echo late-done > late.txt", timeoutMillis = 60_000)
        }
        delay(500)

        job.cancelAndJoin()
        delay(300)

        assertFalse("cancelled command kept running", File(fixture.workspace, "late.txt").exists())
    }

    private fun fixture(
        maxOutputBytes: Int = 1024,
        pidReader: ((File) -> Long)? = null,
        sharedStorageEnabled: () -> Boolean = { false },
        extraBinds: List<Pair<File, String>> = emptyList(),
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
                sharedStorageEnabled = sharedStorageEnabled,
                extraBinds = extraBinds,
                maxOutputBytes = maxOutputBytes,
            )
        } else {
            PRootExecutor(
                prootBinary = proot,
                prootLoader = loader,
                rootfsDir = rootfs,
                workspaceDir = workspace,
                tempDir = tempDir,
                sharedStorageEnabled = sharedStorageEnabled,
                extraBinds = extraBinds,
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
