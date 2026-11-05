package io.github.whywood.deepdeep.runtime

import dev.pi.agent.AgentToolResult
import dev.pi.agent.AgentToolUpdateCallback
import dev.pi.ai.RunSignal
import dev.pi.ai.TextContent
import dev.pi.ai.auth.Credential
import dev.pi.ai.auth.CredentialInfo
import dev.pi.ai.auth.CredentialStore
import dev.pi.ai.http.HttpRequest
import dev.pi.ai.http.HttpResponse
import dev.pi.ai.http.HttpTransport
import dev.pi.coding.session.CodingHooks
import dev.pi.coding.session.InputDecision
import dev.pi.coding.session.InputRequest
import dev.pi.coding.tools.CodingTool
import dev.pi.coding.tools.textResult
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okio.FileSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginRuntimeHooksTest {
    private val transport = object : HttpTransport {
        override suspend fun execute(request: HttpRequest): HttpResponse = error("network is not used")
    }

    private val credentials = object : CredentialStore {
        override fun read(providerId: String): Credential? = null
        override fun readAll(): Map<String, Credential> = emptyMap()
        override fun readApiKey(providerId: String): String? = null
        override fun readEnv(providerId: String): Map<String, String>? = null
        override fun list(): List<CredentialInfo> = emptyList()
        override fun keys(): List<String> = emptyList()
        override fun modify(providerId: String, transform: (Credential?) -> Credential?): Credential? = null
        override fun writeApiKey(providerId: String, key: String, env: Map<String, String>?) = Unit
        override fun write(providerId: String, credential: Credential) = Unit
        override fun writeRawCredential(providerId: String, credential: JsonElement) = Unit
        override fun delete(providerId: String) = Unit
        override fun readRaw(): Map<String, JsonElement> = emptyMap()
    }

    private fun sampleTool(
        name: String = "demo",
        execute: suspend (String, JsonObject, RunSignal?, AgentToolUpdateCallback?) -> AgentToolResult,
    ): CodingTool = CodingTool(
        name = name,
        description = "$name description",
        parameters = buildJsonObject { put("type", "object") },
        label = "$name label",
        promptSnippet = "$name snippet",
        promptGuidelines = listOf("$name guideline"),
        prepareArguments = { args -> args },
        execute = execute,
    )

    private fun AgentToolResult.text(): String =
        content.filterIsInstance<TextContent>().joinToString("") { it.text }

    @Test
    fun nullHooksReturnSameToolAndDelegateExactly() = runTest {
        val result = textResult("ok")
        val underlying = sampleTool { _, _, _, _ -> result }
        val wrapped = underlying.withExecutionHooks(null)
        assertSame(underlying, wrapped)
        assertSame(result, wrapped.execute("call-1", buildJsonObject { put("x", 1) }, null, null))
    }

    @Test
    fun hooksPreserveToolMetadata() {
        val underlying = sampleTool { _, _, _, _ -> textResult("ok") }
        val wrapped = underlying.withExecutionHooks(object : ToolExecutionHooks {})
        assertEquals(underlying.name, wrapped.name)
        assertEquals(underlying.description, wrapped.description)
        assertSame(underlying.parameters, wrapped.parameters)
        assertEquals(underlying.label, wrapped.label)
        assertEquals(underlying.promptSnippet, wrapped.promptSnippet)
        assertEquals(underlying.promptGuidelines, wrapped.promptGuidelines)
        assertSame(underlying.prepareArguments, wrapped.prepareArguments)
    }

    @Test
    fun blockReturnsErrorResultWithoutExecutingTool() = runTest {
        var executions = 0
        val wrapped = sampleTool { _, _, _, _ ->
            executions += 1
            textResult("executed")
        }.withExecutionHooks(object : ToolExecutionHooks {
            override suspend fun beforeToolCall(context: ToolCallContext): ToolCallDecision {
                assertEquals("demo", context.toolName)
                assertEquals("call-1", context.callId)
                assertEquals(1, context.args.getValue("x").jsonPrimitive.int)
                return ToolCallDecision.Block("blocked by plugin")
            }
        })
        val result = wrapped.execute("call-1", buildJsonObject { put("x", 1) }, null, null)
        assertEquals(0, executions)
        assertEquals(true, result.isError)
        assertEquals("blocked by plugin", result.text())
    }

    @Test
    fun rewriteRunsToolAndAfterHookWithNewArguments() = runTest {
        val executedArgs = mutableListOf<JsonObject>()
        val afterArgs = mutableListOf<JsonObject>()
        val wrapped = sampleTool { _, args, _, _ ->
            executedArgs += args
            textResult("done")
        }.withExecutionHooks(object : ToolExecutionHooks {
            override suspend fun beforeToolCall(context: ToolCallContext): ToolCallDecision =
                ToolCallDecision.Rewrite(buildJsonObject { put("x", 2) })

            override suspend fun afterToolResult(context: ToolResultContext): ToolResultDecision {
                afterArgs += context.args
                return ToolResultDecision.Keep
            }
        })
        val result = wrapped.execute("call-1", buildJsonObject { put("x", 1) }, null, null)
        assertEquals(2, executedArgs.single().getValue("x").jsonPrimitive.int)
        assertEquals(2, afterArgs.single().getValue("x").jsonPrimitive.int)
        assertEquals("done", result.text())
    }

    @Test
    fun continueRunsToolWithOriginalArguments() = runTest {
        val executedArgs = mutableListOf<JsonObject>()
        val wrapped = sampleTool { _, args, _, _ ->
            executedArgs += args
            textResult("done")
        }.withExecutionHooks(object : ToolExecutionHooks {
            override suspend fun beforeToolCall(context: ToolCallContext): ToolCallDecision =
                ToolCallDecision.Continue
        })
        wrapped.execute("call-1", buildJsonObject { put("x", 7) }, null, null)
        assertEquals(7, executedArgs.single().getValue("x").jsonPrimitive.int)
    }

    @Test
    fun afterHookReplacesResult() = runTest {
        val original = textResult("original")
        val replacement = textResult("replacement")
        val wrapped = sampleTool { _, _, _, _ -> original }.withExecutionHooks(object : ToolExecutionHooks {
            override suspend fun afterToolResult(context: ToolResultContext): ToolResultDecision {
                assertEquals("original", context.result.text())
                return ToolResultDecision.Replace(replacement)
            }
        })
        assertSame(replacement, wrapped.execute("call-1", buildJsonObject { put("x", 1) }, null, null))
    }

    @Test
    fun afterHookKeepReturnsOriginalResult() = runTest {
        val original = textResult("original")
        val wrapped = sampleTool { _, _, _, _ -> original }.withExecutionHooks(object : ToolExecutionHooks {})
        assertSame(original, wrapped.execute("call-1", buildJsonObject { put("x", 1) }, null, null))
    }

    @Test
    fun signalAndUpdateCallbackReachUnderlyingTool() = runTest {
        val signal = RunSignal()
        val update: AgentToolUpdateCallback = {}
        var seenSignal: RunSignal? = null
        var seenUpdate: AgentToolUpdateCallback? = null
        val wrapped = sampleTool { _, _, receivedSignal, receivedUpdate ->
            seenSignal = receivedSignal
            seenUpdate = receivedUpdate
            textResult("done")
        }.withExecutionHooks(object : ToolExecutionHooks {})
        wrapped.execute("call-1", buildJsonObject { put("x", 1) }, signal, update)
        assertSame(signal, seenSignal)
        assertSame(update, seenUpdate)
    }

    @Test
    fun withPluginHooksReturnsSameHooksWhenNothingToAdd() {
        val hooks = CodingHooks(transformPrompt = { "base:$it" })
        assertSame(hooks, hooks.withPluginHooks(null, null))
    }

    @Test
    fun pluginPromptTransformRunsAfterExistingTransform() = runTest {
        val calls = mutableListOf<String>()
        val base = CodingHooks(transformPrompt = { text ->
            calls += "base"
            "$text/base"
        })
        val composed = base.withPluginHooks(
            promptTransform = { text ->
                calls += "plugin"
                "$text/plugin"
            },
            inputHandler = null,
        )
        assertEquals("hi/base/plugin", composed.transformPrompt!!("hi"))
        assertEquals(listOf("base", "plugin"), calls)
        assertNull(composed.onInput)
    }

    @Test
    fun pluginPromptTransformWithoutExistingTransform() = runTest {
        val composed = CodingHooks().withPluginHooks(promptTransform = { "plugin:$it" }, inputHandler = null)
        assertEquals("plugin:hi", composed.transformPrompt!!("hi"))
    }

    @Test
    fun withPluginHooksKeepsExtraSystemPromptSections() = runTest {
        val sections: suspend () -> Map<String, String> = { mapOf("custom" to "value") }
        val base = CodingHooks(extraSystemPromptSections = sections)
        val composed = base.withPluginHooks(promptTransform = { it }, inputHandler = null)
        assertEquals(mapOf("custom" to "value"), composed.extraSystemPromptSections!!())
    }

    @Test
    fun pluginInputHandlerReceivesRequest() = runTest {
        val received = mutableListOf<InputRequest>()
        val composed = CodingHooks().withPluginHooks(
            promptTransform = null,
            inputHandler = { request ->
                received += request
                InputDecision.Handled
            },
        )
        assertEquals(InputDecision.Handled, composed.onInput!!(InputRequest("hello")))
        assertEquals("hello", received.single().text)
    }

    @Test
    fun existingInputContinueFeedsPluginHandler() = runTest {
        val received = mutableListOf<String>()
        val base = CodingHooks(onInput = { request -> InputDecision.Continue("${request.text}/base") })
        val composed = base.withPluginHooks(
            promptTransform = null,
            inputHandler = { request ->
                received += request.text
                InputDecision.Continue("${request.text}/plugin")
            },
        )
        val decision = composed.onInput!!(InputRequest("hi"))
        assertEquals("hi/base/plugin", (decision as InputDecision.Continue).text)
        assertEquals("hi/base", received.single())
    }

    @Test
    fun existingInputHandledSkipsPluginHandler() = runTest {
        var pluginCalls = 0
        val base = CodingHooks(onInput = { InputDecision.Handled })
        val composed = base.withPluginHooks(
            promptTransform = null,
            inputHandler = {
                pluginCalls += 1
                InputDecision.Handled
            },
        )
        assertEquals(InputDecision.Handled, composed.onInput!!(InputRequest("hi")))
        assertEquals(0, pluginCalls)
    }

    @Test
    fun engineAppliesPluginHooksToSessionAndTools() = runTest {
        val root = Files.createTempDirectory("deepdeep-plugin-hooks").toFile()
        val workspace = File(root, "workspace").apply { mkdirs() }
        val agentDir = File(root, "agent").apply { mkdirs() }
        val blocked = object : ToolExecutionHooks {
            override suspend fun beforeToolCall(context: ToolCallContext): ToolCallDecision =
                if (context.toolName == "read") ToolCallDecision.Block("blocked by plugin") else ToolCallDecision.Continue
        }
        try {
            val engine = PiSessionEngine(
                workspace = workspace,
                agentDir = agentDir,
                credentials = credentials,
                transport = transport,
                shellExecutor = null,
                providers = ProviderCatalog(transport, credentials),
                promptTransform = { "plugin:$it" },
                inputHandler = { request -> InputDecision.Continue("${request.text}/plugin") },
                toolHooks = blocked,
                fs = FileSystem.SYSTEM,
            )
            val session = engine.createSession(
                file = null,
                config = null,
                shellEnabled = false,
                mcpEnabled = false,
            )
            try {
                assertEquals("plugin:hello", session.hooks.transformPrompt!!("hello"))
                val decision = session.hooks.onInput!!(InputRequest("hello"))
                assertEquals("hello/plugin", (decision as InputDecision.Continue).text)
            } finally {
                session.close()
            }
            val read = engine.availableTools().first { tool -> tool.name == "read" }
            val result = read.execute(
                "call-1",
                buildJsonObject { put("path", File(workspace, "notes.txt").absolutePath) },
                null,
                null,
            )
            assertEquals(true, result.isError)
            assertEquals("blocked by plugin", result.text())
        } finally {
            root.deleteRecursively()
        }
    }
}
