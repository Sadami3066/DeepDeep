package io.github.whywood.deepdeep.runtime.host

import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class StreamingShellTest {
    @Test
    fun defaultEnvironmentHasNoStreamingShell() {
        assertNull(FakeHostEnvironment().bashStreaming("/tmp"))
    }

    @Test
    fun environmentExposesProvidedStreamingExecutor() {
        val executor = StreamingShellExecutor { command, timeoutMillis, onOutputLine ->
            onOutputLine(command)
            StreamingShellResult(
                exitCode = 0,
                output = command + "\n",
                truncated = false,
                timedOut = false,
                durationMillis = timeoutMillis,
            )
        }
        val environment = object : HostEnvironment by FakeHostEnvironment() {
            override fun bashStreaming(cwd: String): StreamingShellExecutor? = executor
        }

        assertSame(executor, environment.bashStreaming("/tmp"))
    }
}
