package io.github.whywood.deepdeep.runtime

import dev.pi.agent.AgentEvent
import dev.pi.ai.auth.CredentialStore
import dev.pi.coding.prompt.Skill
import dev.pi.coding.prompt.loadSkills
import dev.pi.coding.session.AgentSession
import dev.pi.coding.session.SessionEvent
import dev.pi.coding.tools.CodingTool
import dev.pi.coding.tools.ShellExecutor
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import okio.FileSystem
import okio.Path.Companion.toPath

private const val MAX_PROMPT_CHARS = 64 * 1024
private const val MAX_SYSTEM_PROMPT_CHARS = 16 * 1024
private const val DEFAULT_MAX_TURNS = 16
private const val MAX_TURNS_LIMIT = 64
private const val MAX_SELECTION_ENTRIES = 100
private const val RUN_TIMEOUT_MS = 180_000L
private const val SKILL_FILE = "SKILL.md"
private const val SKILLS_DIR = "skills"
private const val WORKSPACE_DIR = "workspace"
private const val AGENT_DIR = "agent"
private const val APPEND_SYSTEM_PROMPT_FILE = "APPEND_SYSTEM.md"

class WorkshopAgentService(
    private val providers: ProviderCatalog,
    private val credentials: CredentialStore,
    private val agentDir: File,
    private val runRoot: File,
    private val appToolContext: AppToolContext,
    private val defaultConfig: suspend () -> ModelConfig,
    private val configuredProviders: () -> Set<String>,
    private val personalization: suspend () -> Personalization,
    private val pluginPromptBody: suspend () -> String?,
    private val shellExecutorFor: suspend (File) -> ShellExecutor?,
    private val sharedStorage: suspend () -> Boolean,
) : WorkshopAgentGateway {
    override suspend fun models(): List<WorkshopAgentModel> =
        buildWorkshopAgentModels(providers, configuredProviders())

    override suspend fun capabilities(): WorkshopAgentCapabilities {
        val root = createRunDirectory()
        try {
            val workspace = File(root, WORKSPACE_DIR).apply { mkdirs() }
            val tempAgentDir = File(root, AGENT_DIR).apply { mkdirs() }
            val engine = createEngine(workspace, tempAgentDir, null)
            val tools = buildWorkshopAgentTools(engine.availableTools())
            val skills = discoverSkills(workspace)
                .map { WorkshopAgentSkill(it.name, it.description) }
                .sortedBy { it.name }
            return WorkshopAgentCapabilities(tools, skills)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw IllegalStateException(error.message ?: "cannot read workshop agent capabilities", error)
        } finally {
            root.deleteRecursively()
        }
    }

    override suspend fun run(request: WorkshopAgentRequest): String {
        validateWorkshopAgentRequest(request)
        val config = resolveWorkshopAgentConfig(
            request = request,
            defaultConfig = defaultConfig(),
            models = models(),
            configuredProviders = configuredProviders(),
        )
        val maxTurns = workshopAgentMaxTurns(request.maxTurns)
        val root = createRunDirectory()
        var session: AgentSession? = null
        var unsubscribe: (() -> Unit)? = null
        try {
            val workspace = File(root, WORKSPACE_DIR).apply { mkdirs() }
            val tempAgentDir = File(root, AGENT_DIR).apply { mkdirs() }
            val engine = createEngine(workspace, tempAgentDir, shellExecutorFor(workspace))
            val tools = engine.availableTools()
            val skills = discoverSkills(workspace)
            val selectedTools = selectWorkshopAgentNames("tool", request.tools, tools.map { it.name })
            val selectedSkills = selectWorkshopAgentNames("skill", request.skills, skills.map { it.name })
            copySelectedSkills(
                skills = selectedSkills?.let { names -> skills.filter { it.name in names } } ?: skills,
                skillsRoot = File(agentDir, SKILLS_DIR),
                targetSkillsRoot = File(tempAgentDir, SKILLS_DIR),
            )
            writeAppendSystemPrompt(tempAgentDir, request.systemPrompt)
            val toolsFilter: ((CodingTool) -> Boolean)? = selectedTools?.let { names ->
                { tool: CodingTool -> tool.name in names }
            }
            val created = engine.createSession(
                file = null,
                config = config,
                shellEnabled = true,
                toolsFilter = toolsFilter,
                mcpEnabled = false,
            )
            session = created
            val turns = AtomicInteger(0)
            unsubscribe = created.subscribe { event ->
                val agentEvent = (event as? SessionEvent.AgentEventReceived)?.event
                if (agentEvent is AgentEvent.TurnEnd && turns.incrementAndGet() >= maxTurns) {
                    created.abort()
                }
            }
            withTimeout(RUN_TIMEOUT_MS) { created.prompt(request.prompt) }
            return created.getLastAssistantText() ?: throw IllegalStateException("agent returned no text")
        } catch (error: CancellationException) {
            throw error
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (error: IllegalStateException) {
            throw error
        } catch (error: Exception) {
            throw IllegalStateException(error.message ?: "workshop agent run failed", error)
        } finally {
            runCatching { unsubscribe?.invoke() }
            session?.let { created -> runCatching { created.close() } }
            root.deleteRecursively()
        }
    }

    private fun createEngine(
        workspace: File,
        tempAgentDir: File,
        shellExecutor: ShellExecutor?,
    ): PiSessionEngine = PiSessionEngine(
        workspace = workspace,
        agentDir = tempAgentDir,
        credentials = credentials,
        shellExecutor = shellExecutor,
        providers = providers,
        personalization = personalization,
        sharedStorage = sharedStorage,
        pluginPromptBody = pluginPromptBody,
        toolContext = appToolContext,
    )

    private suspend fun discoverSkills(cwd: File): List<Skill> = runCatching {
        loadSkills(
            cwd = cwd.absolutePath.toPath(),
            agentDir = agentDir.absolutePath.toPath(),
            fs = FileSystem.SYSTEM,
            projectTrusted = false,
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
    tools.map { WorkshopAgentTool(it.name, it.description) }.sortedBy { it.name }

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

internal fun workshopAgentMaxTurns(maxTurns: Int?): Int = maxTurns ?: DEFAULT_MAX_TURNS

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
    File(agentDir, APPEND_SYSTEM_PROMPT_FILE).writeText(content)
}
