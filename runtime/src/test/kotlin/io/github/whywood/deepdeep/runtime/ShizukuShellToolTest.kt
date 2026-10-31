package io.github.whywood.deepdeep.runtime

import dev.pi.agent.AgentToolResult
import dev.pi.ai.TextContent
import dev.pi.coding.tools.CodingTool
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShizukuShellToolTest {
    private fun tool(
        executor: RootCommandExecutor = RootCommandExecutor { _, _ -> RootCommandResult(0, "", "", false) },
        enabled: () -> Boolean = { true },
        status: () -> RootToolStatus = { RootToolStatus.Granted },
    ): CodingTool = shizukuShellTool(executor, enabled, status)

    private fun resultText(result: AgentToolResult): String =
        result.content.filterIsInstance<TextContent>().joinToString("") { it.text }

    private fun args(command: String? = null, timeoutMillis: Long? = null): JsonObject = buildJsonObject {
        if (command != null) put("command", command)
        if (timeoutMillis != null) put("timeout_ms", timeoutMillis)
    }

    @Test
    fun toolExposesShizukuShellSchema() {
        val properties = tool().parameters.getValue("properties").jsonObject
        assertTrue(properties.containsKey("command"))
        assertTrue(properties.containsKey("timeout_ms"))
        assertEquals("shizuku_shell", tool().name)
    }

    @Test
    fun toolDescribesSessionPrivilegesInsteadOfFixedUid() {
        val tool = tool()

        assertTrue(tool.description.contains("privileges follow the Shizuku session"))
        assertTrue(tool.description.contains("uid 2000"))
        assertTrue(tool.description.contains("root"))
        assertFalse(tool.description.contains("as the ADB shell (uid 2000)"))
        assertTrue(tool.promptGuidelines.orEmpty().any { it.contains("uid 2000") })
    }

    @Test
    fun disabledToolReturnsShizukuDisabled() = runBlocking {
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
        assertEquals("shizuku_disabled", resultText(result))
        assertFalse(called)
    }

    @Test
    fun unsupportedStatusReturnsShizukuUnavailable() = runBlocking {
        val result = tool(status = { RootToolStatus.Unsupported }).execute("call-1", args("id"), null, null)

        assertEquals(true, result.isError)
        assertEquals("shizuku_unavailable", resultText(result))
    }

    @Test
    fun deniedStatusStillAttemptsExecution() = runBlocking {
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
        assertEquals("permission denied\n\nCommand exited with code 1", resultText(result))
    }

    @Test
    fun successPassesCommandAndDefaultTimeout() = runBlocking {
        var seenCommand: String? = null
        var seenTimeout = 0L
        val tool = tool(
            executor = RootCommandExecutor { command, timeout ->
                seenCommand = command
                seenTimeout = timeout
                RootCommandResult(0, "out", "", false)
            },
        )

        val result = tool.execute("call-1", args(command = "  pm list packages  "), null, null)

        assertEquals("pm list packages", seenCommand)
        assertEquals(15_000L, seenTimeout)
        assertNull(result.isError)
        assertEquals("out", resultText(result))
    }

    @Test
    fun truncatedOutputKeepsTruncationNotice() = runBlocking {
        val tool = tool(executor = RootCommandExecutor { _, _ -> RootCommandResult(0, "out", "err", true) })

        val result = tool.execute("call-1", args("id"), null, null)

        assertNull(result.isError)
        assertEquals("out\nerr\n\n[Output truncated]", resultText(result))
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
        assertEquals("timeout_ms 必须是整数", resultText(result))
    }

    @Test
    fun blankCommandReturnsErrorText() = runBlocking {
        val result = tool().execute("call-1", args("   "), null, null)

        assertEquals(true, result.isError)
        assertEquals("command 不能为空", resultText(result))
    }

    @Test
    fun executorFailureReturnsErrorText() = runBlocking {
        val tool = tool(executor = RootCommandExecutor { _, _ -> throw IllegalStateException("shizuku crashed") })

        val result = tool.execute("call-1", args("id"), null, null)

        assertEquals(true, result.isError)
        assertEquals("shizuku crashed", resultText(result))
    }
}
