package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.sdk.agent.AfterToolCallContext
import io.github.whywood.deepdeep.sdk.agent.AgentToolResult
import io.github.whywood.deepdeep.sdk.agent.BeforeToolCallContext
import io.github.whywood.deepdeep.sdk.ai.TextContent
import io.github.whywood.deepdeep.sdk.ai.auth.Credential
import io.github.whywood.deepdeep.sdk.ai.auth.CredentialInfo
import io.github.whywood.deepdeep.sdk.ai.auth.CredentialStore
import io.github.whywood.deepdeep.sdk.ai.http.HttpRequest
import io.github.whywood.deepdeep.sdk.ai.http.HttpResponse
import io.github.whywood.deepdeep.sdk.ai.http.HttpTransport
import io.github.whywood.deepdeep.sdk.coding.session.CodingHooks
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    private fun assistantMessage(): io.github.whywood.deepdeep.sdk.ai.AssistantMessage = io.github.whywood.deepdeep.sdk.ai.AssistantMessage(
        content = mutableListOf(),
        api = "test",
        provider = "test",
        model = "test",
        usage = io.github.whywood.deepdeep.sdk.ai.Usage.empty(),
        stopReason = io.github.whywood.deepdeep.sdk.ai.StopReason.Stop,
        timestamp = 1L,
    )

    private fun beforeContext(args: JsonObject = buildJsonObject { put("x", 1) }): BeforeToolCallContext =
        BeforeToolCallContext(
            assistantMessage = assistantMessage(),
            toolCall = io.github.whywood.deepdeep.sdk.ai.ToolCall("call-1", "demo", args),
            args = LinkedHashMap(args),
            context = io.github.whywood.deepdeep.sdk.agent.AgentContext(emptyList(), emptyList()),
        )

    private fun afterContext(
        result: AgentToolResult = AgentToolResult(content = listOf(TextContent("raw"))),
    ): AfterToolCallContext = AfterToolCallContext(
        assistantMessage = assistantMessage(),
        toolCall = io.github.whywood.deepdeep.sdk.ai.ToolCall("call-1", "demo", buildJsonObject { put("x", 1) }),
        args = LinkedHashMap(buildJsonObject { put("x", 1) }),
        result = result,
        isError = false,
        context = io.github.whywood.deepdeep.sdk.agent.AgentContext(emptyList(), emptyList()),
    )

    private fun AgentToolResult.text(): String =
        content.filterIsInstance<TextContent>().joinToString("") { it.text }

    @Test
    fun beforeBridgeContinuesToChainedHook() = runTest {
        val calls = mutableListOf<String>()
        val bridge = bridgeBeforeToolCall(
            object : ToolExecutionHooks {
                override suspend fun beforeToolCall(context: ToolCallContext): ToolCallDecision {
                    calls += "plugin:${context.toolName}"
                    return ToolCallDecision.Continue
                }
            },
            chained = { _, _ ->
                calls += "chained"
                null
            },
        )
        bridge(beforeContext(), null)
        assertEquals(listOf("plugin:demo", "chained"), calls)
    }

    @Test
    fun beforeBridgeBlockReturnsReasonWithoutRunningChain() = runTest {
        var chained = 0
        val bridge = bridgeBeforeToolCall(
            object : ToolExecutionHooks {
                override suspend fun beforeToolCall(context: ToolCallContext): ToolCallDecision =
                    ToolCallDecision.Block("blocked by plugin")
            },
            chained = { _, _ ->
                chained += 1
                null
            },
        )
        val result = bridge(beforeContext(), null)
        assertEquals("blocked by plugin", result?.reason)
        assertEquals(true, result?.block)
        assertEquals(0, chained)
    }

    @Test
    fun beforeBridgeRewriteMutatesArgsSeenByTool() = runTest {
        val bridge = bridgeBeforeToolCall(
            object : ToolExecutionHooks {
                override suspend fun beforeToolCall(context: ToolCallContext): ToolCallDecision =
                    ToolCallDecision.Rewrite(buildJsonObject { put("x", 42) })
            },
            chained = null,
        )
        val context = beforeContext()
        bridge(context, null)
        assertEquals(42, context.args["x"]?.jsonPrimitive?.int)
    }

    @Test
    fun afterBridgeKeepsChainedResult() = runTest {
        val bridge = bridgeAfterToolCall(
            object : ToolExecutionHooks {},
            chained = { _, _ -> io.github.whywood.deepdeep.sdk.agent.AfterToolCallResult(content = listOf(TextContent("normalized"))) },
        )
        val result = bridge(afterContext(), null)
        assertEquals("normalized", (result?.content?.first() as TextContent).text)
    }

    @Test
    fun afterBridgeSeesNormalizedResultAndReplacesIt() = runTest {
        val seen = mutableListOf<String>()
        val bridge = bridgeAfterToolCall(
            object : ToolExecutionHooks {
                override suspend fun afterToolResult(context: ToolResultContext): ToolResultDecision {
                    seen += context.result.text()
                    return ToolResultDecision.Replace(
                        AgentToolResult(content = listOf(TextContent("replaced")), isError = true),
                    )
                }
            },
            chained = { _, _ -> io.github.whywood.deepdeep.sdk.agent.AfterToolCallResult(content = listOf(TextContent("normalized"))) },
        )
        val result = bridge(afterContext(), null)
        assertEquals(listOf("normalized"), seen)
        assertEquals("replaced", (result?.content?.first() as TextContent).text)
        assertEquals(true, result?.isError)
    }

    @Test
    fun afterBridgeWithoutChainReturnsPluginResult() = runTest {
        val bridge = bridgeAfterToolCall(
            object : ToolExecutionHooks {
                override suspend fun afterToolResult(context: ToolResultContext): ToolResultDecision =
                    ToolResultDecision.Replace(AgentToolResult(content = listOf(TextContent("plugin"))))
            },
            chained = null,
        )
        val result = bridge(afterContext(), null)
        assertEquals("plugin", (result?.content?.first() as TextContent).text)
    }

    @Test
    fun afterBridgeKeepsOriginalWhenPluginKeeps() = runTest {
        val bridge = bridgeAfterToolCall(object : ToolExecutionHooks {}, chained = null)
        assertNull(bridge(afterContext(), null))
    }

    @Test
    fun withPluginHooksReturnsSameHooksWhenNothingToAdd() {
        val hooks = CodingHooks(transformPrompt = { "base:$it" })
        assertSame(hooks, hooks.withPluginHooks(null))
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
        )
        assertEquals("hi/base/plugin", composed.transformPrompt!!("hi"))
        assertEquals(listOf("base", "plugin"), calls)
        assertNull(composed.onInput)
    }

    @Test
    fun pluginPromptTransformWithoutExistingTransform() = runTest {
        val composed = CodingHooks().withPluginHooks(promptTransform = { "plugin:$it" })
        assertEquals("plugin:hi", composed.transformPrompt!!("hi"))
    }

    @Test
    fun withPluginHooksKeepsExtraSystemPromptSections() = runTest {
        val sections: suspend () -> Map<String, String> = { mapOf("custom" to "value") }
        val base = CodingHooks(extraSystemPromptSections = sections)
        val composed = base.withPluginHooks(promptTransform = { it })
        assertEquals(mapOf("custom" to "value"), composed.extraSystemPromptSections!!())
    }

    @Test
    fun engineAppliesInputHooksAndAgentLevelToolHooks() = runTest {
        val root = Files.createTempDirectory("deepdeep-plugin-hooks").toFile()
        val workspace = File(root, "workspace").apply { mkdirs() }
        val agentDir = File(root, "agent").apply { mkdirs() }
        val seenImages = mutableListOf<List<ImagePart>>()
        val image = ImagePart("aGVsbG8=", "image/png", "/tmp/photo.png")
        val runtimeHooks = RuntimeHooks(
            promptTransform = { "plugin:$it" },
            inputHandler = { request ->
                seenImages += request.images
                InputDecision.Continue("${request.text}/plugin", request.images)
            },
            toolHooks = object : ToolExecutionHooks {},
        )
        try {
            val engine = PiSessionEngine(
                workspace = workspace,
                agentDir = agentDir,
                credentials = credentials,
                transport = transport,
                shellExecutor = null,
                providers = ProviderCatalog(transport, credentials),
                fs = FileSystem.SYSTEM,
                hooks = runtimeHooks,
            )
            val session = engine.createSession(
                file = null,
                config = null,
                shellEnabled = false,
                mcpEnabled = false,
            )
            try {
                assertEquals("plugin:hello", session.hooks.transformPrompt!!("hello"))
                assertNotNull(session.agent.beforeToolCall)
                assertNotNull(session.agent.afterToolCall)
                val prepared = engine.prepareInput("hello", listOf(image)) as PreparedPrompt.Ready
                assertEquals("hello/plugin", prepared.text)
                assertEquals(listOf(image), seenImages.single())
                assertEquals(listOf(image), prepared.images)
            } finally {
                session.close()
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun engineBindsCreatedSessionAsSessionBinding() = runTest {
        val root = Files.createTempDirectory("deepdeep-plugin-binding").toFile()
        val workspace = File(root, "workspace").apply { mkdirs() }
        val agentDir = File(root, "agent").apply { mkdirs() }
        val bound = mutableListOf<SessionBinding>()
        try {
            val engine = PiSessionEngine(
                workspace = workspace,
                agentDir = agentDir,
                credentials = credentials,
                transport = transport,
                shellExecutor = null,
                providers = ProviderCatalog(transport, credentials),
                fs = FileSystem.SYSTEM,
                hooks = RuntimeHooks(onSessionBound = { binding -> bound += binding }),
            )
            val session = engine.createSession(
                file = null,
                config = null,
                shellEnabled = false,
                mcpEnabled = false,
            )
            try {
                val binding = bound.single()
                assertTrue(binding.supportsChatDelivery)
                val snapshot = binding.current()
                assertNotNull(snapshot)
                assertEquals(session.sessionId, snapshot!!.sessionId)
                assertEquals(workspace.absolutePath, snapshot.cwd)
            } finally {
                session.close()
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun engineInputHookHandledStopsPrompt() = runTest {
        val root = Files.createTempDirectory("deepdeep-plugin-handled").toFile()
        val workspace = File(root, "workspace").apply { mkdirs() }
        val agentDir = File(root, "agent").apply { mkdirs() }
        try {
            val engine = PiSessionEngine(
                workspace = workspace,
                agentDir = agentDir,
                credentials = credentials,
                transport = transport,
                shellExecutor = null,
                providers = ProviderCatalog(transport, credentials),
                fs = FileSystem.SYSTEM,
                hooks = RuntimeHooks(inputHandler = { InputDecision.Handled }),
            )
            assertEquals(PreparedPrompt.Handled, engine.prepareInput("drop me", emptyList()))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun engineWithoutHooksLeavesAgentHooksUntouchedAndPassesInputThrough() = runTest {
        val root = Files.createTempDirectory("deepdeep-plugin-no-hooks").toFile()
        val workspace = File(root, "workspace").apply { mkdirs() }
        val agentDir = File(root, "agent").apply { mkdirs() }
        val image = ImagePart("aGVsbG8=", "image/png", "/tmp/photo.png")
        try {
            val engine = PiSessionEngine(
                workspace = workspace,
                agentDir = agentDir,
                credentials = credentials,
                transport = transport,
                shellExecutor = null,
                providers = ProviderCatalog(transport, credentials),
                fs = FileSystem.SYSTEM,
            )
            val session = engine.createSession(
                file = null,
                config = null,
                shellEnabled = false,
                mcpEnabled = false,
            )
            try {
                val prepared = engine.prepareInput("hello", listOf(image))
                assertEquals(PreparedPrompt.Ready("hello", listOf(image)), prepared)
                assertNull(session.agent.beforeToolCall)
            } finally {
                session.close()
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun displayArgumentsKeepScalarText() {
        val args = buildJsonObject {
            put("command", "ls -la")
            put("limit", 5)
        }
        assertEquals(mapOf("command" to "ls -la", "limit" to "5"), args.toDisplayArguments())
        assertFalse(args.isEmpty())
    }
}
