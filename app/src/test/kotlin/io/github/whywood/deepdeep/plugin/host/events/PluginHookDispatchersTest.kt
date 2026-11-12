package io.github.whywood.deepdeep.plugin.host.events

import io.github.whywood.deepdeep.plugin.api.InputDecision
import io.github.whywood.deepdeep.plugin.api.InputRequest
import io.github.whywood.deepdeep.plugin.api.PluginContextRequest
import io.github.whywood.deepdeep.plugin.api.PluginToolResult
import io.github.whywood.deepdeep.plugin.api.PromptRequest
import io.github.whywood.deepdeep.plugin.api.ToolCallAction
import io.github.whywood.deepdeep.plugin.api.ToolCallDecision
import io.github.whywood.deepdeep.plugin.api.ToolCallRequest
import io.github.whywood.deepdeep.plugin.api.ToolExecutionResult
import io.github.whywood.deepdeep.plugin.api.ToolResultDecision
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import io.github.whywood.deepdeep.plugin.api.contributions
import io.github.whywood.deepdeep.plugin.api.inputContinue
import io.github.whywood.deepdeep.plugin.api.inputHandled
import io.github.whywood.deepdeep.plugin.api.resultKeep
import io.github.whywood.deepdeep.plugin.api.resultReplace
import io.github.whywood.deepdeep.plugin.api.textResult
import io.github.whywood.deepdeep.plugin.api.toolBlock
import io.github.whywood.deepdeep.plugin.api.toolContinue
import io.github.whywood.deepdeep.plugin.api.toolRewrite

class PluginHookDispatchersTest {
    private val reporter = RecordingReporter()

    private fun dispatchers(source: PluginHookSource, timeoutMs: Long = 1_000): PluginHookDispatchers =
        PluginHookDispatchers(source, reporter, timeoutMs)

    private fun promptRequest(prompt: String = "hi"): PromptRequest = PromptRequest("session-1", prompt)

    private fun toolCallRequest(args: JsonObject = JsonObject(emptyMap())): ToolCallRequest =
        ToolCallRequest("session-1", "call-1", "bash", args)

    private fun toolExecution(result: PluginToolResult = textResult("original")): ToolExecutionResult =
        ToolExecutionResult("session-1", "call-1", "bash", result)

    @Test
    fun promptTransformsChainInOrder() = runTest {
        val seen = mutableListOf<String>()
        val first = probeHooks(onPrompt = { request ->
            seen += request.prompt
            "${request.prompt}|a"
        })
        val second = probeHooks(onPrompt = { request ->
            seen += request.prompt
            "${request.prompt}|b"
        })
        val source = TestHookSource(listOf(hookEntry("a", first), hookEntry("b", second)))
        assertEquals("hi|a|b", dispatchers(source).transformPrompt(promptRequest()))
        assertEquals(listOf("hi", "hi|a"), seen)
    }

    @Test
    fun promptTransformFailureKeepsPreviousText() = runTest {
        val failing = probeHooks(onPrompt = { error("boom") })
        val healthy = probeHooks(onPrompt = { "${it.prompt}|ok" })
        val source = TestHookSource(listOf(hookEntry("failing", failing), hookEntry("healthy", healthy)))
        assertEquals("hi|ok", dispatchers(source).transformPrompt(promptRequest()))
        assertEquals(PluginHookStage.PROMPT_TRANSFORM, reporter.failures.single().stage)
        assertEquals("failing", reporter.failures.single().pluginId)
    }

    @Test
    fun promptTransformTimeoutKeepsPreviousText() = runTest {
        val slow = probeHooks(onPrompt = {
            delay(60_000)
            "late"
        })
        val source = TestHookSource(listOf(hookEntry("slow", slow)))
        assertEquals("hi", dispatchers(source, timeoutMs = 100).transformPrompt(promptRequest()))
        assertTrue(reporter.failures.single().message.startsWith("timed out"))
    }

    @Test
    fun contextCollectsInOrderAndTruncatesPerPlugin() = runTest {
        val seen = mutableListOf<String>()
        val first = probeHooks(onContext = { request ->
            seen += request.cwd
            "a".repeat(9_000)
        })
        val second = probeHooks(onContext = { "b".repeat(9_000) })
        val source = TestHookSource(listOf(hookEntry("a", first), hookEntry("b", second)))
        val contributions = dispatchers(source).collectContext(PluginContextRequest("session-1", "/workspace"))
        assertEquals(2, contributions.size)
        assertEquals("a", contributions[0].first)
        assertEquals(8_192, contributions[0].second.length)
        assertEquals(8_192, contributions[1].second.length)
        assertEquals(listOf("/workspace"), seen)
    }

