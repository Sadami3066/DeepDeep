package io.github.whywood.deepdeep.runtime

import dev.pi.agent.AgentToolResult
import dev.pi.ai.TextContent
import dev.pi.coding.tools.CodingTool
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RootShellToolTest {
    private fun tool(
        executor: RootCommandExecutor = RootCommandExecutor { _, _ -> RootCommandResult(0, "", "", false) },
        enabled: () -> Boolean = { true },
        foreground: () -> Boolean = { true },
        userTurn: () -> Boolean = { true },
        status: () -> RootToolStatus = { RootToolStatus.Granted },
    ): CodingTool = rootShellTool(executor, enabled, foreground, userTurn, status)

    private fun resultText(result: AgentToolResult): String =
        result.content.filterIsInstance<TextContent>().joinToString("") { it.text }

    private fun resultJson(result: AgentToolResult): JsonObject =
        Json.parseToJsonElement(resultText(result)).jsonObject

    private fun args(command: String? = null, timeoutMillis: Long? = null): JsonObject = buildJsonObject {
        if (command != null) put("command", command)
        if (timeoutMillis != null) put("timeout_ms", timeoutMillis)
    }

    @Test
    fun toolExposesRootShellSchema() {
        val properties = tool().parameters.getValue("properties").jsonObject
        assertTrue(properties.containsKey("command"))
        assertTrue(properties.containsKey("timeout_ms"))
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
        assertEquals("root_disabled", resultText(result))
        assertFalse(called)
    }

    @Test
    fun backgroundToolReturnsForegroundRequired() = runBlocking {
        var called = false
        val tool = tool(
            executor = RootCommandExecutor { _, _ ->
                called = true
                RootCommandResult(0, "", "", false)
            },
            foreground = { false },
        )

        val result = tool.execute("call-1", args("id"), null, null)

        assertEquals(true, result.isError)
        assertEquals("root_foreground_required", resultText(result))
        assertFalse(called)
    }

    @Test
    fun backgroundTurnReturnsUserTurnRequired() = runBlocking {
        var called = false
        val tool = tool(
            executor = RootCommandExecutor { _, _ ->
                called = true
                RootCommandResult(0, "", "", false)
            },
            userTurn = { false },
        )

        val result = tool.execute("call-1", args("id"), null, null)

        assertEquals(true, result.isError)
        assertEquals("root_user_turn_required", resultText(result))
        assertFalse(called)
    }

    @Test
    fun unsupportedStatusReturnsRootUnavailable() = runBlocking {
        val result = tool(status = { RootToolStatus.Unsupported }).execute("call-1", args("id"), null, null)

        assertEquals(true, result.isError)
        assertEquals("root_unavailable", resultText(result))
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
        assertNull(result.isError)
        assertTrue(resultText(result).contains("\"exitCode\":1"))
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
    }

    @Test
    fun successReturnsResultJson() = runBlocking {
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
        assertNull(result.isError)
        val body = resultJson(result)
        assertEquals(3, (body.getValue("exitCode") as JsonPrimitive).content.toInt())
        assertEquals("out", (body.getValue("stdout") as JsonPrimitive).content)
        assertEquals("err", (body.getValue("stderr") as JsonPrimitive).content)
        assertEquals(true, (body.getValue("truncated") as JsonPrimitive).content.toBoolean())
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
        assertEquals(1L, seenTimeout)
    }

    @Test
    fun executionTimeoutIsReportedInJson() = runBlocking {
        val tool = tool(
            executor = RootCommandExecutor { _, _ -> RootCommandResult(-1, "partial", "", false) },
        )

        val result = tool.execute("call-1", args("sleep 60"), null, null)

        assertNull(result.isError)
        val body = resultJson(result)
        assertEquals(-1, (body.getValue("exitCode") as JsonPrimitive).content.toInt())
        assertEquals("partial", (body.getValue("stdout") as JsonPrimitive).content)
    }

    @Test
    fun invalidTimeoutReturnsErrorText() = runBlocking {
        val result = tool().execute(
            "call-1",
            buildJsonObject {
                put("command", "id")
                put("timeout_ms", "soon")
            },
            null,
            null,
        )

        assertEquals(true, result.isError)
        assertTrue(resultText(result).isNotBlank())
    }

    @Test
    fun blankCommandReturnsErrorText() = runBlocking {
        val result = tool().execute("call-1", args("   "), null, null)

        assertEquals(true, result.isError)
        assertTrue(resultText(result).isNotBlank())
    }

    @Test
    fun executorFailureReturnsErrorText() = runBlocking {
        val tool = tool(executor = RootCommandExecutor { _, _ -> throw IllegalStateException("su crashed") })

        val result = tool.execute("call-1", args("id"), null, null)

        assertEquals(true, result.isError)
        assertEquals("su crashed", resultText(result))
    }
}
