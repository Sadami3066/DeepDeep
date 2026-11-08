package io.github.whywood.deepdeep.plugin.host.events

import io.github.whywood.deepdeep.sdk.ai.TextContent
import io.github.whywood.deepdeep.plugin.api.InputDecision as PluginInputDecision
import io.github.whywood.deepdeep.plugin.api.PluginEvent
import io.github.whywood.deepdeep.plugin.api.PluginToolResult
import io.github.whywood.deepdeep.plugin.api.PromptRequest
import io.github.whywood.deepdeep.plugin.api.ToolCallDecision as PluginToolCallDecision
import io.github.whywood.deepdeep.plugin.api.ToolCallRequest as PluginToolCallRequest
import io.github.whywood.deepdeep.plugin.api.ToolExecutionResult as PluginToolExecutionResult
import io.github.whywood.deepdeep.plugin.api.ToolResultDecision as PluginToolResultDecision
import io.github.whywood.deepdeep.runtime.ImagePart
import io.github.whywood.deepdeep.runtime.InputDecision as RuntimeInputDecision
import io.github.whywood.deepdeep.runtime.InputRequest as RuntimeInputRequest
import io.github.whywood.deepdeep.runtime.ToolCallContext
import io.github.whywood.deepdeep.runtime.ToolCallDecision as RuntimeToolCallDecision
import io.github.whywood.deepdeep.runtime.ToolResultContext
import io.github.whywood.deepdeep.runtime.ToolResultDecision as RuntimeToolResultDecision
import java.nio.file.Files
import java.util.Base64
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
    fun systemContextWrapsAndEscapesContributions() = runTest {
        val hooks = probeHooks(onContext = { "a & <b>" })
        val context = adapter(TestHookSource(listOf(hookEntry("p", hooks)))).systemContext()
        assertEquals("<plugin_context plugin=\"p\">\na &amp; &lt;b&gt;\n</plugin_context>", context)
    }

    @Test
    fun systemContextIsNullWhenNoPluginContributes() = runTest {
        val hooks = probeHooks(onContext = { null })
        assertNull(adapter(TestHookSource(listOf(hookEntry("p", hooks)))).systemContext())
    }

    @Test
    fun inputHandlerPassesImagePathsAndKeepsImagesWhenUnchanged() = runTest {
        val images = listOf(ImagePart("aGk=", "image/png", "/tmp/photo.png"))
        val seenPaths = mutableListOf<List<String>>()
        val hooks = probeHooks(onInput = { pluginRequest ->
            seenPaths += pluginRequest.imagePaths
            PluginInputDecision.Continue("${pluginRequest.text}!", pluginRequest.imagePaths)
        })
        val decision = adapter(TestHookSource(listOf(hookEntry("p", hooks))))
            .inputHandler(RuntimeInputRequest("hi", images)) as RuntimeInputDecision.Continue
        assertEquals("hi!", decision.text)
        assertEquals(listOf("/tmp/photo.png"), seenPaths.single())
        assertEquals(images, decision.images)
    }

    @Test
    fun inputHandlerAdoptsReturnedImagePaths() = runTest {
        val file = Files.createTempFile("plugin-input", ".png").toFile().apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }
        val hooks = probeHooks(onInput = { PluginInputDecision.Continue("rewritten", listOf(file.absolutePath)) })
        val decision = adapter(TestHookSource(listOf(hookEntry("p", hooks))))
            .inputHandler(RuntimeInputRequest("hi")) as RuntimeInputDecision.Continue
        assertEquals("rewritten", decision.text)
        val image = decision.images.single()
        assertEquals("image/png", image.mimeType)
        assertEquals(file.absolutePath, image.path)
        assertEquals(Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3)), image.data)
    }

    @Test
    fun inputHandlerDropsImagesWhenPluginReturnsNoPaths() = runTest {
        val hooks = probeHooks(onInput = { PluginInputDecision.Continue("no images") })
        val decision = adapter(TestHookSource(listOf(hookEntry("p", hooks))))
            .inputHandler(RuntimeInputRequest("hi", listOf(ImagePart("aGk=", "image/png", "/tmp/photo.png"))))
            as RuntimeInputDecision.Continue
        assertEquals("no images", decision.text)
        assertTrue(decision.images.isEmpty())
    }

    @Test
    fun inputHandlerSkipsUnreadableReturnedPaths() = runTest {
        val hooks = probeHooks(onInput = { PluginInputDecision.Continue("rewritten", listOf("/no/such/file.png")) })
        val decision = adapter(TestHookSource(listOf(hookEntry("p", hooks))))
            .inputHandler(RuntimeInputRequest("hi")) as RuntimeInputDecision.Continue
        assertTrue(decision.images.isEmpty())
    }

    @Test
    fun inputHandlerMapsHandled() = runTest {
        val hooks = probeHooks(onInput = { PluginInputDecision.Handled })
        assertEquals(
            RuntimeInputDecision.Handled,
            adapter(TestHookSource(listOf(hookEntry("p", hooks)))).inputHandler(RuntimeInputRequest("hi")),
        )
    }

    @Test
    fun inputHandlerFailsOpenWhenPluginThrows() = runTest {
        val hooks = probeHooks(onInput = { error("boom") })
        val images = listOf(ImagePart("aGk=", "image/png", "/tmp/photo.png"))
        val decision = adapter(TestHookSource(listOf(hookEntry("failing", hooks))))
            .inputHandler(RuntimeInputRequest("hi", images)) as RuntimeInputDecision.Continue
        assertEquals("hi", decision.text)
        assertEquals(images, decision.images)
        assertEquals(PluginHookStage.INPUT_TRANSFORM, reporter.failures.single().stage)
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
