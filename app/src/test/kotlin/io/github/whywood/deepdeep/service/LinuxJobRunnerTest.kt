package io.github.whywood.deepdeep.service

import io.github.whywood.deepdeep.runtime.host.HostAccess
import io.github.whywood.deepdeep.runtime.host.HostCapabilities
import io.github.whywood.deepdeep.runtime.host.HostEnvironment
import io.github.whywood.deepdeep.runtime.host.HostEnvironmentProvider
import io.github.whywood.deepdeep.runtime.host.StreamingShellExecutor
import io.github.whywood.deepdeep.runtime.host.StreamingShellResult
import io.github.whywood.deepdeep.sdk.coding.tools.ShellExecutor
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import org.junit.Assert.assertEquals
import org.junit.Test

class LinuxJobRunnerTest {
    @Test
    fun streamsOutputThroughStreamingShell() = runTest {
        val environment = object : HostEnvironment {
            override val access = HostAccess.ROOT
            override val capabilities = HostCapabilities(isRoot = true, hasSystemShell = true, canReadSystemData = true)
            override val sharedStorageEnabled = false
            override fun bash(cwd: String): ShellExecutor = error("fallback bash should not be used")
            override fun bashStreaming(cwd: String): StreamingShellExecutor = StreamingShellExecutor { _, _, onLine ->
                onLine("one")
                onLine("two")
                StreamingShellResult(exitCode = 7, output = "", truncated = false, timedOut = false, durationMillis = 3)
            }
            override val files: FileSystem = FileSystem.SYSTEM
            override val system: ShellExecutor? = null
            override val promptLines: List<String> = emptyList()
        }
        val provider = object : HostEnvironmentProvider {
            override fun current(): HostEnvironment = environment
        }

        val lines = mutableListOf<String>()
        val exitCode = LinuxJobRunner(provider, "/tmp").run("script") { line -> lines += line }

        assertEquals(7, exitCode)
        assertEquals(listOf("one\n", "two\n"), lines)
    }
}
