package io.github.whywood.deepdeep.plugin.api

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginHooksTest {
    @Test
    fun nonePreservesTransformDefaults() = runBlocking {
        val prompt = PluginHooks.None.promptTransform(PromptRequest("session", "hello"))
        assertEquals("hello", prompt)

        val input = InputRequest("session", "hi", listOf("a.png"))
        val decision = PluginHooks.None.inputTransform(input)
        assertEquals(InputDecision.Continue("hi", listOf("a.png")), decision)

        val args = buildJsonObject { put("path", "/tmp") }
        assertEquals(
            ToolCallDecision.Continue,
            PluginHooks.None.beforeToolCall(ToolCallRequest("session", "call", "read", args)),
        )
        assertEquals(
            ToolResultDecision.Keep,
            PluginHooks.None.afterToolExecution(
                ToolExecutionResult("session", "call", "read", PluginToolResult.Text("ok")),
            ),
        )
    }

    @Test
    fun noneIgnoresNotifications() = runBlocking {
        val hooks: PluginHooks = PluginHooks.None
        hooks.sessionStarted(PluginEvent.SessionStarted("s"))
        hooks.sessionClosed(PluginEvent.SessionClosed("s"))
        hooks.runStarted(PluginEvent.RunStarted("s", "r"))
        hooks.runFinished(PluginEvent.RunFinished("s", "r"))
        hooks.turnStarted(PluginEvent.TurnStarted("s", "t"))
        hooks.turnFinished(PluginEvent.TurnFinished("s", "t"))
        hooks.messageStarted(PluginEvent.MessageStarted("s", "m", PluginEvent.MessageRole.USER))
        hooks.messageDelta(PluginEvent.MessageDelta("s", "m", "delta"))
        hooks.messageFinished(PluginEvent.MessageFinished("s", "m"))
        hooks.toolExecutionUpdate(
            PluginEvent.ToolExecutionUpdate("s", "c", "tool", PluginEvent.ToolPhase.STARTED),
        )
        hooks.compactionStarted(PluginEvent.CompactionStarted("s"))
        hooks.compactionFinished(PluginEvent.CompactionFinished("s", 100, 20))
        hooks.compactionFailed(PluginEvent.CompactionFailed("s", "error"))
        hooks.modelChanged(PluginEvent.ModelChanged("s", "model"))
        hooks.thinkingLevelChanged(PluginEvent.ThinkingLevelChanged("s", "high"))
        hooks.toolSetChanged(PluginEvent.ToolSetChanged("s", listOf("read")))
        hooks.retryScheduled(PluginEvent.RetryScheduled("s", 1, 1_000))
        hooks.retrySucceeded(PluginEvent.RetrySucceeded("s", 1))
        hooks.retryExhausted(PluginEvent.RetryExhausted("s", 3, "error"))
        hooks.mcpStatusChanged(PluginEvent.McpStatusChanged("server", McpServerStatus.OK))
        hooks.mcpToolsChanged(PluginEvent.McpToolsChanged("server", listOf("tool")))
        hooks.busEvent(PluginEvent.BusEvent(PluginBusEvent("topic", "publisher")))
    }

    @Test
    fun timeoutConstantsMatchContract() {
        assertEquals(2_000L, PluginHooks.TRANSFORM_TIMEOUT_MS)
        assertEquals(5_000L, PluginHooks.OBSERVE_TIMEOUT_MS)
        assertTrue(PluginHooks.OBSERVE_TIMEOUT_MS > PluginHooks.TRANSFORM_TIMEOUT_MS)
    }

    @Test
    fun eventsExposeUsefulDerivedState() {
        assertTrue(PluginEvent.RunFinished("s", "r").succeeded)
        assertEquals(false, PluginEvent.RunFinished("s", "r", "boom").succeeded)
    }

    @Test
    fun toolResultFactoriesPreserveErrorFlag() {
        val text = PluginToolResult.text("failed", isError = true)
        assertTrue(text.isError)
        assertEquals(PluginToolResult.Text("failed", null, true), text)

        val details = JsonObject(emptyMap())
        val rich = PluginToolResult.rich(listOf(ToolBlock.Text("ok")), details = details)
        assertEquals(PluginToolResult.Rich(listOf(ToolBlock.Text("ok")), details, false), rich)
    }
}