    @Test
    fun contextStopsAtTotalLimit() = runTest {
        val entries = (1..5).map { index ->
            hookEntry("p$index", probeHooks(onContext = { "x".repeat(8_192) }))
        }
        val contributions = dispatchers(TestHookSource(entries))
            .collectContext(PluginContextRequest("session-1", "/workspace"))
        assertEquals(4, contributions.size)
    }

    @Test
    fun contextSkipsFailuresAndTimeouts() = runTest {
        val failing = probeHooks(onContext = { error("boom") })
        val slow = probeHooks(onContext = {
            delay(60_000)
            "late"
        })
        val healthy = probeHooks(onContext = { "ok" })
        val source = TestHookSource(listOf(hookEntry("failing", failing), hookEntry("slow", slow), hookEntry("healthy", healthy)))
        val contributions = dispatchers(source, timeoutMs = 100)
            .collectContext(PluginContextRequest("session-1", "/workspace"))
        assertEquals(listOf("healthy" to "ok"), contributions)
        assertEquals(2, reporter.failures.size)
        assertTrue(reporter.failures.all { it.stage == PluginHookStage.CONTEXT })
    }

    @Test
    fun blankContextIsIgnored() = runTest {
        val hooks = probeHooks(onContext = { "   " })
        assertNull(dispatchers(TestHookSource(listOf(hookEntry("blank", hooks))))
            .collectContext(PluginContextRequest("session-1", "/workspace")).firstOrNull())
    }

    @Test
    fun inputTransformChainsAndHandledShortCircuits() = runTest {
        var thirdCalls = 0
        var secondText: String? = null
        val first = probeHooks(onInput = { inputContinue("${it.text}-a") })
        val second = probeHooks(onInput = { request ->
            secondText = request.text
            inputHandled()
        })
        val third = probeHooks(onInput = {
            thirdCalls += 1
            inputContinue(it.text, it.imagePaths)
        })
        val source = TestHookSource(listOf(hookEntry("a", first), hookEntry("b", second), hookEntry("c", third)))
        assertEquals(inputHandled(), dispatchers(source).transformInput(InputRequest("session-1", "hi")))
        assertEquals("hi-a", secondText)
        assertEquals(0, thirdCalls)
    }

    @Test
    fun inputTransformFailureIsFailOpen() = runTest {
        val failing = probeHooks(onInput = { error("boom") })
        val healthy = probeHooks(onInput = { inputContinue("${it.text}-b") })
        val source = TestHookSource(listOf(hookEntry("failing", failing), hookEntry("healthy", healthy)))
        val decision = dispatchers(source).transformInput(InputRequest("session-1", "hi"))
        assertEquals("hi-b", decision.text)
        assertEquals(PluginHookStage.INPUT_TRANSFORM, reporter.failures.single().stage)
    }

    @Test
    fun inputTransformTimeoutIsFailOpen() = runTest {
        val slow = probeHooks(onInput = {
            delay(60_000)
            inputHandled()
        })
        val source = TestHookSource(listOf(hookEntry("slow", slow)))
        val decision = dispatchers(source, timeoutMs = 100).transformInput(InputRequest("session-1", "hi"))
        assertEquals("hi", decision.text)
    }

    @Test
    fun toolGateBlocksOnFirstBlock() = runTest {
        var afterCalls = 0
        val blocking = probeHooks(onBefore = { toolBlock("danger") })
        val after = probeHooks(onBefore = {
            afterCalls += 1
            toolContinue()
        })
        val source = TestHookSource(listOf(hookEntry("blocking", blocking), hookEntry("after", after)))
        assertEquals(toolBlock("danger"), dispatchers(source).beforeToolCall(toolCallRequest()))
        assertEquals(0, afterCalls)
    }

