package io.github.whywood.deepdeep.plugin.api

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginHooksTest {
    @Test
    fun nonePreservesTransformDefaults() = runBlocking {
        val prompt = PluginHooks.None.promptTransform(PromptRequest("session", "hello"))
        assertEquals("hello", prompt)

        val input = PluginHooks.None.inputTransform(InputRequest("session", "hi", listOf("a.png")))
        assertEquals("hi", input.text)
        assertEquals(listOf("a.png"), input.imagePaths)
        assertFalse(input.handled)

        val args = buildJsonObject { put("path", "/tmp") }
        val tool = PluginHooks.None.beforeToolCall(ToolCallRequest("session", "call", "read", args))
        assertEquals(ToolCallAction.CONTINUE, tool.action)

        val result = PluginHooks.None.afterToolExecution(
            ToolExecutionResult("session", "call", "read", textResult("ok")),
        )
        assertEquals(ToolResultAction.KEEP, result.action)
    }

    @Test
    fun decisionFactoriesCarryTheirPayload() {
        val blocked = toolBlock("no")
        assertEquals(ToolCallAction.BLOCK, blocked.action)
        assertEquals("no", blocked.reason)

        val rewritten = toolRewrite(buildJsonObject { put("path", "/data") })
        assertEquals(ToolCallAction.REWRITE, rewritten.action)

        val replaced = resultReplace(textResult("new"))
        assertEquals(ToolResultAction.REPLACE, replaced.action)
        assertEquals("new", replaced.result?.text)

        val handled = inputHandled()
        assertTrue(handled.handled)
    }

    @Test
    fun timeoutConstantsMatchContract() {
        assertEquals(2_000L, PluginHooks.TRANSFORM_TIMEOUT_MS)
        assertEquals(5_000L, PluginHooks.OBSERVE_TIMEOUT_MS)
        assertTrue(PluginHooks.OBSERVE_TIMEOUT_MS > PluginHooks.TRANSFORM_TIMEOUT_MS)
    }

    @Test
    fun eventsExposeUsefulDerivedState() {
        val ok = object : RunFinishedEvent {
            override val sessionId: String = "s"
            override val runId: String = "r"
            override val error: String? = null
        }
        assertTrue(ok.succeeded)

        val failed = object : RunFinishedEvent {
            override val sessionId: String = "s"
            override val runId: String = "r"
            override val error: String? = "boom"
        }
        assertFalse(failed.succeeded)
    }

    @Test
    fun toolResultFactoriesPreservePayload() {
        val text = textResult("failed", isError = true)
        assertTrue(text.isError)
        assertEquals("failed", text.text)
        assertTrue(text.blocks.isEmpty())

        val blocks = listOf(textBlock("ok"), imageBlock(byteArrayOf(1, 2), "image/png"), fileRefBlock("/tmp/a"))
        val rich = richResult(blocks)
        assertEquals(3, rich.blocks.size)
        assertFalse(rich.isError)
    }
}
