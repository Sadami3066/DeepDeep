package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.sdk.agent.AgentToolResult
import io.github.whywood.deepdeep.sdk.ai.TextContent
import io.github.whywood.deepdeep.sdk.coding.tools.CodingTool
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

class AdbShellToolTest {
    private fun tool(
        executor: RootCommandExecutor = RootCommandExecutor { _, _ -> RootCommandResult(0, "", "", false) },
        enabled: () -> Boolean = { true },
        status: () -> RootToolStatus = { RootToolStatus.Granted },
    ): CodingTool = adbShellTool(executor, enabled, status)

    private fun resultText(result: AgentToolResult): String =
        result.content.filterIsInstance<TextContent>().joinToString("") { it.text }

    private fun args(command: String? = null, timeoutMillis: Long? = null): JsonObject = buildJsonObject {
        if (command != null) put("command", command)
        if (timeoutMillis != null) put("timeoutMs", timeoutMillis)
    }

    @Test
    fun toolExposesAdbShellSchema() {
        val properties = tool().parameters.getValue("properties").jsonObject
        assertTrue(properties.containsKey("command"))
        assertTrue(properties.containsKey("timeoutMs"))
        assertEquals("adb_shell", tool().name)
    }

    @Test
    fun toolDescriptionIsTaskOriented() {
        val tool = tool()

        assertTrue(tool.description.contains("Android shell"))
        assertTrue(tool.description.contains("combined output"))
        assertTrue(tool.description.contains("su"))
        assertTrue(tool.description.isNotBlank())
        assertFalse(tool.description.contains("not root"))
        val command = tool.parameters.getValue("properties").jsonObject.getValue("command").jsonObject
        assertFalse(command.getValue("description").toString().contains("not root"))
        assertNull(tool.promptSnippet)
        assertTrue(tool.promptGuidelines.orEmpty().contains(SYSTEM_SHELL_GUIDELINE))
    }

    @Test
    fun disabledToolReturnsAdbUnavailable() = runBlocking {
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
        assertEquals("ADB shell is unavailable: wireless debugging is not connected.", resultText(result))
        assertFalse(called)
    }

    @Test
    fun unsupportedStatusReturnsAdbUnavailable() = runBlocking {
        val result = tool(status = { RootToolStatus.Unsupported }).execute("call-1", args("id"), null, null)

        assertEquals(true, result.isError)
        assertEquals("ADB shell is unavailable on this device.", resultText(result))
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
        assertEquals("permission denied\n\n[exit: 1]", resultText(result))
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
        assertEquals("out\n\n[exit: 0]", resultText(result))
    }

    @Test
    fun truncatedOutputKeepsTruncationNotice() = runBlocking {
        val tool = tool(executor = RootCommandExecutor { _, _ -> RootCommandResult(0, "out", "err", true) })

        val result = tool.execute("call-1", args("id"), null, null)

        assertNull(result.isError)
        assertEquals("out\nerr\n\n[Output incomplete: upstream truncated it; no complete log is available]\n\n[exit: 0]", resultText(result))
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
                put("timeoutMs", "soon")
            },
            null,
            null,
        )

        assertEquals(true, result.isError)
        assertEquals("timeoutMs must be an integer", resultText(result))
    }

    @Test
    fun blankCommandReturnsErrorText() = runBlocking {
        val result = tool().execute("call-1", args("   "), null, null)

        assertEquals(true, result.isError)
        assertEquals("command must not be empty", resultText(result))
    }

    @Test
    fun executorFailureReturnsErrorText() = runBlocking {
        val tool = tool(executor = RootCommandExecutor { _, _ -> throw IllegalStateException("adb daemon crashed") })

        val result = tool.execute("call-1", args("id"), null, null)

        assertEquals(true, result.isError)
        assertEquals("adb daemon crashed", resultText(result))
    }
}