    @Test
    fun toolGateRewritesArgsForLaterPlugins() = runTest {
        val seen = mutableListOf<JsonObject>()
        val rewriter = probeHooks(onBefore = { toolRewrite(buildJsonObject { put("x", 2) }) })
        val observer = probeHooks(onBefore = { request ->
            seen += request.args
            toolContinue()
        })
        val source = TestHookSource(listOf(hookEntry("rewriter", rewriter), hookEntry("observer", observer)))
        val request = toolCallRequest(buildJsonObject { put("x", 1) })
        val decision = dispatchers(source).beforeToolCall(request)
        assertEquals(2, seen.single().getValue("x").jsonPrimitive.int)
        assertTrue(decision.action == ToolCallAction.REWRITE)
        assertEquals(2, decision.args!!.getValue("x").jsonPrimitive.int)
    }

    @Test
    fun toolGateExceptionFailsClosed() = runTest {
        var afterCalls = 0
        val failing = probeHooks(onBefore = { error("boom") })
        val after = probeHooks(onBefore = {
            afterCalls += 1
            toolContinue()
        })
        val source = TestHookSource(listOf(hookEntry("failing", failing), hookEntry("after", after)))
        val decision = dispatchers(source).beforeToolCall(toolCallRequest())
        assertTrue(decision.action == ToolCallAction.BLOCK)
        assertEquals("plugin failing failed: boom", decision.reason.orEmpty())
        assertEquals(0, afterCalls)
        assertEquals(PluginHookStage.BEFORE_TOOL_CALL, reporter.failures.single().stage)
    }

    @Test
    fun toolGateTimeoutFailsClosed() = runTest {
        val slow = probeHooks(onBefore = {
            delay(60_000)
            toolContinue()
        })
        val source = TestHookSource(listOf(hookEntry("slow", slow)))
        val decision = dispatchers(source, timeoutMs = 100).beforeToolCall(toolCallRequest())
        assertTrue(decision.action == ToolCallAction.BLOCK)
        assertTrue(decision.reason.orEmpty().contains("timed out"))
    }

    @Test
    fun toolResultReplacementsChain() = runTest {
        val seen = mutableListOf<String>()
        val first = probeHooks(onAfter = { request ->
            seen += request.result.text.orEmpty()
            resultReplace(textResult("first"))
        })
        val second = probeHooks(onAfter = { request ->
            seen += request.result.text.orEmpty()
            resultReplace(textResult("second"))
        })
        val source = TestHookSource(listOf(hookEntry("a", first), hookEntry("b", second)))
        val decision = dispatchers(source).afterToolExecution(toolExecution())
        assertEquals(resultReplace(textResult("second")), decision)
        assertEquals(listOf("original", "first"), seen)
    }

    @Test
    fun toolResultFailureKeepsPreviousResult() = runTest {
        val failing = probeHooks(onAfter = { error("boom") })
        val source = TestHookSource(listOf(hookEntry("failing", failing)))
        assertEquals(resultKeep(), dispatchers(source).afterToolExecution(toolExecution()))
        assertEquals(PluginHookStage.AFTER_TOOL_EXECUTION, reporter.failures.single().stage)
    }

    @Test
    fun toolResultTimeoutIsFailOpen() = runTest {
        val slow = probeHooks(onAfter = {
            delay(60_000)
            resultReplace(textResult("late"))
        })
        val source = TestHookSource(listOf(hookEntry("slow", slow)))
        assertEquals(resultKeep(), dispatchers(source, timeoutMs = 100).afterToolExecution(toolExecution()))
    }

    @Test
    fun sourceSnapshotIsReadOncePerDispatch() = runTest {
        val source = TestHookSource(listOf(hookEntry("a", probeHooks(onPrompt = { "${it.prompt}!" }))))
        val dispatcher = dispatchers(source)
        dispatcher.transformPrompt(promptRequest())
        dispatcher.transformInput(InputRequest("session-1", "hi"))
        dispatcher.beforeToolCall(toolCallRequest())
        dispatcher.afterToolExecution(toolExecution())
        assertEquals(4, source.reads)
    }

    @Test
    fun noHooksKeepsIdentity() = runTest {
        val dispatcher = dispatchers(TestHookSource())
        assertEquals("hi", dispatcher.transformPrompt(promptRequest()))
        assertEquals(inputContinue("hi"), dispatcher.transformInput(InputRequest("session-1", "hi")))
        assertEquals(toolContinue(), dispatcher.beforeToolCall(toolCallRequest()))
        assertEquals(resultKeep(), dispatcher.afterToolExecution(toolExecution()))
        assertTrue(reporter.failures.isEmpty())
    }
}
