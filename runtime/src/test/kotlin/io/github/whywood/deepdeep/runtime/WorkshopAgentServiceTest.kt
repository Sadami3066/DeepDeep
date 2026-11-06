package io.github.whywood.deepdeep.runtime

import dev.pi.agent.AgentEvent
import dev.pi.agent.AgentMessage
import dev.pi.agent.AgentToolResult
import dev.pi.ai.AssistantMessage
import dev.pi.ai.StopReason
import dev.pi.ai.TextDeltaEvent
import dev.pi.ai.ThinkingDeltaEvent
import dev.pi.ai.Usage
import dev.pi.ai.auth.Credential
import dev.pi.ai.auth.CredentialInfo
import dev.pi.ai.auth.CredentialStore
import dev.pi.ai.http.HttpRequest
import dev.pi.ai.http.HttpResponse
import dev.pi.ai.http.HttpTransport
import dev.pi.coding.prompt.Skill
import dev.pi.coding.session.SessionEvent
import dev.pi.coding.tools.CodingTool
import java.io.File
import dev.pi.ai.TextContent
import dev.pi.coding.tools.ShellExecutor
import dev.pi.coding.tools.ShellResult
import dev.pi.ai.RunSignal
import dev.pi.coding.tools.textResult
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.Buffer
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkshopAgentServiceTest {
    private val transport = object : HttpTransport {
        override suspend fun execute(request: HttpRequest): HttpResponse =
            error("network is not used by the catalog")
    }

    private val credentials = object : CredentialStore {
        override fun read(providerId: String): Credential? = dev.pi.ai.auth.ApiKeyCredential(key = "test-key")
        override fun readAll(): Map<String, Credential> = mapOf("deepseek" to dev.pi.ai.auth.ApiKeyCredential(key = "test-key"))
        override fun readApiKey(providerId: String): String? = "test-key"
        override fun readEnv(providerId: String): Map<String, String>? = null
        override fun list(): List<CredentialInfo> = emptyList()
        override fun keys(): List<String> = listOf("deepseek")
        override fun modify(providerId: String, transform: (Credential?) -> Credential?): Credential? = null
        override fun writeApiKey(providerId: String, key: String, env: Map<String, String>?) = Unit
        override fun write(providerId: String, credential: Credential) = Unit
        override fun writeRawCredential(providerId: String, credential: JsonElement) = Unit
        override fun delete(providerId: String) = Unit
        override fun readRaw(): Map<String, JsonElement> = emptyMap()
    }

    @Test
    fun modelsUseOnlyConfiguredProviders() {
        val models = buildWorkshopAgentModels(ProviderCatalog(transport, credentials), setOf("deepseek"))

        assertTrue(models.isNotEmpty())
        assertTrue(models.all { it.providerId == "deepseek" && it.modelId.isNotBlank() && it.label.isNotBlank() })
        assertTrue(models.all { it.thinkingLevels.isNotEmpty() && it.thinkingLevels.first() == "off" })
        assertEquals(models.sortedWith(compareBy({ it.providerId }, { it.modelId })), models)
    }

    @Test
    fun modelsAreEmptyWithoutConfiguredProviders() {
        assertTrue(buildWorkshopAgentModels(ProviderCatalog(transport, credentials), emptySet()).isEmpty())
    }

    @Test
    fun rejectsBlankPrompt() {
        assertRejected { validateWorkshopAgentRequest(request(prompt = "   ")) }
    }

    @Test
    fun rejectsOversizedPrompt() {
        assertRejected { validateWorkshopAgentRequest(request(prompt = "x".repeat(64 * 1024 + 1))) }
        validateWorkshopAgentRequest(request(prompt = "x".repeat(64 * 1024)))
    }

    @Test
    fun rejectsOversizedSystemPrompt() {
        assertRejected { validateWorkshopAgentRequest(request(systemPrompt = "x".repeat(16 * 1024 + 1))) }
        validateWorkshopAgentRequest(request(systemPrompt = "x".repeat(16 * 1024)))
    }

    @Test
    fun rejectsMaxTurnsOutOfRange() {
        assertRejected { validateWorkshopAgentRequest(request(maxTurns = 0)) }
        assertRejected { validateWorkshopAgentRequest(request(maxTurns = 65)) }
        validateWorkshopAgentRequest(request(maxTurns = 1))
        validateWorkshopAgentRequest(request(maxTurns = 64))
    }

    @Test
    fun maxTurnsDefaultsToSixteen() {
        assertEquals(16, workshopAgentMaxTurns(null))
        assertEquals(64, workshopAgentMaxTurns(64))
        assertEquals(8, workshopAgentMaxTurns(null, 8))
        assertEquals(64, workshopAgentMaxTurns(64, 8))
    }

    @Test
    fun providerAndModelMustComeTogether() {
        assertRejected {
            resolveWorkshopAgentConfig(
                request = request(providerId = "openai"),
                defaultConfig = ModelConfig(),
                models = sampleModels,
                configuredProviders = setOf("openai"),
            )
        }
        assertRejected {
            resolveWorkshopAgentConfig(
                request = request(modelId = "gpt"),
                defaultConfig = ModelConfig(),
                models = sampleModels,
                configuredProviders = setOf("openai"),
            )
        }
    }

    @Test
    fun unconfiguredProviderIsRejected() {
        assertRejected {
            resolveWorkshopAgentConfig(
                request = request(providerId = "openai", modelId = "gpt"),
                defaultConfig = ModelConfig(),
                models = sampleModels,
                configuredProviders = setOf("deepseek"),
            )
        }
    }

    @Test
    fun unknownModelIsRejected() {
        assertRejected {
            resolveWorkshopAgentConfig(
                request = request(providerId = "deepseek", modelId = "missing"),
                defaultConfig = ModelConfig(),
                models = sampleModels,
                configuredProviders = setOf("deepseek"),
            )
        }
    }

    @Test
    fun unsupportedThinkingLevelIsRejected() {
        assertRejected {
            resolveWorkshopAgentConfig(
                request = request(providerId = "deepseek", modelId = "chat", thinkingLevel = "high"),
                defaultConfig = ModelConfig(),
                models = sampleModels,
                configuredProviders = setOf("deepseek"),
            )
        }
    }

    @Test
    fun explicitSelectionResolvesConfig() {
        val config = resolveWorkshopAgentConfig(
            request = request(providerId = "openai", modelId = "gpt", thinkingLevel = "high"),
            defaultConfig = ModelConfig(),
            models = sampleModels,
            configuredProviders = setOf("deepseek", "openai"),
        )

        assertEquals(ModelConfig("openai", "gpt", ThinkingLevel.HIGH), config)
    }

    @Test
    fun missingThinkingLevelKeepsDefaultLevel() {
        val config = resolveWorkshopAgentConfig(
            request = request(providerId = "openai", modelId = "gpt"),
            defaultConfig = ModelConfig("deepseek", "chat", ThinkingLevel.LOW),
            models = sampleModels,
            configuredProviders = setOf("deepseek", "openai"),
        )

        assertEquals(ModelConfig("openai", "gpt", ThinkingLevel.LOW), config)
    }

    @Test
    fun defaultConfigIsUsedWithoutSelection() {
        val defaultConfig = ModelConfig("deepseek", "chat", ThinkingLevel.LOW)

        assertEquals(
            defaultConfig,
            resolveWorkshopAgentConfig(request(), defaultConfig, sampleModels, setOf("deepseek")),
        )
    }

    @Test
    fun thinkingLevelOverrideAppliesToDefaultConfig() {
        val config = resolveWorkshopAgentConfig(
            request = request(thinkingLevel = "low"),
            defaultConfig = ModelConfig("deepseek", "chat", ThinkingLevel.OFF),
            models = sampleModels,
            configuredProviders = setOf("deepseek"),
        )

        assertEquals(ModelConfig("deepseek", "chat", ThinkingLevel.LOW), config)
        assertRejected {
            resolveWorkshopAgentConfig(
                request = request(thinkingLevel = "max"),
                defaultConfig = ModelConfig("deepseek", "chat", ThinkingLevel.OFF),
                models = sampleModels,
                configuredProviders = setOf("deepseek"),
            )
        }
    }

    @Test
    fun thinkingLevelStringsMapToEnumValues() {
        assertEquals(ThinkingLevel.OFF, parseWorkshopThinkingLevel("off"))
        assertEquals(ThinkingLevel.LOW, parseWorkshopThinkingLevel(" low "))
        assertEquals(ThinkingLevel.MEDIUM, parseWorkshopThinkingLevel("medium"))
        assertEquals(ThinkingLevel.HIGH, parseWorkshopThinkingLevel("high"))
        assertEquals(ThinkingLevel.MAX, parseWorkshopThinkingLevel("max"))
        assertRejected { parseWorkshopThinkingLevel("extreme") }
    }

    @Test
    fun nullSelectionMeansAllNames() {
        assertNull(selectWorkshopAgentNames("tool", null, listOf("read", "write")))
        assertNull(selectWorkshopAgentNames("skill", null, emptyList()))
    }

    @Test
    fun emptySelectionMeansNoNames() {
        assertEquals(emptySet<String>(), selectWorkshopAgentNames("tool", emptyList(), listOf("read")))
    }

    @Test
    fun unknownNameIsRejected() {
        assertRejected { selectWorkshopAgentNames("tool", listOf("missing"), listOf("read")) }
    }

    @Test
    fun oversizedSelectionIsRejected() {
        assertRejected {
            selectWorkshopAgentNames("tool", List(101) { "read" }, listOf("read"))
        }
    }

    @Test
    fun selectionKeepsOnlyKnownNames() {
        assertEquals(
            setOf("read", "write"),
            selectWorkshopAgentNames("tool", listOf("read", "write", "read"), listOf("read", "write", "bash")),
        )
    }

    @Test
    fun toolCatalogIsSortedWithDescriptions() {
        val tools = buildWorkshopAgentTools(
            listOf(
                tool("write", "Last tool"),
                tool("read", "First tool"),
            ),
        )

        assertEquals(listOf("read", "write"), tools.map { it.name })
        assertEquals(listOf("First tool", "Last tool"), tools.map { it.description })
    }

    @Test
    fun skillDirectoryNameIsSanitized() {
        assertEquals("workshop", workshopSkillDirectoryName("workshop"))
        assertEquals("a_b", workshopSkillDirectoryName("a/b"))
        assertEquals("skill", workshopSkillDirectoryName(".."))
        assertEquals("skill", workshopSkillDirectoryName("   "))
        assertFalse(workshopSkillDirectoryName("../../evil").contains("/"))
        assertFalse(workshopSkillDirectoryName("../../evil").contains("\\"))
    }

    @Test
    fun copiesDirectorySkillWithAssets() {
        val sourceRoot = tempDir("workshop-source-skills")
        val directory = File(sourceRoot, "toolbox").apply { mkdirs() }
        File(directory, "SKILL.md").writeText(skillFile("toolbox", "Toolbox helper"))
        File(directory, "assets").mkdirs()
        File(directory, "assets/helper.js").writeText("console.log(1)")
        val skill = Skill(
            name = "toolbox",
            description = "Toolbox helper",
            filePath = File(directory, "SKILL.md").absolutePath,
            content = File(directory, "SKILL.md").readText(),
            baseDir = directory.absolutePath,
        )
        val targetRoot = File(tempDir("workshop-target"), "skills")

        copySelectedSkills(listOf(skill), sourceRoot, targetRoot)

        val copied = File(targetRoot, "toolbox")
        assertTrue(File(copied, "SKILL.md").isFile)
        assertEquals("console.log(1)", File(copied, "assets/helper.js").readText())
    }

    @Test
    fun copiesRootLevelSkillAsSkillFile() {
        val sourceRoot = tempDir("workshop-source-skills")
        val loose = File(sourceRoot, "loose.md").apply { writeText("# Loose skill") }
        val skill = Skill(
            name = "loose",
            description = "Loose helper",
            filePath = loose.absolutePath,
            content = loose.readText(),
            baseDir = sourceRoot.absolutePath,
        )
        val targetRoot = File(tempDir("workshop-target"), "skills")

        copySelectedSkills(listOf(skill), sourceRoot, targetRoot)

        assertEquals("# Loose skill", File(targetRoot, "loose/SKILL.md").readText())
    }

    @Test
    fun emptySkillSelectionCopiesNothing() {
        val targetRoot = File(tempDir("workshop-target"), "skills")

        copySelectedSkills(emptyList(), tempDir("workshop-source-skills"), targetRoot)

        assertFalse(targetRoot.exists())
    }

    @Test
    fun writesAppendSystemPromptOnlyWhenNonBlank() {
        val agentDir = tempDir("workshop-agent")

        writeAppendSystemPrompt(agentDir, "  ")
        writeAppendSystemPrompt(agentDir, null)
        assertFalse(File(agentDir, "APPEND_SYSTEM.md").exists())

        writeAppendSystemPrompt(agentDir, "  Be brief.  ")
        assertEquals("Be brief.", File(agentDir, "APPEND_SYSTEM.md").readText())
    }

    @Test
    fun workshopToolsIncludeConfiguredCapabilitiesAndExcludePersistentLifecycle() {
        val tools = listOf(
            tool("read", "Read a file"),
            tool("ls", "List files"),
            tool("grep", "Search files"),
            tool("find", "Find files"),
            tool("write", "Write a file"),
            tool("edit", "Edit a file"),
            tool("web_search", "Search the web"),
            tool("web_fetch", "Fetch a page"),
            tool("bash", "Run a command"),
            tool("arrange_alarm", "Set an alarm"),
            tool("mi_cloud_notes", "Read notes"),
            tool("gui_tap", "Tap"),
        )

        assertEquals(
            listOf("arrange_alarm", "bash", "edit", "find", "grep", "gui_tap", "ls", "mi_cloud_notes", "read", "web_fetch", "web_search", "write"),
            visibleWorkshopAgentTools(tools).map { it.name }.sorted(),
        )
        assertEquals(
            listOf("arrange_alarm", "bash", "edit", "find", "grep", "gui_tap", "ls", "mi_cloud_notes", "read", "web_fetch", "web_search", "write"),
            buildWorkshopAgentTools(tools).map { it.name },
        )
        assertTrue(workshopAgentToolVisible("web_search"))
        assertTrue(workshopAgentToolVisible("bash"))
        assertTrue(workshopAgentToolVisible("arrange_alarm"))
        assertTrue(workshopAgentToolVisible("screen_tap"))
        assertTrue(workshopAgentToolVisible("mi_cloud_notes"))
        ONE_SHOT_UNAVAILABLE_TOOLS.forEach { assertFalse(workshopAgentToolVisible(it)) }
    }

    @Test
    fun callerMaySelectWebShellAndGuiTools() {
        val available = visibleWorkshopAgentTools(
            listOf(
                tool("read", "Read a file"),
                tool("web_search", "Search the web"),
                tool("web_fetch", "Fetch a page"),
                tool("bash", "Run a command"),
                tool("gui_click", "Click"),
            ),
        ).map { it.name }

        assertEquals(
            setOf("web_search", "web_fetch"),
            selectWorkshopAgentNames("tool", listOf("web_search", "web_fetch"), available),
        )
        assertEquals(setOf("bash"), selectWorkshopAgentNames("tool", listOf("bash"), available))
        assertEquals(setOf("gui_click"), selectWorkshopAgentNames("tool", listOf("gui_click"), available))
        assertNull(selectWorkshopAgentNames("tool", null, available))
    }

    @Test
    fun mapsAgentEventsToWorkshopEvents() {
        val message = assistantMessage()
        val agentMessage = agentMessage()

        assertEquals(
            WorkshopAgentEvent(WorkshopAgentEventType.TEXT_DELTA, text = "hi"),
            workshopAgentEventOf(AgentEvent.MessageUpdate(agentMessage, TextDeltaEvent(0, "hi", message))),
        )
        assertEquals(
            WorkshopAgentEvent(WorkshopAgentEventType.THINKING_DELTA, text = "think"),
            workshopAgentEventOf(AgentEvent.MessageUpdate(agentMessage, ThinkingDeltaEvent(0, "think", message))),
        )
        assertEquals(
            WorkshopAgentEvent(WorkshopAgentEventType.TOOL_START, name = "web_search"),
            workshopAgentEventOf(AgentEvent.ToolExecutionStart("call", "web_search", JsonObject(emptyMap()))),
        )
        assertEquals(
            WorkshopAgentEvent(
                WorkshopAgentEventType.TOOL_END,
                name = "web_search",
                isError = true,
                durationMs = 12,
            ),
            workshopAgentEventOf(AgentEvent.ToolExecutionEnd("call", "web_search", AgentToolResult(), true, 12)),
        )
        assertEquals(
            WorkshopAgentEvent(WorkshopAgentEventType.TURN_END),
            workshopAgentEventOf(AgentEvent.TurnEnd(agentMessage, emptyList())),
        )
        assertNull(workshopAgentEventOf(AgentEvent.AgentStart))
        assertNull(workshopAgentEventOf(AgentEvent.AgentEnd(emptyList())))
    }

    @Test
    fun mapsSessionEventsToWorkshopEvents() {
        assertEquals(
            WorkshopAgentEvent(WorkshopAgentEventType.TEXT_DELTA, text = "hi"),
            workshopAgentEventOf(
                SessionEvent.AgentEventReceived(
                    AgentEvent.MessageUpdate(agentMessage(), TextDeltaEvent(0, "hi", assistantMessage())),
                ),
            ),
        )
        assertEquals(
            WorkshopAgentEvent(
                type = WorkshopAgentEventType.RUN_ERROR,
                code = WorkshopAgentErrorCode.AGENT_FAILED,
                message = "boom",
            ),
            workshopAgentEventOf(SessionEvent.SessionError("boom")),
        )
        assertNull(workshopAgentEventOf(SessionEvent.Settled))
    }

    private fun assistantMessage(): AssistantMessage = AssistantMessage(
        content = mutableListOf(),
        api = "test",
        provider = "test",
        model = "test",
        usage = Usage.empty(),
        stopReason = StopReason.Stop,
        timestamp = 0,
    )

    private fun agentMessage(): AgentMessage = object : AgentMessage {
        override val role: String = "assistant"
        override val timestamp: Long = 0
    }

    private val sampleModels = listOf(
        WorkshopAgentModel("deepseek", "chat", "Chat", listOf("off", "low")),
        WorkshopAgentModel("openai", "gpt", "GPT", listOf("off", "medium", "high")),
    )

    @Test
    fun runAppliesRuntimeHooksAndBindsWorkshopSession() = runBlocking {
        val root = tempDir("one-shot-hooks")
        val workspace = File(root, "home").apply { mkdirs() }
        val agentDir = File(root, "agent").apply { mkdirs() }
        val runRoot = File(root, "runs")
        val modelRequests = mutableListOf<JsonObject>()
        val hookTransport = object : HttpTransport {
            override suspend fun execute(request: HttpRequest): HttpResponse {
                modelRequests += Json.parseToJsonElement(request.body!!.toString(Charsets.UTF_8)).jsonObject
                val payload = "{\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"finished\"},\"finish_reason\":\"stop\"}]}"
                return HttpResponse(
                    200,
                    mapOf("content-type" to "text/event-stream"),
                    Buffer().writeUtf8("data: $payload\n\ndata: [DONE]\n\n"),
                )
            }
        }
        val catalog = ProviderCatalog(hookTransport, credentials)
        val bound = mutableListOf<SessionBinding>()
        val closed = mutableListOf<String>()
        var factoryCalls = 0
        val service = WorkshopAgentService(
            providers = catalog,
            credentials = credentials,
            transport = hookTransport,
            agentDir = agentDir,
            runRoot = runRoot,
            appToolContext = AppToolContext(QuestionCenter()),
            defaultConfig = { ModelConfig("deepseek", catalog.defaultModel("deepseek")!!.id, ThinkingLevel.OFF) },
            configuredProviders = { setOf("deepseek") },
            personalization = { Personalization() },
            pluginPromptBody = { null },
            sharedStorage = { false },
            workspace = workspace,
            hooksFactory = {
                factoryCalls += 1
                RuntimeHooks(
                    promptTransform = { "$it/plugin" },
                    inputHandler = { input -> InputDecision.Continue("${input.text}-input", input.images) },
                    onSessionBound = { session -> bound += session },
                    onSessionClosed = { sessionId -> closed += sessionId },
                )
            },
        )
        try {
            assertEquals("finished", service.run(request(prompt = "hello")) {})

            assertEquals(1, factoryCalls)
            assertEquals(1, bound.size)
            val binding = bound.single()
            assertFalse(binding.supportsChatDelivery)
            val snapshot = binding.current()
            assertNotNull(snapshot)
            assertEquals(workspace.absolutePath, snapshot!!.cwd)
            assertEquals(listOf(snapshot.sessionId), closed)
            assertTrue(modelRequests.last().toString().contains("hello-input/plugin"))
        } finally {
            root.deleteRecursively()
        }
    }

    private fun request(
        prompt: String = "hello",
        providerId: String? = null,
        modelId: String? = null,
        thinkingLevel: String? = null,
        systemPrompt: String? = null,
        maxTurns: Int? = null,
        tools: List<String>? = null,
        skills: List<String>? = null,
    ): WorkshopAgentRequest = WorkshopAgentRequest(
        prompt = prompt,
        providerId = providerId,
        modelId = modelId,
        thinkingLevel = thinkingLevel,
        systemPrompt = systemPrompt,
        maxTurns = maxTurns,
        tools = tools,
        skills = skills,
    )

    @Test
    fun oneShotUsesCurrentToolsReadsOutsideAndRetainsArtifactsWhileJoiningJobs() = runBlocking {
        val root = tempDir("one-shot-integration")
        val workspace = File(root, "home").apply { mkdirs() }
        val agentDir = File(root, "agent").apply { mkdirs() }
        val outside = File(root, "outside.txt").apply { writeText("outside content") }
        val artifact = File(workspace, "artifact.txt")
        val runRoot = File(root, "runs")
        var shellCalled = false
        var pluginCalled = false
        var jobStopped = false
        val jobStarted = CompletableDeferred<Unit>()
        var modelCalls = 0
        var cancelOnReply = false
        var selectedOnly = false
        val replyRequested = CompletableDeferred<Unit>()
        val modelRequests = mutableListOf<JsonObject>()
        val scriptedTransport = object : HttpTransport {
            override suspend fun execute(request: HttpRequest): HttpResponse {
                val body = Json.parseToJsonElement(request.body!!.toString(Charsets.UTF_8)).jsonObject
                modelRequests += body
                val payload = if (!selectedOnly && modelCalls++ == 0) {
                    val calls = listOf(
                        "read" to "{\"path\":${Json.encodeToString(kotlinx.serialization.json.JsonPrimitive(outside.absolutePath))}}",
                        "write" to "{\"path\":${Json.encodeToString(kotlinx.serialization.json.JsonPrimitive(artifact.absolutePath))},\"content\":\"retained\"}",
                        "bash" to "{\"command\":\"printf ready\"}",
                        "current_plugin" to "{}",
                        "job_start" to "{\"command\":\"slow command\"}",
                    )
                    val toolCalls = calls.mapIndexed { index, (name, args) ->
                        "{\"index\":$index,\"id\":\"call_$index\",\"type\":\"function\",\"function\":{\"name\":\"$name\",\"arguments\":${Json.encodeToString(kotlinx.serialization.json.JsonPrimitive(args))}}}"
                    }.joinToString(",")
                    "{\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"tool_calls\":[$toolCalls]},\"finish_reason\":\"tool_calls\"}]}"
                } else {
                    if (!selectedOnly) jobStarted.await()
                    if (cancelOnReply) { replyRequested.complete(Unit); awaitCancellation() }
                    "{\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"finished\"},\"finish_reason\":\"stop\"}]}"
                }
                return HttpResponse(200, mapOf("content-type" to "text/event-stream"), Buffer().writeUtf8("data: $payload\n\ndata: [DONE]\n\n"))
            }
        }
        val shell = object : ShellExecutor {
            override suspend fun run(command: String, timeoutSeconds: Long?, signal: RunSignal?): ShellResult {
                shellCalled = command == "printf ready"
                return ShellResult("ready", 0)
            }
        }
        val context = AppToolContext(QuestionCenter(), pluginTools = {
            listOf(tool("current_plugin", "Configured plugin") { pluginCalled = true; textResult("plugin ready") }) +
                goalTools(SessionBinding(), GoalService()) + listOf(presentTool(null) { null })
        })
        val catalog = ProviderCatalog(scriptedTransport, credentials)
        val service = WorkshopAgentService(
            providers = catalog, credentials = credentials, transport = scriptedTransport, agentDir = agentDir, runRoot = runRoot,
            appToolContext = context, defaultConfig = { ModelConfig("deepseek", catalog.defaultModel("deepseek")!!.id, ThinkingLevel.OFF) },
            configuredProviders = { setOf("deepseek") }, personalization = { Personalization() }, pluginPromptBody = { null }, sharedStorage = { false },
            workspace = workspace, shellExecutorFor = { shell }, backgroundRunnerFactory = {
                BackgroundCommandRunner { _, _ ->
                    jobStarted.complete(Unit)
                    try { awaitCancellation() } finally { jobStopped = true }
                }
            },
        )
        try {
            val events = mutableListOf<WorkshopAgentEvent>()
            assertEquals("finished", service.run(request(prompt = "neutral integration check")) { events += it })
            assertEquals("retained", artifact.readText())
            assertTrue(shellCalled)
            assertTrue(pluginCalled)
            assertTrue(jobStopped)
            assertTrue(runRoot.listFiles().orEmpty().isEmpty())
            assertEquals(1, events.count { it.type == WorkshopAgentEventType.RUN_END })
            val transcript = modelRequests.last().toString()
            assertTrue(transcript.contains("outside content"))
            assertFalse(transcript.contains("create_goal"))
            assertFalse(transcript.contains("timer_create"))
            assertFalse(transcript.contains("Deliver finished work with present"))
            modelCalls = 0
            cancelOnReply = true
            jobStopped = false
            val running = async { service.run(request(prompt = "cancel neutral run")) {} }
            replyRequested.await()
            running.cancelAndJoin()
            assertTrue(jobStopped)
            assertTrue(runRoot.listFiles().orEmpty().isEmpty())
            assertEquals("retained", artifact.readText())
            cancelOnReply = false
            selectedOnly = true
            service.run(request(prompt = "selected tools", tools = listOf("read"), skills = emptyList())) {}
            val toolNames = modelRequests.last().getValue("tools").let { it as kotlinx.serialization.json.JsonArray }
                .map { it.jsonObject.getValue("function").jsonObject.getValue("name").jsonPrimitive.content }
            assertEquals(listOf("read"), toolNames)

        } finally { root.deleteRecursively() }
    }

    @Test
    fun capabilitiesLoadConfiguredMcpTools() = runBlocking {
        val root = tempDir("one-shot-mcp")
        val agent = File(root, "agent").apply { mkdirs() }
        File(agent, "mcp.json").writeText("""{"mcpServers":{"fixture":{"url":"https://mcp.invalid/rpc","exposure":"direct"}}}""")
        val methods = mutableListOf<String>()
        val mcpTransport = object : HttpTransport {
            override suspend fun execute(request: HttpRequest): HttpResponse {
                if (request.method != "POST") return HttpResponse(405, emptyMap(), Buffer())
                val body = Json.parseToJsonElement(request.body!!.toString(Charsets.UTF_8)).jsonObject
                val method = body.getValue("method").jsonPrimitive.content
                methods += method
                val id = body["id"] ?: return HttpResponse(202, emptyMap(), Buffer())
                val result = when (method) {
                    "initialize" -> """{"protocolVersion":"2025-03-26","capabilities":{"tools":{}},"serverInfo":{"name":"fixture","version":"1"}}"""
                    "tools/list" -> """{"tools":[{"name":"probe","description":"Configured MCP probe","inputSchema":{"type":"object","properties":{}}}]}"""
                    else -> "{}"
                }
                return HttpResponse(200, mapOf("content-type" to "application/json"), Buffer().writeUtf8("{\"jsonrpc\":\"2.0\",\"id\":$id,\"result\":$result}"))
            }
        }
        val catalog = ProviderCatalog(mcpTransport, credentials)
        val service = WorkshopAgentService(catalog, credentials, mcpTransport, agent, File(root, "runs"), AppToolContext(QuestionCenter()),
            defaultConfig = { ModelConfig() }, configuredProviders = { setOf("deepseek") }, personalization = { Personalization() }, pluginPromptBody = { null }, sharedStorage = { false })
        try {
            val capabilities = service.capabilities()
            assertTrue(capabilities.tools.toString(), capabilities.tools.any { it.name == "mcp__fixture__probe" })
            assertTrue(methods.contains("initialize"))
            assertTrue(methods.contains("tools/list"))
            assertTrue(File(root, "runs").listFiles().orEmpty().isEmpty())
        } finally { root.deleteRecursively() }
    }

    @Test
    fun mcpCredentialRefreshUsesSharedStorageAndSurvivesSessionCleanup() {
        val root = tempDir("one-shot-shared-mcp-auth")
        val agent = File(root, "agent").apply { mkdirs() }
        val temporary = File(root, "run/agent").apply { mkdirs() }
        val workspace = File(root, "home").apply { mkdirs() }
        val fs = workshopResourceFileSystem(workspace, agent, temporary)
        try {
            File(agent, "mcp-auth.json").writeText("original")
            val auth = File(temporary, "mcp-auth.json").absolutePath.toPath()
            val pending = File(temporary, "mcp-auth.json.tmp").absolutePath.toPath()
            assertEquals("original", fs.read(auth) { readUtf8() })
            fs.write(pending) { writeUtf8("refreshed") }
            fs.atomicMove(pending, auth)
            temporary.parentFile!!.deleteRecursively()
            assertEquals("refreshed", File(agent, "mcp-auth.json").readText())
            assertFalse(File(temporary, "mcp-auth.json").exists())
            val outside = File(root, "outside.txt").apply { writeText("unrestricted") }
            assertEquals("unrestricted", fs.read(outside.absolutePath.toPath()) { readUtf8() })
        } finally { root.deleteRecursively() }
    }

    private fun tool(name: String, description: String, run: (suspend () -> AgentToolResult)? = null): CodingTool = CodingTool(
        name = name,
        description = description,
        parameters = JsonObject(emptyMap()),
        label = name,
        execute = { _, _, _, _ -> run?.invoke() ?: error("not executed in catalog tests") },
    )

    private fun skillFile(name: String, description: String): String =
        "---\nname: $name\ndescription: $description\n---\n\nBody of $name.\n"

    private fun tempDir(prefix: String): File {
        val directory = Files.createTempDirectory(prefix).toFile()
        directory.deleteOnExit()
        return directory
    }

    private fun assertRejected(block: () -> Unit) {
        try {
            block()
        } catch (_: IllegalArgumentException) {
            return
        }
        throw AssertionError("expected IllegalArgumentException")
    }
}
