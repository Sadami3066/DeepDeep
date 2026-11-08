package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.sdk.agent.asAssistantMessage
import io.github.whywood.deepdeep.sdk.ai.StopReason
import io.github.whywood.deepdeep.sdk.agent.AgentEvent
import io.github.whywood.deepdeep.sdk.ai.TextDeltaEvent
import io.github.whywood.deepdeep.sdk.ai.ThinkingDeltaEvent
import io.github.whywood.deepdeep.sdk.ai.auth.CredentialStore
import io.github.whywood.deepdeep.sdk.ai.http.HttpTransport
import io.github.whywood.deepdeep.sdk.coding.prompt.Skill
import io.github.whywood.deepdeep.sdk.coding.prompt.loadSkills
import io.github.whywood.deepdeep.sdk.coding.session.AgentSession
import io.github.whywood.deepdeep.sdk.coding.session.SessionEvent
import io.github.whywood.deepdeep.sdk.coding.tools.CodingTool
import io.github.whywood.deepdeep.sdk.coding.tools.ShellExecutor
import io.github.whywood.deepdeep.sdk.mcp.config.McpExposure
import io.github.whywood.deepdeep.sdk.mcp.config.McpServerConfig
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

class MiniappAgentService(
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
    private val storageMounts: suspend () -> List<StorageMountInfo> = { emptyList() },
    private val defaultMaxTurns: () -> Int = { DEFAULT_MAX_TURNS },
    private val workspace: File = File(runRoot.parentFile, "home"),
    private val shellExecutorFor: suspend (File) -> ShellExecutor? = { null },
    private val backgroundRunnerFactory: ((String?) -> BackgroundCommandRunner)? = null,
    private val hooksFactory: (() -> RuntimeHooks)? = null,
) : MiniappAgentGateway {
    override suspend fun models(): List<MiniappAgentModel> =
        buildMiniappAgentModels(providers, configuredProviders())

    override suspend fun capabilities(): MiniappAgentCapabilities {
        val root = createRunDirectory()
        var session: AgentSession? = null
        try {
            val tempAgentDir = prepareAgentDirectory(root)
            val registry = runJobs()
            val engine = createEngine(tempAgentDir, registry)
            val created = engine.createSession(null, defaultConfig(), toolsFilter = { miniappAgentToolVisible(it.name) })
            session = created
            val tools = (buildMiniappAgentTools(engine.availableTools()) + created.mcp.tools()
                .filter { it.exposure != McpExposure.Hidden }
                .map { MiniappAgentTool(it.exposedName, it.description.orEmpty()) })
                .distinctBy { it.name }.sortedBy { it.name }
            val skills = discoverSkills(workspace).map { MiniappAgentSkill(it.name, it.description) }.sortedBy { it.name }
            return MiniappAgentCapabilities(tools, skills)
        } finally {
            withContext(NonCancellable) { try { session?.close() } finally { root.deleteRecursively() } }
        }
    }

    override suspend fun run(
        request: MiniappAgentRequest,
        onEvent: suspend (MiniappAgentEvent) -> Unit,
    ): String {
        validateMiniappAgentRequest(request)
        val config = resolveMiniappAgentConfig(
            request = request,
            defaultConfig = defaultConfig(),
            models = models(),
            configuredProviders = configuredProviders(),
        )
        val maxTurns = miniappAgentMaxTurns(request.maxTurns, defaultMaxTurns())
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

        suspend fun emit(event: MiniappAgentEvent) {
            if (event.type == MiniappAgentEventType.RUN_END || event.type == MiniappAgentEventType.RUN_ERROR) {
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
            val tools = visibleMiniappAgentTools(engine.availableTools())
            val skills = discoverSkills(workspace)
            val selectedSkills = selectMiniappAgentNames("skill", request.skills, skills.map { it.name })
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
                toolsFilter = { miniappAgentToolVisible(it.name) && (requested == null || it.name in requested) },
                mcpEnabled = mcpEnabled,
                extraSystemPromptSections = mapOf("run_lifetime" to wrapPromptSection("run_lifetime", "This is a one-shot page run. Return final text and retain work files. Parallel jobs exist only for this run and will be stopped on completion or cancellation.")),
            )
            session = created
            val mcpTools = if (mcpEnabled) created.mcp.tools().filter { it.exposure != McpExposure.Hidden } else emptyList()
            val selectedTools = selectMiniappAgentNames("tool", request.tools, tools.map { it.name } + mcpTools.map { it.exposedName })
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
                miniappAgentEventOf(sessionEvent)?.let { event -> emit(event) }
            }
            emit(MiniappAgentEvent(type = MiniappAgentEventType.RUN_START))
            when (val input = engine.prepareInput(request.prompt, emptyList())) {
                is PreparedPrompt.Ready -> withTimeout(RUN_TIMEOUT_MS) { created.prompt(input.text) }
                PreparedPrompt.Handled -> {
                    closeRun()
                    emit(
                        MiniappAgentEvent(
                            type = MiniappAgentEventType.RUN_END,
                            status = MiniappAgentRunStatus.COMPLETED,
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
                MiniappAgentEvent(
                    type = MiniappAgentEventType.RUN_END,
                    status = if (truncated.get()) {
                        MiniappAgentRunStatus.TRUNCATED
                    } else {
                        MiniappAgentRunStatus.COMPLETED
                    },
                    truncated = truncated.get(),
                ),
            )
            return text
        } catch (error: TimeoutCancellationException) {
            runCatching { session?.abort() }
            if (unsubscribe != null) awaitSettled()
            emit(
                MiniappAgentEvent(
                    type = MiniappAgentEventType.RUN_ERROR,
                    code = MiniappAgentErrorCode.TIMEOUT,
                    message = "miniapp agent timed out after ${RUN_TIMEOUT_MS / 1000} seconds",
                ),
            )
            throw IllegalStateException("miniapp agent timed out", error)
        } catch (error: CancellationException) {
            throw error
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (error: Exception) {
            if (unsubscribe != null) awaitSettled()
            emit(
                MiniappAgentEvent(
                    type = MiniappAgentEventType.RUN_ERROR,
                    code = MiniappAgentErrorCode.AGENT_FAILED,
                    message = error.message ?: "miniapp agent run failed",
                ),
            )
            if (error is IllegalStateException) throw error
            throw IllegalStateException(error.message ?: "miniapp agent run failed", error)
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
        storageMounts = storageMounts,
        pluginPromptBody = pluginPromptBody,
        toolContext = oneShotToolContext(appToolContext, registry),
        fs = miniappResourceFileSystem(workspace, agentDir, tempAgentDir),
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
            throw IllegalStateException("cannot create miniapp agent run directory")
        }
        val directory = File(runRoot, UUID.randomUUID().toString())
        if (!directory.mkdirs() && !directory.isDirectory) {
            throw IllegalStateException("cannot create miniapp agent run directory")
        }
        return directory
    }
}
