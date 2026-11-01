package io.github.whywood.deepdeep.runtime

import dev.pi.agent.AgentToolResult
import dev.pi.ai.RequestAbortedException
import dev.pi.ai.RunSignal
import dev.pi.ai.TextContent
import dev.pi.coding.tools.CodingTool
import dev.pi.coding.tools.ShellResult
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RootShellToolTest {
    private fun tool(
        executor: RootCommandExecutor = RootCommandExecutor { _, _ -> RootCommandResult(0, "", "", false) },
        enabled: () -> Boolean = { true },
        status: () -> RootToolStatus = { RootToolStatus.Granted },
    ): CodingTool = rootShellTool(executor, enabled, status)

    private fun resultText(result: AgentToolResult): String =
        result.content.filterIsInstance<TextContent>().joinToString("") { it.text }

    private fun args(command: String? = null, timeoutMillis: Long? = null): JsonObject = buildJsonObject {
        if (command != null) put("command", command)
        if (timeoutMillis != null) put("timeoutMs", timeoutMillis)
    }

    private fun adapter(executor: RootCommandExecutor): RootShellAdapter = RootShellAdapter(executor)

    @Test
    fun toolExposesRootShellSchema() {
        val properties = tool().parameters.getValue("properties").jsonObject
        assertTrue(properties.containsKey("command"))
        assertTrue(properties.containsKey("timeoutMs"))
        assertEquals("root_shell", tool().name)
    }

    @Test
    fun disabledToolReturnsRootDisabled() = runBlocking {
        var called = false
        val tool = tool(
            executor = RootCommandExecutor { _, _ ->
                called = true
                RootCommandResult(0, "", "", false)
            },
            enabled = { false },
        )

        val result = tool.execute("call-1", args("id"), null, null)

        assertEquals(true, result.isError)
        assertEquals("root_shell is unavailable: select the Root tier and grant root access first.", resultText(result))
        assertFalse(called)
    }

    @Test
    fun unsupportedStatusReturnsRootUnavailable() = runBlocking {
        val result = tool(status = { RootToolStatus.Unsupported }).execute("call-1", args("id"), null, null)

        assertEquals(true, result.isError)
        assertEquals("root_shell is unavailable: this device does not provide root access.", resultText(result))
    }

    @Test
    fun deniedProbeStatusStillAttemptsExecution() = runBlocking {
        var called = false
        val tool = tool(
            executor = RootCommandExecutor { _, _ ->
                called = true
                RootCommandResult(1, "", "permission denied", false)
            },
            status = { RootToolStatus.Denied },
        )

        val result = tool.execute("call-1", args("id"), null, null)

        assertTrue(called)
        assertEquals(true, result.isError)
        assertEquals("permission denied\n\n[exit: 1]", resultText(result))
    }

    @Test
    fun timedOutProbeStatusStillAttemptsExecution() = runBlocking {
        var called = false
        val tool = tool(
            executor = RootCommandExecutor { _, _ ->
                called = true
                RootCommandResult(0, "ok", "", false)
            },
            status = { RootToolStatus.Timeout },
        )

        val result = tool.execute("call-1", args("id"), null, null)

        assertTrue(called)
        assertNull(result.isError)
        assertEquals("ok\n\n[exit: 0]", resultText(result))
    }

    @Test
    fun notRequestedStatusStillAttemptsExecution() = runBlocking {
        var called = false
        val tool = tool(
            executor = RootCommandExecutor { _, _ ->
                called = true
                RootCommandResult(0, "", "", false)
            },
            status = { RootToolStatus.NotRequested },
        )

        val result = tool.execute("call-1", args("id"), null, null)

        assertTrue(called)
        assertNull(result.isError)
        assertEquals("(no output)\n\n[exit: 0]", resultText(result))
    }

    @Test
    fun successReturnsBashStyleText() = runBlocking {
        var seenCommand: String? = null
        var seenTimeout = 0L
        val tool = tool(
            executor = RootCommandExecutor { command, timeout ->
                seenCommand = command
                seenTimeout = timeout
                RootCommandResult(3, "out", "err", true)
            },
        )

        val result = tool.execute("call-1", args(command = "  id /data  "), null, null)

        assertEquals("id /data", seenCommand)
        assertEquals(15_000L, seenTimeout)
        assertEquals(true, result.isError)
        assertEquals("out\nerr\n\n[Output incomplete: upstream truncated it; no complete log is available]\n\n[exit: 3]", resultText(result))
    }

    @Test
    fun zeroExitOutputHasExplicitExitCode() = runBlocking {
        val tool = tool(executor = RootCommandExecutor { _, _ -> RootCommandResult(0, "out", "err", false) })

        val result = tool.execute("call-1", args("id"), null, null)

        assertNull(result.isError)
        assertEquals("out\nerr\n\n[exit: 0]", resultText(result))
    }

    @Test
    fun timeoutParameterIsClamped() = runBlocking {
        var seenTimeout = 0L
        val tool = tool(
            executor = RootCommandExecutor { _, timeout ->
                seenTimeout = timeout
                RootCommandResult(0, "", "", false)
            },
        )

        tool.execute("call-1", args("id", 999_999), null, null)
        assertEquals(120_000L, seenTimeout)

        tool.execute("call-2", args("id", 0), null, null)
        assertEquals(1_000L, seenTimeout)
    }

    @Test
    fun timeoutIsRoundedUpToWholeSeconds() = runBlocking {
        var seenTimeout = 0L
        val tool = tool(
            executor = RootCommandExecutor { _, timeout ->
                seenTimeout = timeout
                RootCommandResult(0, "", "", false)
            },
        )

        tool.execute("call-1", args("id", 2_500), null, null)

        assertEquals(3_000L, seenTimeout)
    }

    @Test
    fun missingExitCodeIsExplicitlyReported() = runBlocking {
        val tool = tool(
            executor = RootCommandExecutor { _, _ -> RootCommandResult(-1, "partial", "", false) },
        )

        val result = tool.execute("call-1", args("sleep 60"), null, null)

        assertEquals(true, result.isError)
        assertEquals("partial\n\nCommand terminated without an exit code", resultText(result))
    }

    @Test
    fun invalidTimeoutReturnsErrorText() = runBlocking {
        val result = tool().execute(
            "call-1",
            buildJsonObject {
                put("command", "id")
                put("timeoutMs", "soon")
            },
            null,
            null,
        )

        assertEquals(true, result.isError)
        assertEquals("timeoutMs 必须是整数", resultText(result))
    }

    @Test
    fun blankCommandReturnsErrorText() = runBlocking {
        val result = tool().execute("call-1", args("   "), null, null)

        assertEquals(true, result.isError)
        assertEquals("command 不能为空", resultText(result))
    }

    @Test
    fun executorFailureReturnsErrorText() = runBlocking {
        val tool = tool(executor = RootCommandExecutor { _, _ -> throw IllegalStateException("su crashed") })

        val result = tool.execute("call-1", args("id"), null, null)

        assertEquals(true, result.isError)
        assertEquals("su crashed", resultText(result))
    }

    @Test
    fun abortedSignalPropagatesAbortWithoutExecution() {
        var called = false
        val tool = tool(
            executor = RootCommandExecutor { _, _ ->
                called = true
                RootCommandResult(0, "", "", false)
            },
        )
        val signal = RunSignal().apply { abort("stop") }

        assertThrows(RequestAbortedException::class.java) {
            runBlocking { tool.execute("call-1", args("id"), signal, null) }
        }
        assertFalse(called)
    }

    @Test
    fun adapterMergesStdoutAndStderrAndPassesTimeout() = runBlocking {
        var seenCommand: String? = null
        var seenTimeout = 0L
        val adapter = adapter(
            RootCommandExecutor { command, timeout ->
                seenCommand = command
                seenTimeout = timeout
                RootCommandResult(0, "out", "err", false)
            },
        )

        val result = adapter.run("id", 5, null)

        assertEquals("id", seenCommand)
        assertEquals(5_000L, seenTimeout)
        assertEquals(ShellResult(output = "out\nerr", exitCode = 0, truncated = false), result)
    }

    @Test
    fun adapterSkipsEmptyStdoutAndStderr() = runBlocking {
        val withEmptyStderr = adapter(RootCommandExecutor { _, _ -> RootCommandResult(0, "out", "", false) })
        assertEquals("out", withEmptyStderr.run("id", 1, null).output)

        val withEmptyStdout = adapter(RootCommandExecutor { _, _ -> RootCommandResult(0, "", "err", false) })
        assertEquals("err", withEmptyStdout.run("id", 1, null).output)

        val withEmptyOutput = adapter(RootCommandExecutor { _, _ -> RootCommandResult(0, "", "", false) })
        assertEquals("", withEmptyOutput.run("id", 1, null).output)
    }

    @Test
    fun adapterDefaultsTimeoutToFifteenSeconds() = runBlocking {
        var seenTimeout = 0L
        val adapter = adapter(
            RootCommandExecutor { _, timeout ->
                seenTimeout = timeout
                RootCommandResult(0, "", "", false)
            },
        )

        adapter.run("id", null, null)

        assertEquals(15_000L, seenTimeout)
    }

    @Test
    fun adapterClampsTimeoutSeconds() = runBlocking {
        var seenTimeout = 0L
        val adapter = adapter(
            RootCommandExecutor { _, timeout ->
                seenTimeout = timeout
                RootCommandResult(0, "", "", false)
            },
        )

        adapter.run("id", 0, null)
        assertEquals(1_000L, seenTimeout)

        adapter.run("id", 500, null)
        assertEquals(120_000L, seenTimeout)
    }

    @Test
    fun adapterPassesExitCodeAndTruncation() = runBlocking {
        val adapter = adapter(RootCommandExecutor { _, _ -> RootCommandResult(-1, "partial", "", true) })

        assertEquals(ShellResult(output = "partial", exitCode = null, truncated = true), adapter.run("id", 5, null))
    }

    @Test
    fun abortDuringExecutionWaitsForCleanupAndDoesNotRetry() = runTest {
        val started = CompletableDeferred<Unit>()
        var cleanup = false
        var calls = 0
        val signal = RunSignal()
        val tool = tool(executor = RootCommandExecutor { _, _ ->
            calls++
            started.complete(Unit)
            try { awaitCancellation() } finally { cleanup = true }
        })
        val run = async { runCatching { tool.execute("cancel", args("sleep 60"), signal, null) } }
        runCurrent()
        started.await()
        signal.abort("stop")
        runCurrent()
        assertTrue(run.await().exceptionOrNull() is RequestAbortedException)
        assertTrue(cleanup)
        assertEquals(1, calls)
    }

    @Test
    fun adapterThrowsWhenSignalAlreadyAborted() {
        var called = false
        val adapter = adapter(
            RootCommandExecutor { _, _ ->
                called = true
                RootCommandResult(0, "", "", false)
            },
        )
        val signal = RunSignal().apply { abort("stop") }

        assertThrows(RequestAbortedException::class.java) {
            runBlocking { adapter.run("id", 5, signal) }
        }
        assertFalse(called)
    }
}
