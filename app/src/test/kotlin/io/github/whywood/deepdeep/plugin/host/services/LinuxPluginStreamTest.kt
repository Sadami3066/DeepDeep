package io.github.whywood.deepdeep.plugin.host.services

import io.github.whywood.deepdeep.plugin.api.LinuxChunk
import io.github.whywood.deepdeep.runtime.host.HostAccess
import io.github.whywood.deepdeep.runtime.host.HostCapabilities
import io.github.whywood.deepdeep.runtime.host.HostEnvironment
import io.github.whywood.deepdeep.runtime.host.HostEnvironmentProvider
import io.github.whywood.deepdeep.runtime.host.StreamingShellExecutor
import io.github.whywood.deepdeep.runtime.host.StreamingShellResult
import io.github.whywood.deepdeep.sdk.ai.RunSignal
import io.github.whywood.deepdeep.sdk.coding.tools.ShellExecutor
import io.github.whywood.deepdeep.sdk.coding.tools.ShellResult
import io.github.whywood.deepdeep.shell.LinuxResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LinuxPluginStreamTest {
    @Test
    fun emitsLinesAndExitThenCompletes() = runTest {
        val chunks = linuxStream(
            execute = { onLine ->
                onLine("one")
                onLine("two")
                shellResult(exitCode = 0)
            },
        ).toList()

        assertEquals(
            listOf(LinuxChunk.Stdout("one"), LinuxChunk.Stdout("two"), LinuxChunk.Exit(0)),
            chunks,
        )
    }

    @Test
    fun slowCollectorReceivesEveryLine() = runTest {
        val lineCount = 500
        val chunks = linuxStream(
            execute = { onLine ->
                repeat(lineCount) { index -> onLine("line-$index") }
                shellResult(exitCode = 0)
            },
        ).onEach { delay(1) }.toList()

        assertEquals(lineCount, chunks.count { it is LinuxChunk.Stdout })
        assertEquals(LinuxChunk.Exit(0), chunks.last())
    }

    @Test
    fun executeFailureReachesCollector() = runTest {
        val flow = linuxStream(execute = { error("Linux environment is not installed") })

        val failure = runCatching { flow.toList() }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals("Linux environment is not installed", failure?.message)
    }

    @Test
    fun streamUsesJobAlignedTimeoutAndKeepsStdout() = runTest {
        var seenTimeout = -1L
        val environment = FakeHostEnvironment(
            streaming = FakeStreamingExecutor { timeoutMillis, onLine ->
                seenTimeout = timeoutMillis
                onLine("streamed")
                StreamingShellResult(
                    exitCode = 0,
                    output = "streamed",
                    truncated = false,
                    timedOut = false,
                    durationMillis = 5L,
                )
            },
        )
        val service = LinuxPluginService(
            hostEnvironment = object : HostEnvironmentProvider {
                override fun current() = environment
            },
            defaultWorkspace = { "/tmp" },
            availableProvider = { true },
        )

        val chunks = service.stream("apk info").toList()

        assertEquals(24 * 60 * 60 * 1000L, seenTimeout)
        assertEquals(listOf(LinuxChunk.Stdout("streamed"), LinuxChunk.Exit(0)), chunks)
    }

    @Test
    fun execReportsMergedOutputOnStdout() = runTest {
        val environment = FakeHostEnvironment(
            bashExecutor = object : ShellExecutor {
                override suspend fun run(command: String, timeoutSeconds: Long?, signal: RunSignal?): ShellResult =
                    ShellResult(output = "merged\n", exitCode = 0)
            },
        )
        val service = LinuxPluginService(
            hostEnvironment = object : HostEnvironmentProvider {
                override fun current() = environment
            },
            defaultWorkspace = { "/tmp" },
            availableProvider = { true },
        )

        val result = service.exec("apk info", null)

        assertTrue(result.stdout.contains("merged"))
        assertEquals("", result.stderr)
        assertEquals(0, result.exitCode)
    }

    private fun shellResult(exitCode: Int): LinuxResult = LinuxResult(
        exitCode = exitCode,
        output = "",
        truncated = false,
        timedOut = false,
        durationMillis = 1,
    )

    private class FakeHostEnvironment(
        private val bashExecutor: ShellExecutor = object : ShellExecutor {
            override suspend fun run(command: String, timeoutSeconds: Long?, signal: RunSignal?): ShellResult =
                ShellResult(output = "", exitCode = 0)
        },
        private val streaming: StreamingShellExecutor? = null,
    ) : HostEnvironment {
        override val access = HostAccess.LOCAL
        override val capabilities = HostCapabilities(bashAvailable = true)
        override val sharedStorageEnabled = false
        override val files = FileSystem.SYSTEM
        override val system: ShellExecutor? = null
        override val promptLines = emptyList<String>()

        override fun bash(cwd: String): ShellExecutor = bashExecutor
        override fun bashStreaming(cwd: String): StreamingShellExecutor? = streaming
    }

    private class FakeStreamingExecutor(
        private val execute: (timeoutMillis: Long, onLine: (String) -> Unit) -> StreamingShellResult,
    ) : StreamingShellExecutor {
        override suspend fun execute(
            command: String,
            timeoutMillis: Long,
            onOutputLine: (String) -> Unit,
        ): StreamingShellResult = execute(timeoutMillis, onOutputLine)
    }
}
