package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.sdk.agent.AgentToolResult
import io.github.whywood.deepdeep.sdk.ai.RequestAbortedException
import io.github.whywood.deepdeep.sdk.ai.RunSignal
import io.github.whywood.deepdeep.sdk.ai.TextContent
import io.github.whywood.deepdeep.sdk.coding.tools.CodingTool
import io.github.whywood.deepdeep.sdk.coding.tools.ShellExecutionException
import io.github.whywood.deepdeep.sdk.coding.tools.ShellExecutor
import io.github.whywood.deepdeep.sdk.coding.tools.ShellResult
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepDeepBashToolTest {
    private fun tool(executor: ShellExecutor): CodingTool = deepDeepBashTool(executor)

    private fun executor(
        handler: suspend (command: String, timeoutSeconds: Long?, signal: RunSignal?) -> ShellResult,
    ): ShellExecutor = object : ShellExecutor {
        override suspend fun run(command: String, timeoutSeconds: Long?, signal: RunSignal?): ShellResult =
            handler(command, timeoutSeconds, signal)
    }

    private fun args(command: String = "true", timeout: Double? = null): JsonObject = buildJsonObject {
        put("command", command)
        if (timeout != null) put("timeout", timeout)
    }

    private fun resultText(result: AgentToolResult): String =
        result.content.filterIsInstance<TextContent>().joinToString("") { it.text }

    @Test
    fun exposesBashSchema() {
        val schema = tool(executor { _, _, _ -> ShellResult("", 0) })

        assertEquals("bash", schema.name)
        assertEquals(
            listOf("command"),
            schema.parameters.getValue("required").jsonArray.map { it.jsonPrimitive.content },
        )
        assertTrue(schema.parameters.getValue("properties").jsonObject.containsKey("timeout"))
        val timeout = schema.parameters.getValue("properties").jsonObject.getValue("timeout").jsonObject
        assertEquals(1, timeout.getValue("minimum").jsonPrimitive.int)
        assertEquals(600, timeout.getValue("maximum").jsonPrimitive.int)
        assertTrue(timeout.getValue("description").jsonPrimitive.content.contains("1-600"))
        assertTrue(schema.description.contains("[exit: N]"))
    }

    @Test
    fun reportsZeroExitCodeWithOutput() = runBlocking {
        val result = tool(executor { _, _, _ -> ShellResult("hello\n", 0) }).execute("call", args(), null, null)

        assertTrue(resultText(result).startsWith("hello"))
        assertTrue(resultText(result).contains("[exit: 0]"))
        assertNull(result.isError)
    }

    @Test
    fun reportsNonZeroExitCodeAsError() = runBlocking {
        val result = tool(executor { _, _, _ -> ShellResult("boom\n", 3) }).execute("call", args("false"), null, null)

        assertTrue(resultText(result).contains("boom"))
        assertTrue(resultText(result).contains("[exit: 3]"))
        assertEquals(true, result.isError)
    }

    @Test
    fun reportsMissingExitCodeAsError() = runBlocking {
        val result = tool(executor { _, _, _ -> ShellResult("killed", null) }).execute("call", args(), null, null)

        assertTrue(resultText(result).contains("Command terminated without an exit code"))
        assertEquals(true, result.isError)
    }

    @Test
    fun reportsEmptyOutput() = runBlocking {
        val result = tool(executor { _, _, _ -> ShellResult("", 0) }).execute("call", args(), null, null)

        assertTrue(resultText(result).contains("(no output)"))
        assertTrue(resultText(result).contains("[exit: 0]"))
    }

    @Test
    fun truncatesLongOutputTail() = runBlocking {
        val output = (1..3000).joinToString("\n") { "line $it" }
        val result = tool(executor { _, _, _ -> ShellResult(output, 0) }).execute("call", args(), null, null)
        val text = resultText(result)

        assertTrue(text.contains("Showing lines 1001-3000 of 3000"))
        assertTrue(text.contains("line 3000"))
        assertTrue(!text.contains("line 1\n"))
        assertTrue(text.contains("[exit: 0]"))
    }

    @Test
    fun reportsExecutorTruncation() = runBlocking {
        val result = tool(executor { _, _, _ -> ShellResult("tail", 0, truncated = true) })
            .execute("call", args(), null, null)

        assertTrue(resultText(result).contains("Output incomplete: upstream truncated"))
    }

    @Test
    fun reportsTimeoutAsError() = runBlocking {
        val result = tool(executor { _, _, _ -> throw ShellExecutionException("command timed out after 60s") })
            .execute("call", args(), null, null)

        assertEquals("command timed out after 60s", resultText(result))
        assertEquals(true, result.isError)
    }

    @Test
    fun passesTimeoutToExecutor() = runBlocking {
        var seenTimeout: Long? = null
        val result = tool(executor { _, timeout, _ ->
            seenTimeout = timeout
            ShellResult("ok", 0)
        }).execute("call", args(timeout = 5.0), null, null)

        assertEquals(5L, seenTimeout)
        assertTrue(resultText(result).contains("[exit: 0]"))
    }

    @Test
    fun rejectsNonPositiveTimeout() {
        listOf(0.0, -3.0).forEach { timeout ->
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    tool(executor { _, _, _ -> ShellResult("", 0) }).execute("call", args(timeout = timeout), null, null)
                }
            }
        }
    }

    @Test
    fun rejectsTimeoutAboveMaximum() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                tool(executor { _, _, _ -> ShellResult("", 0) }).execute("call", args(timeout = 600.5), null, null)
            }
        }
        assertEquals("Invalid timeout: maximum is 600 seconds", error.message)
    }

    @Test
    fun acceptsMaximumTimeout() = runBlocking {
        var seenTimeout: Long? = null
        val result = tool(executor { _, timeout, _ ->
            seenTimeout = timeout
            ShellResult("ok", 0)
        }).execute("call", args(timeout = 600.0), null, null)

        assertEquals(600L, seenTimeout)
        assertTrue(resultText(result).contains("[exit: 0]"))
    }

    @Test
    fun requiresCommand() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                tool(executor { _, _, _ -> ShellResult("", 0) })
                    .execute("call", buildJsonObject { }, null, null)
            }
        }
    }

    @Test
    fun abortedSignalSkipsExecution() {
        var called = false
        val bashTool = tool(executor { _, _, _ ->
            called = true
            ShellResult("", 0)
        })
        val signal = RunSignal().apply { abort("stop") }

        assertThrows(RequestAbortedException::class.java) {
            runBlocking { bashTool.execute("call", args(), signal, null) }
        }
        assertTrue(!called)
    }

    @Test
    fun detailedTimeoutKeepsOutputAndFullLogPath() = runBlocking {
        val executor = object : DetailedShellExecutor {
            override suspend fun runDetailed(command: String, timeoutSeconds: Long?, signal: RunSignal?) =
                CommandResult("last error", null, timedOut = true, truncated = true, fullOutputPath = "/tmp/full.log")
        }
        val result = tool(executor).execute("timeout", args(), null, null)
        assertEquals(true, result.isError)
        assertTrue(resultText(result).contains("last error"))
        assertTrue(resultText(result).contains("Command timed out"))
        assertTrue(resultText(result).contains("Full output: /tmp/full.log"))
        assertEquals("/tmp/full.log", result.details!!.jsonObject.getValue("fullOutputPath").jsonPrimitive.content)
    }

    @Test
    fun executorAbortPropagates() {
        assertThrows(RequestAbortedException::class.java) {
            runBlocking {
                tool(executor { _, _, _ -> throw RequestAbortedException("stop") })
                    .execute("call", args(), null, null)
            }
        }
    }
}
