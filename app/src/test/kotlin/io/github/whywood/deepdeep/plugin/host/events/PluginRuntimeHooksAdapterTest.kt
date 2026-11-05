package io.github.whywood.deepdeep.plugin.host.events

import dev.pi.ai.ImageContent
import dev.pi.ai.TextContent
import dev.pi.coding.session.InputDecision as SdkInputDecision
import dev.pi.coding.session.InputRequest as SdkInputRequest
import io.github.whywood.deepdeep.plugin.api.InputDecision as PluginInputDecision
import io.github.whywood.deepdeep.plugin.api.PluginEvent
import io.github.whywood.deepdeep.plugin.api.PluginToolResult
import io.github.whywood.deepdeep.plugin.api.PromptRequest
import io.github.whywood.deepdeep.plugin.api.ToolCallDecision as PluginToolCallDecision
import io.github.whywood.deepdeep.plugin.api.ToolCallRequest as PluginToolCallRequest
import io.github.whywood.deepdeep.plugin.api.ToolExecutionResult as PluginToolExecutionResult
import io.github.whywood.deepdeep.plugin.api.ToolResultDecision as PluginToolResultDecision
import io.github.whywood.deepdeep.runtime.ToolCallContext
import io.github.whywood.deepdeep.runtime.ToolCallDecision as RuntimeToolCallDecision
import io.github.whywood.deepdeep.runtime.ToolResultContext
import io.github.whywood.deepdeep.runtime.ToolResultDecision as RuntimeToolResultDecision
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginRuntimeHooksAdapterTest {
    private val session = PluginHookSession("session-1", "/workspace", "/workspace/session.jsonl")

    private val reporter = RecordingReporter()

    private fun adapter(source: PluginHookSource, timeoutMs: Long = 1_000): PluginRuntimeHooksAdapter =
        PluginRuntimeHooksAdapter(
            session = session,
            source = source,
            reporter = reporter,
            observeTimeoutMs = timeoutMs,
            transformTimeoutMs = timeoutMs,
        )

    @Test
    fun promptTransformCarriesSessionId() = runTest {
        val seen = mutableListOf<PromptRequest>()
        val hooks = probeHooks(onPrompt = { request ->
            seen += request
            "plugin:${request.prompt}"
        })
        val result = adapter(TestHookSource(listOf(hookEntry("p", hooks)))).promptTransform("hi")
        assertEquals("plugin:hi", result)
        assertEquals(PromptRequest("session-1", "hi"), seen.single())
    }

    @Test
    fun inputHandlerMapsContinueAndKeepsImages() = runTest {
        val images = listOf(ImageContent("aGk=", "image/png"))
        val request = SdkInputRequest("hi", images)
        val hooks = probeHooks(onInput = { pluginRequest -> PluginInputDecision.Continue("${pluginRequest.text}!") })
        val decision = adapter(TestHookSource(listOf(hookEntry("p", hooks)))).inputHandler(request)
        assertTrue(decision is SdkInputDecision.Continue)
        val continued = decision as SdkInputDecision.Continue
        assertEquals("hi!", continued.text)
        assertSame(images, continued.images)
    }

    @Test
    fun inputHandlerMapsHandled() = runTest {
        val hooks = probeHooks(onInput = { PluginInputDecision.Handled })
        val request = SdkInputRequest("hi")
        assertEquals(SdkInputDecision.Handled, adapter(TestHookSource(listOf(hookEntry("p", hooks)))).inputHandler(request))
    }

    @Test
    fun toolBeforeMapsRewrite() = runTest {
        val seen = mutableListOf<PluginToolCallRequest>()
        val hooks = probeHooks(onBefore = { request ->
            seen += request
            PluginToolCallDecision.Rewrite(buildJsonObject { put("x", 2) })
        })
        val context = ToolCallContext("bash", "call-1", buildJsonObject { put("x", 1) })
        val decision = adapter(TestHookSource(listOf(hookEntry("p", hooks)))).toolHooks.beforeToolCall(context)
        assertTrue(decision is RuntimeToolCallDecision.Rewrite)
        assertEquals(2, (decision as RuntimeToolCallDecision.Rewrite).args.getValue("x").jsonPrimitive.int)
        assertEquals("session-1", seen.single().sessionId)
        assertEquals("bash", seen.single().toolName)
        assertEquals("call-1", seen.single().callId)
    }

    @Test
    fun toolBeforeMapsBlock() = runTest {
        val hooks = probeHooks(onBefore = { PluginToolCallDecision.Block("danger") })
        val context = ToolCallContext("bash", "call-1", JsonObject(emptyMap()))
        val decision = adapter(TestHookSource(listOf(hookEntry("p", hooks)))).toolHooks.beforeToolCall(context)
        assertEquals(RuntimeToolCallDecision.Block("danger"), decision)
    }

    @Test
    fun toolBeforeFailureBecomesBlock() = runTest {
        val hooks = probeHooks(onBefore = { error("boom") })
        val context = ToolCallContext("bash", "call-1", JsonObject(emptyMap()))
        val decision = adapter(TestHookSource(listOf(hookEntry("failing", hooks)))).toolHooks.beforeToolCall(context)
        assertTrue(decision is RuntimeToolCallDecision.Block)
        assertEquals("plugin failing failed: boom", (decision as RuntimeToolCallDecision.Block).reason)
        assertEquals(PluginHookStage.BEFORE_TOOL_CALL, reporter.failures.single().stage)
    }

    @Test
    fun toolAfterConvertsPluginResultToRuntimeResult() = runTest {
        val seen = mutableListOf<PluginToolResult>()
        val hooks = probeHooks(onAfter = { request ->
            seen += request.result
            PluginToolResultDecision.Replace(PluginToolResult.Text("replaced", isError = true))
        })
        val context = ToolResultContext("bash", "call-1", JsonObject(emptyMap()), agentTextResult("original"))
        val decision = adapter(TestHookSource(listOf(hookEntry("p", hooks)))).toolHooks.afterToolResult(context)
        assertTrue(decision is RuntimeToolResultDecision.Replace)
        val result = (decision as RuntimeToolResultDecision.Replace).result
        assertEquals("replaced", (result.content.single() as TextContent).text)
        assertEquals(true, result.isError)
        assertEquals(PluginToolResult.Text("original"), seen.single())
    }

    @Test
    fun toolAfterCarriesSessionIdToPlugins() = runTest {
        val seen = mutableListOf<PluginToolExecutionResult>()
        val hooks = probeHooks(onAfter = { request ->
            seen += request
            PluginToolResultDecision.Keep
        })
        val context = ToolResultContext("bash", "call-1", JsonObject(emptyMap()), agentTextResult("original"))
        assertEquals(
            RuntimeToolResultDecision.Keep,
            adapter(TestHookSource(listOf(hookEntry("p", hooks)))).toolHooks.afterToolResult(context),
        )
        assertEquals("session-1", seen.single().sessionId)
        assertEquals("bash", seen.single().toolName)
        assertEquals("call-1", seen.single().callId)
    }

    @Test
    fun eventsAreDispatchedThroughAdapter() = runTest {
        val hooks = EventRecordingHooks()
        val event = PluginEvent.RunStarted("session-1", "run-1")
        val failures = adapter(TestHookSource(listOf(hookEntry("p", hooks)))).emit(event)
        assertTrue(failures.isEmpty())
        assertEquals(listOf(event), hooks.events)
    }

    @Test
    fun factoryCreatesAdapterBoundToSession() = runTest {
        val hooks = probeHooks(onPrompt = { "plugin:${it.prompt}" })
        val factory = PluginRuntimeHooksFactory(TestHookSource(listOf(hookEntry("p", hooks))), reporter)
        val adapter = factory.create("session-42", "/workspace", "/workspace/session.jsonl")
        assertEquals("session-42", adapter.session.sessionId)
        assertEquals("/workspace", adapter.session.cwd)
        assertEquals("/workspace/session.jsonl", adapter.session.file)
        assertEquals("plugin:hi", adapter.promptTransform("hi"))
    }
}
