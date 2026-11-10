package io.github.whywood.deepdeep.shell

import java.io.File
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChrootStreamingTest {
    @Test
    fun executeStreamingForwardsLinesAndMatchesExecuteResult() = runBlocking {
        val dir = tempDir()
        try {
            var seenStdin: String? = null
            val runner = object : RootCommandRunner {
                override suspend fun run(
                    command: String,
                    timeoutMillis: Long,
                    stdin: String?,
                ): RootProcessResult = run(command, timeoutMillis, stdin, null)

                override suspend fun run(
                    command: String,
                    timeoutMillis: Long,
                    stdin: String?,
                    onOutputLine: ((String) -> Unit)?,
                ): RootProcessResult {
                    seenStdin = stdin
                    onOutputLine?.invoke("one")
                    onOutputLine?.invoke("two")
                    return RootProcessResult(3, "one\ntwo\n", "", false, true)
                }
            }

            val lines = mutableListOf<String>()
            val streamed = executor(dir, runner).executeStreaming("echo hi", 5_000) { lines += it }
            assertEquals(listOf("one", "two"), lines)
            assertEquals("echo hi", seenStdin)
            assertEquals(3, streamed.exitCode)
            assertEquals("one\ntwo\n", streamed.output)
            assertFalse(streamed.truncated)
            assertFalse(streamed.timedOut)
            assertNull(streamed.fullOutputPath)

            val direct = executor(dir, runner).execute("echo hi", 5_000)
            assertEquals(direct.exitCode, streamed.exitCode)
            assertEquals(direct.output, streamed.output)
            assertEquals(direct.truncated, streamed.truncated)
            assertEquals(direct.timedOut, streamed.timedOut)
            assertEquals(direct.fullOutputPath, streamed.fullOutputPath)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun executeStreamingBoundsOutputAndWritesFullLog() = runBlocking {
        val dir = tempDir()
        try {
            val runner = object : RootCommandRunner {
                override suspend fun run(
                    command: String,
                    timeoutMillis: Long,
                    stdin: String?,
                ): RootProcessResult = run(command, timeoutMillis, stdin, null)

                override suspend fun run(
                    command: String,
                    timeoutMillis: Long,
                    stdin: String?,
                    onOutputLine: ((String) -> Unit)?,
                ): RootProcessResult {
                    onOutputLine?.invoke("abcdefghijklmnopqrstuvwxyz")
                    return RootProcessResult(0, "abcdefghijklmnopqrstuvwxyz", "", false, true)
                }
            }

            val streamed = executor(dir, runner, maxOutputBytes = 16).executeStreaming("cat", 5_000) {}
            assertTrue(streamed.truncated)
            assertEquals("klmnopqrstuvwxyz", streamed.output)
            assertTrue(File(streamed.fullOutputPath!!).isFile)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun executeStreamingPropagatesTimeout() = runBlocking {
        val dir = tempDir()
        try {
            val runner = object : RootCommandRunner {
                override suspend fun run(
                    command: String,
                    timeoutMillis: Long,
                    stdin: String?,
                ): RootProcessResult = run(command, timeoutMillis, stdin, null)

                override suspend fun run(
                    command: String,
                    timeoutMillis: Long,
                    stdin: String?,
                    onOutputLine: ((String) -> Unit)?,
                ): RootProcessResult {
                    onOutputLine?.invoke("partial")
                    return RootProcessResult(-1, "partial\n", "", true, true)
                }
            }

            val streamed = executor(dir, runner).executeStreaming("sleep 10", 1_000) {}
            assertEquals(-1, streamed.exitCode)
            assertEquals("partial\n", streamed.output)
            assertTrue(streamed.timedOut)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun executeStreamingPropagatesCancellation() = runBlocking {
        val dir = tempDir()
        try {
            val runner = object : RootCommandRunner {
                override suspend fun run(
                    command: String,
                    timeoutMillis: Long,
                    stdin: String?,
                ): RootProcessResult = run(command, timeoutMillis, stdin, null)

                override suspend fun run(
                    command: String,
                    timeoutMillis: Long,
                    stdin: String?,
                    onOutputLine: ((String) -> Unit)?,
                ): RootProcessResult {
                    delay(10_000)
                    return RootProcessResult(0, "", "", false, true)
                }
            }

            val job = launch { executor(dir, runner).executeStreaming("sleep 10", 60_000) {} }
            delay(50)
            job.cancelAndJoin()
            assertTrue(job.isCancelled)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun executeStreamingSurvivesFailingCallback() = runBlocking {
        val dir = tempDir()
        try {
            val runner = object : RootCommandRunner {
                override suspend fun run(
                    command: String,
                    timeoutMillis: Long,
                    stdin: String?,
                ): RootProcessResult = run(command, timeoutMillis, stdin, null)

                override suspend fun run(
                    command: String,
                    timeoutMillis: Long,
                    stdin: String?,
                    onOutputLine: ((String) -> Unit)?,
                ): RootProcessResult {
                    onOutputLine?.invoke("one")
                    onOutputLine?.invoke("two")
                    return RootProcessResult(0, "one\ntwo\n", "", false, true)
                }
            }

            var attempts = 0
            val streamed = executor(dir, runner).executeStreaming("echo", 5_000) {
                attempts++
                throw IllegalStateException("consumer failed")
            }
            assertEquals(2, attempts)
            assertEquals(0, streamed.exitCode)
            assertEquals("one\ntwo\n", streamed.output)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun executor(
        dir: File,
        runner: RootCommandRunner,
        maxOutputBytes: Int = 1024,
    ): ChrootExecutor = ChrootExecutor(
        linuxDir = File(dir, "linux"),
        workspaceDir = File(dir, "workspace"),
        logDir = File(dir, "logs"),
        sharedStorageEnabled = { false },
        resolvConf = { null },
        maxOutputBytes = maxOutputBytes,
        runner = runner,
    )

    private fun tempDir(): File = kotlin.io.path.createTempDirectory("chroot-streaming").toFile()
}
