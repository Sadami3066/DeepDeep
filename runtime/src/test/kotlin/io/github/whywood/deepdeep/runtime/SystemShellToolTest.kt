package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.runtime.host.HostAccessUnavailableException
import io.github.whywood.deepdeep.sdk.ai.TextContent
import io.github.whywood.deepdeep.sdk.agent.AgentToolResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemShellToolTest {
    @Test
    fun reportsRetryableErrorWhenEnvironmentIsNotReady() = runTest {
        val tool = systemShellTool { throw HostAccessUnavailableException("Root Linux is being prepared") }

        val result = tool.execute(
            "call",
            buildJsonObject { put("command", "pm list packages") },
            null,
            null,
        )

        assertEquals(true, result.isError)
        val message = textOf(result)
        assertTrue(message.contains("Root Linux is being prepared"))
        assertTrue(message.contains("Retry once it becomes available"))
    }

    @Test
    fun keepsMissingShellMessageAsError() = runTest {
        val tool = systemShellTool { null }

        val result = tool.execute(
            "call",
            buildJsonObject { put("command", "pm list packages") },
            null,
            null,
        )

        assertEquals(true, result.isError)
        assertTrue(textOf(result).contains("not available in the current access tier"))
    }

    private fun textOf(result: AgentToolResult): String =
        result.content.filterIsInstance<TextContent>().joinToString("") { it.text }
}
