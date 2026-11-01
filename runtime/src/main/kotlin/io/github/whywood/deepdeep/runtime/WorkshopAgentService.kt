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

private const val MAX_PROMPT_CHARS = 64 * 1024
private const val MAX_SYSTEM_PROMPT_CHARS = 16 * 1024
private const val DEFAULT_MAX_TURNS = 16
private const val MAX_TURNS_LIMIT = 64
private const val MAX_SELECTION_ENTRIES = 100
private const val RUN_TIMEOUT_MS = 180_000L
private const val SETTLE_TIMEOUT_MS = 2_000L
private const val SKILL_FILE = "SKILL.md"
private const val SKILLS_DIR = "skills"
private const val AGENT_DIR = "agent"
private const val APPEND_SYSTEM_PROMPT_FILE = "APPEND_SYSTEM.md"

internal val ONE_SHOT_UNAVAILABLE_TOOLS = setOf(
    "get_goal", "create_goal", "update_goal", "schedule_create", "schedule_list", "schedule_update", "schedule_delete", "present",
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
                        try { created.close() } finally { registry.finishSession(created.sessionId); session = null }
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
            val engine = createEngine(tempAgentDir, registry)
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
            withTimeout(RUN_TIMEOUT_MS) { created.prompt(request.prompt) }
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

    private suspend fun createEngine(tempAgentDir: File, registry: JobRegistry): PiSessionEngine = PiSessionEngine(
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

internal fun workshopResourceFileSystem(workspace: File, agentDir: File, temporaryAgentDir: File): FileSystem =
    object : ForwardingFileSystem(FileSystem.SYSTEM) {
        private val projectSkills = workspace.absolutePath.toPath() / ".pi" / "skills"
        private val temporaryRoot = temporaryAgentDir.absolutePath.toPath()
        private val sharedRoot = agentDir.absolutePath.toPath()
        override fun list(dir: Path): List<Path> = if (dir == projectSkills) emptyList() else super.list(dir)
        override fun listOrNull(dir: Path): List<Path>? = if (dir == projectSkills) emptyList() else super.listOrNull(dir)
        override fun onPathParameter(path: Path, functionName: String, parameterName: String): Path =
            if (path.parent == temporaryRoot && path.name in setOf("mcp-auth.json", "mcp-auth.json.tmp")) sharedRoot / path.name else path
    }

internal fun buildWorkshopAgentModels(
    providers: ProviderCatalog,
    configuredProviders: Set<String>,
): List<WorkshopAgentModel> = configuredProviders
    .flatMap { providerId ->
        providers.models(providerId).map { option ->
            WorkshopAgentModel(
                providerId = option.providerId,
                modelId = option.id,
                label = option.displayName,
                thinkingLevels = option.thinkingLevels.map { it.runtimeValue() },
            )
        }
    }
    .sortedWith(compareBy({ it.providerId }, { it.modelId }))

internal fun buildWorkshopAgentTools(tools: List<CodingTool>): List<WorkshopAgentTool> =
    visibleWorkshopAgentTools(tools).map { WorkshopAgentTool(it.name, it.description) }.sortedBy { it.name }

internal fun visibleWorkshopAgentTools(tools: List<CodingTool>): List<CodingTool> =
    tools.filter { workshopAgentToolVisible(it.name) }

internal fun workshopAgentToolVisible(name: String): Boolean = name !in ONE_SHOT_UNAVAILABLE_TOOLS

internal fun oneShotToolContext(context: AppToolContext, registry: JobRegistry): AppToolContext = context.copy(
    pluginTools = { binding ->
        context.pluginTools(binding).filter { workshopAgentToolVisible(it.name) && !it.name.startsWith("job_") } + jobTools(binding, registry)
    },
    extraToolFactories = context.extraToolFactories.map { factory ->
        { cwd, binding -> factory(cwd, binding).filter { workshopAgentToolVisible(it.name) && !it.name.startsWith("job_") } }
    },
)

internal fun workshopAgentEventOf(sessionEvent: SessionEvent): WorkshopAgentEvent? = when (sessionEvent) {
    is SessionEvent.AgentEventReceived -> workshopAgentEventOf(sessionEvent.event)
    is SessionEvent.SessionError -> WorkshopAgentEvent(
        type = WorkshopAgentEventType.RUN_ERROR,
        code = WorkshopAgentErrorCode.AGENT_FAILED,
        message = sessionEvent.message,
    )
    else -> null
}

internal fun workshopAgentEventOf(event: AgentEvent): WorkshopAgentEvent? = when (event) {
    is AgentEvent.MessageUpdate -> when (val update = event.assistantMessageEvent) {
        is TextDeltaEvent -> WorkshopAgentEvent(WorkshopAgentEventType.TEXT_DELTA, text = update.delta)
        is ThinkingDeltaEvent -> WorkshopAgentEvent(WorkshopAgentEventType.THINKING_DELTA, text = update.delta)
        else -> null
    }

    is AgentEvent.ToolExecutionStart -> WorkshopAgentEvent(
        type = WorkshopAgentEventType.TOOL_START,
        name = event.toolName,
    )

    is AgentEvent.ToolExecutionEnd -> WorkshopAgentEvent(
        type = WorkshopAgentEventType.TOOL_END,
        name = event.toolName,
        isError = event.isError,
        durationMs = event.durationMs,
    )

    is AgentEvent.TurnEnd -> WorkshopAgentEvent(type = WorkshopAgentEventType.TURN_END)
    else -> null
}

internal fun validateWorkshopAgentRequest(request: WorkshopAgentRequest) {
    if (request.prompt.isBlank()) {
        throw IllegalArgumentException("prompt must not be blank")
    }
    if (request.prompt.length > MAX_PROMPT_CHARS) {
        throw IllegalArgumentException("prompt must be at most $MAX_PROMPT_CHARS characters")
    }
    request.systemPrompt?.let { systemPrompt ->
        if (systemPrompt.length > MAX_SYSTEM_PROMPT_CHARS) {
            throw IllegalArgumentException("systemPrompt must be at most $MAX_SYSTEM_PROMPT_CHARS characters")
        }
    }
    request.maxTurns?.let { maxTurns ->
        if (maxTurns !in 1..MAX_TURNS_LIMIT) {
            throw IllegalArgumentException("maxTurns must be between 1 and $MAX_TURNS_LIMIT")
        }
    }
}

internal fun workshopAgentMaxTurns(maxTurns: Int?, default: Int = DEFAULT_MAX_TURNS): Int = maxTurns ?: default

internal fun resolveWorkshopAgentConfig(
    request: WorkshopAgentRequest,
    defaultConfig: ModelConfig,
    models: List<WorkshopAgentModel>,
    configuredProviders: Set<String>,
): ModelConfig {
    val providerId = request.providerId
    val modelId = request.modelId
    if ((providerId == null) != (modelId == null)) {
        throw IllegalArgumentException("providerId and modelId must be provided together")
    }
    val thinkingLevel = request.thinkingLevel
    if (providerId == null || modelId == null) {
        if (thinkingLevel == null) return defaultConfig
        val defaultModel = models.firstOrNull {
            it.providerId == defaultConfig.providerId && it.modelId == defaultConfig.modelId
        }
        if (defaultModel != null && thinkingLevel !in defaultModel.thinkingLevels) {
            throw IllegalArgumentException("thinkingLevel is not supported by this model: $thinkingLevel")
        }
        return defaultConfig.copy(thinkingLevel = parseWorkshopThinkingLevel(thinkingLevel))
    }
    if (providerId !in configuredProviders) {
        throw IllegalArgumentException("provider is not configured: $providerId")
    }
    val model = models.firstOrNull { it.providerId == providerId && it.modelId == modelId }
        ?: throw IllegalArgumentException("unknown model: $providerId/$modelId")
    if (thinkingLevel == null) {
        return ModelConfig(providerId, modelId, defaultConfig.thinkingLevel)
    }
    if (thinkingLevel !in model.thinkingLevels) {
        throw IllegalArgumentException("thinkingLevel is not supported by this model: $thinkingLevel")
    }
    return ModelConfig(providerId, modelId, parseWorkshopThinkingLevel(thinkingLevel))
}

internal fun parseWorkshopThinkingLevel(value: String): ThinkingLevel {
    val normalized = value.trim().lowercase()
    return ThinkingLevel.entries.firstOrNull { it.runtimeValue() == normalized }
        ?: throw IllegalArgumentException("unknown thinkingLevel: $value")
}

internal fun selectWorkshopAgentNames(
    kind: String,
    requested: List<String>?,
    available: Collection<String>,
): Set<String>? {
    if (requested == null) return null
    if (requested.size > MAX_SELECTION_ENTRIES) {
        throw IllegalArgumentException("$kind selection is limited to $MAX_SELECTION_ENTRIES entries")
    }
    val availableNames = available.toSet()
    requested.forEach { name ->
        if (name !in availableNames) {
            throw IllegalArgumentException("unknown $kind: $name")
        }
    }
    return requested.toSet()
}

internal fun workshopSkillDirectoryName(name: String): String {
    val sanitized = name.trim().replace(Regex("[^A-Za-z0-9._-]"), "_").trim('.')
    return sanitized.takeIf { it.isNotEmpty() } ?: "skill"
}

internal fun copySelectedSkills(skills: List<Skill>, skillsRoot: File, targetSkillsRoot: File) {
    if (skills.isEmpty()) return
    if (!targetSkillsRoot.isDirectory && !targetSkillsRoot.mkdirs() && !targetSkillsRoot.isDirectory) {
        throw IllegalStateException("cannot create workshop skill directory")
    }
    skills.forEach { skill ->
        val target = File(targetSkillsRoot, workshopSkillDirectoryName(skill.name)).apply { mkdirs() }
        val base = File(skill.baseDir).absoluteFile
        if (base == skillsRoot.absoluteFile) {
            File(skill.filePath).copyTo(File(target, SKILL_FILE), overwrite = true)
        } else {
            base.copyRecursively(target, overwrite = true)
        }
    }
}

internal fun writeAppendSystemPrompt(agentDir: File, systemPrompt: String?) {
    val content = systemPrompt?.trim().orEmpty()
    if (content.isEmpty()) return
    if (!agentDir.isDirectory && !agentDir.mkdirs() && !agentDir.isDirectory) {
        throw IllegalStateException("cannot create workshop agent directory")
    }
    val file = File(agentDir, APPEND_SYSTEM_PROMPT_FILE)
    val existing = if (file.isFile) file.readText().trim() else ""
    file.writeText(listOf(existing, content).filter { it.isNotEmpty() }.joinToString("\n\n"))
}
