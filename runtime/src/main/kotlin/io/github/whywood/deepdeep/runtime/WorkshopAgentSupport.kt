package io.github.whywood.deepdeep.runtime

import dev.pi.agent.AgentEvent
import dev.pi.ai.TextDeltaEvent
import dev.pi.ai.ThinkingDeltaEvent
import dev.pi.coding.prompt.Skill
import dev.pi.coding.session.SessionEvent
import dev.pi.coding.tools.CodingTool
import java.io.File
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Path
import okio.Path.Companion.toPath

private const val MAX_PROMPT_CHARS = 64 * 1024
private const val MAX_SYSTEM_PROMPT_CHARS = 16 * 1024
internal const val DEFAULT_MAX_TURNS = 16
private const val MAX_TURNS_LIMIT = 64
private const val MAX_SELECTION_ENTRIES = 100
internal const val RUN_TIMEOUT_MS = 180_000L
internal const val SETTLE_TIMEOUT_MS = 2_000L
internal const val SKILL_FILE = "SKILL.md"
internal const val SKILLS_DIR = "skills"
internal const val AGENT_DIR = "agent"
internal const val APPEND_SYSTEM_PROMPT_FILE = "APPEND_SYSTEM.md"


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
