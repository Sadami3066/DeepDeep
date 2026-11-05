package io.github.whywood.deepdeep.runtime

import dev.pi.agent.asAssistantMessage
import dev.pi.ai.StopReason
import dev.pi.agent.AgentEvent
import dev.pi.ai.TextDeltaEvent
import dev.pi.ai.ThinkingDeltaEvent
import dev.pi.ai.auth.CredentialStore
import dev.pi.ai.http.HttpTransport
import dev.pi.coding.prompt.Skill
import dev.pi.coding.prompt.loadSkills
import dev.pi.coding.session.AgentSession
import dev.pi.coding.session.SessionEvent
import dev.pi.coding.tools.CodingTool
import dev.pi.coding.tools.ShellExecutor
import dev.pi.mcp.config.McpExposure
import dev.pi.mcp.config.McpServerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import okio.ForwardingFileSystem
import okio.Path
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okio.FileSystem
import okio.Path.Companion.toPath

internal val ONE_SHOT_UNAVAILABLE_TOOLS = setOf(
    "get_goal", "create_goal", "update_goal", "timer_create", "timer_list", "timer_update", "timer_delete", "present",
)

class WorkshopAgentService(
    private val providers: ProviderCatalog,
    private val credentials: CredentialStore,
    private val transport: HttpTransport,
    private val agentDir: File,
    private val runRoot: File,
    private val appToolContext: AppToolContext,
    private val defaultConfig: suspend () -> ModelConfig,
    private val configuredProviders: () -> Set<String>,
    private val personalization: suspend () -> Personalization,
    private val pluginPromptBody: suspend () -> String?,
    private val sharedStorage: suspend () -> Boolean,
    private val defaultMaxTurns: () -> Int = { DEFAULT_MAX_TURNS },
    private val workspace: File = File(runRoot.parentFile, "home"),
    private val shellExecutorFor: suspend (File) -> ShellExecutor? = { null },
    private val backgroundRunnerFactory: ((String?) -> BackgroundCommandRunner)? = null,
    private val hooksFactory: (() -> RuntimeHooks)? = null,
) : WorkshopAgentGateway {
    override suspend fun models(): List<WorkshopAgentModel> =
        buildWorkshopAgentModels(providers, configuredProviders())

    override suspend fun capabilities(): WorkshopAgentCapabilities {
        val root = createRunDirectory()
        var session: AgentSession? = null
        try {
            val tempAgentDir = prepareAgentDirectory(root)
            val registry = runJobs()
            val engine = createEngine(tempAgentDir, registry)
            val created = engine.createSession(null, defaultConfig(), toolsFilter = { workshopAgentToolVisible(it.name) })
            session = created
            val tools = (buildWorkshopAgentTools(engine.availableTools()) + created.mcp.tools()
                .filter { it.exposure != McpExposure.Hidden }
                .map { WorkshopAgentTool(it.exposedName, it.description.orEmpty()) })
                .distinctBy { it.name }.sortedBy { it.name }
            val skills = discoverSkills(workspace).map { WorkshopAgentSkill(it.name, it.description) }.sortedBy { it.name }
            return WorkshopAgentCapabilities(tools, skills)
        } finally {
            withContext(NonCancellable) { try { session?.close() } finally { root.deleteRecursively() } }
        }
    }

    override suspend fun run(
        request: WorkshopAgentRequest,
        onEvent: suspend (WorkshopAgentEvent) -> Unit,
    ): String {
        validateWorkshopAgentRequest(request)
        val config = resolveWorkshopAgentConfig(
            request = request,
            defaultConfig = defaultConfig(),
            models = models(),
            configuredProviders = configuredProviders(),
        )
        val maxTurns = workshopAgentMaxTurns(request.maxTurns, defaultMaxTurns())
        val root = createRunDirectory()
        val registry = runJobs()
        val runtimeHooks = hooksFactory?.invoke() ?: RuntimeHooks.None
        var session: AgentSession? = null
        var unsubscribe: (() -> Unit)? = null
        val settled = CompletableDeferred<Unit>()
        val terminal = AtomicBoolean(false)
        val truncated = AtomicBoolean(false)
        val sessionError = AtomicReference<String?>(null)

        suspend fun closeRun() {
            withContext(NonCancellable) {
                try {
                    unsubscribe?.invoke()
                    unsubscribe = null
                    session?.let { created ->
                        try {
                            created.close()
                        } finally {
                            registry.finishSession(created.sessionId)
                            session = null
                            runtimeHooks.onSessionClosed?.invoke(created.sessionId)
                        }
                    }
                } finally { root.deleteRecursively() }
            }
        }

        suspend fun emit(event: WorkshopAgentEvent) {
            if (event.type == WorkshopAgentEventType.RUN_END || event.type == WorkshopAgentEventType.RUN_ERROR) {
                if (!terminal.compareAndSet(false, true)) return
            }
            onEvent(event)
        }

        suspend fun awaitSettled() {
            withTimeoutOrNull(SETTLE_TIMEOUT_MS) { settled.await() }
        }

        try {
            workspace.mkdirs()
            val tempAgentDir = prepareAgentDirectory(root)
            val engine = createEngine(tempAgentDir, registry, runtimeHooks)
            val tools = visibleWorkshopAgentTools(engine.availableTools())
            val skills = discoverSkills(workspace)
            val selectedSkills = selectWorkshopAgentNames("skill", request.skills, skills.map { it.name })
            copySelectedSkills(
                skills = selectedSkills?.let { names -> skills.filter { it.name in names } } ?: skills,
                skillsRoot = File(agentDir, SKILLS_DIR),
                targetSkillsRoot = File(tempAgentDir, SKILLS_DIR),
            )
            writeAppendSystemPrompt(tempAgentDir, request.systemPrompt)
            val requested = request.tools?.toSet()
            val mcpEnabled = requested == null || requested.any { it.startsWith("mcp__") }
            val created = engine.createSession(
                file = null,
                config = config,
                toolsFilter = { workshopAgentToolVisible(it.name) && (requested == null || it.name in requested) },
                mcpEnabled = mcpEnabled,
                extraSystemPromptSections = mapOf("run_lifetime" to wrapPromptSection("run_lifetime", "This is a one-shot page run. Return final text and retain work files. Parallel jobs exist only for this run and will be stopped on completion or cancellation.")),
            )
            session = created
            val mcpTools = if (mcpEnabled) created.mcp.tools().filter { it.exposure != McpExposure.Hidden } else emptyList()
            val selectedTools = selectWorkshopAgentNames("tool", request.tools, tools.map { it.name } + mcpTools.map { it.exposedName })
            if (selectedTools != null && mcpTools.isNotEmpty()) {
                val configs = engine.loadMcpServers().mapValues { (server, config) ->
                    val selected = mcpTools.filter { it.server == server && it.exposedName in selectedTools }
                        .associate { it.name to McpExposure.Direct }
                    when (config) {
                        is McpServerConfig.Stdio -> config.copy(enabled = config.enabled && selected.isNotEmpty(), exposure = McpExposure.Hidden, toolExposure = selected)
                        is McpServerConfig.Http -> config.copy(enabled = config.enabled && selected.isNotEmpty(), exposure = McpExposure.Hidden, toolExposure = selected)
                    }
                }
                created.mcp.reload(configs)
                created.agent.state.tools = created.agent.state.tools.filter { it.name in selectedTools }.toMutableList()
            }
            val turns = AtomicInteger(0)
            unsubscribe = created.subscribe { sessionEvent ->
                when (sessionEvent) {
                    is SessionEvent.AgentEventReceived -> {
                        val agentEvent = sessionEvent.event
                        if (agentEvent is AgentEvent.TurnEnd && turns.incrementAndGet() >= maxTurns) {
                            truncated.set(true)
                            created.abort()
                        }
                    }

                    is SessionEvent.SessionError -> sessionError.set(sessionEvent.message)
                    is SessionEvent.Settled -> settled.complete(Unit)
                    else -> {}
                }
                workshopAgentEventOf(sessionEvent)?.let { event -> emit(event) }
            }
            emit(WorkshopAgentEvent(type = WorkshopAgentEventType.RUN_START))
            when (val input = engine.prepareInput(request.prompt, emptyList())) {
                is PreparedPrompt.Ready -> withTimeout(RUN_TIMEOUT_MS) { created.prompt(input.text) }
                PreparedPrompt.Handled -> {
                    closeRun()
                    emit(
                        WorkshopAgentEvent(
                            type = WorkshopAgentEventType.RUN_END,
                            status = WorkshopAgentRunStatus.COMPLETED,
                        ),
                    )
                    return ""
                }
            }
            awaitSettled()
            sessionError.get()?.let { message -> throw IllegalStateException(message) }
            val last = created.messages.lastOrNull()?.asAssistantMessage()
            if (last?.stopReason == StopReason.Error) throw IllegalStateException(last.errorMessage ?: "agent model request failed")
            val text = created.getLastAssistantText() ?: throw IllegalStateException("agent returned no text")
            closeRun()
            emit(
                WorkshopAgentEvent(
                    type = WorkshopAgentEventType.RUN_END,
                    status = if (truncated.get()) {
                        WorkshopAgentRunStatus.TRUNCATED
                    } else {
                        WorkshopAgentRunStatus.COMPLETED
                    },
                    truncated = truncated.get(),
                ),
            )
            return text
        } catch (error: TimeoutCancellationException) {
            runCatching { session?.abort() }
            if (unsubscribe != null) awaitSettled()
            emit(
                WorkshopAgentEvent(
                    type = WorkshopAgentEventType.RUN_ERROR,
                    code = WorkshopAgentErrorCode.TIMEOUT,
                    message = "workshop agent timed out after ${RUN_TIMEOUT_MS / 1000} seconds",
                ),
            )
            throw IllegalStateException("workshop agent timed out", error)
        } catch (error: CancellationException) {
            throw error
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (error: Exception) {
            if (unsubscribe != null) awaitSettled()
            emit(
                WorkshopAgentEvent(
                    type = WorkshopAgentEventType.RUN_ERROR,
                    code = WorkshopAgentErrorCode.AGENT_FAILED,
                    message = error.message ?: "workshop agent run failed",
                ),
            )
            if (error is IllegalStateException) throw error
            throw IllegalStateException(error.message ?: "workshop agent run failed", error)
        } finally { closeRun() }
    }

    private suspend fun runJobs() = JobRegistry(
        scope = CoroutineScope(currentCoroutineContext()),
        runnerFactory = backgroundRunnerFactory,
        notifier = { _, _, _ -> },
        wakeEnabled = { false },
    )

    private fun prepareAgentDirectory(root: File): File {
        val directory = File(root, AGENT_DIR).apply { mkdirs() }
        listOf("SYSTEM.md", "APPEND_SYSTEM.md", "AGENTS.md", "settings.json", "models.json", "mcp.json").forEach { name ->
            val source = File(agentDir, name)
            if (source.isFile) source.copyTo(File(directory, name), overwrite = true)
        }
        return directory
    }

    private suspend fun createEngine(
        tempAgentDir: File,
        registry: JobRegistry,
        hooks: RuntimeHooks = RuntimeHooks.None,
    ): PiSessionEngine = PiSessionEngine(
        workspace = workspace,
        agentDir = tempAgentDir,
        credentials = credentials,
        transport = transport,
        shellExecutor = shellExecutorFor(workspace),
        providers = providers,
        personalization = personalization,
        sharedStorage = sharedStorage,
        pluginPromptBody = pluginPromptBody,
        toolContext = oneShotToolContext(appToolContext, registry),
        fs = workshopResourceFileSystem(workspace, agentDir, tempAgentDir),
        toolFs = FileSystem.SYSTEM,
        supportsChatDelivery = false,
        hooks = hooks,
    )

    private suspend fun discoverSkills(cwd: File): List<Skill> = runCatching {
        loadSkills(
            cwd = cwd.absolutePath.toPath(),
            agentDir = agentDir.absolutePath.toPath(),
            fs = FileSystem.SYSTEM,
            projectTrusted = true,
        )
    }.getOrDefault(emptyList())

    private fun createRunDirectory(): File {
        if (!runRoot.isDirectory && !runRoot.mkdirs() && !runRoot.isDirectory) {
            throw IllegalStateException("cannot create workshop agent run directory")
        }
        val directory = File(runRoot, UUID.randomUUID().toString())
        if (!directory.mkdirs() && !directory.isDirectory) {
            throw IllegalStateException("cannot create workshop agent run directory")
        }
        return directory
    }
}
