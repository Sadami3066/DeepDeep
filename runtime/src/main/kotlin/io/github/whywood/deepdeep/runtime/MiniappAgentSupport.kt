package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.sdk.agent.AgentEvent
import io.github.whywood.deepdeep.sdk.ai.TextDeltaEvent
import io.github.whywood.deepdeep.sdk.ai.ThinkingDeltaEvent
import io.github.whywood.deepdeep.sdk.coding.prompt.Skill
import io.github.whywood.deepdeep.sdk.coding.session.SessionEvent
import io.github.whywood.deepdeep.sdk.coding.tools.CodingTool
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


internal fun miniappResourceFileSystem(workspace: File, agentDir: File, temporaryAgentDir: File): FileSystem =
    object : ForwardingFileSystem(FileSystem.SYSTEM) {
        private val projectSkills = workspace.absolutePath.toPath() / ".pi" / "skills"
        private val temporaryRoot = temporaryAgentDir.absolutePath.toPath()
        private val sharedRoot = agentDir.absolutePath.toPath()
        override fun list(dir: Path): List<Path> = if (dir == projectSkills) emptyList() else super.list(dir)
        override fun listOrNull(dir: Path): List<Path>? = if (dir == projectSkills) emptyList() else super.listOrNull(dir)
        override fun onPathParameter(path: Path, functionName: String, parameterName: String): Path =
            if (path.parent == temporaryRoot && path.name in setOf("mcp-auth.json", "mcp-auth.json.tmp")) sharedRoot / path.name else path
    }

internal fun buildMiniappAgentModels(
    providers: ProviderCatalog,
    configuredProviders: Set<String>,
): List<MiniappAgentModel> = configuredProviders
    .flatMap { providerId ->
        providers.models(providerId).map { option ->
            MiniappAgentModel(
                providerId = option.providerId,
                modelId = option.id,
                label = option.displayName,
                thinkingLevels = option.thinkingLevels.map { it.runtimeValue() },
            )
        }
    }
    .sortedWith(compareBy({ it.providerId }, { it.modelId }))

internal fun buildMiniappAgentTools(tools: List<CodingTool>): List<MiniappAgentTool> =
    visibleMiniappAgentTools(tools).map { MiniappAgentTool(it.name, it.description) }.sortedBy { it.name }

internal fun visibleMiniappAgentTools(tools: List<CodingTool>): List<CodingTool> =
    tools.filter { miniappAgentToolVisible(it.name) }

internal fun miniappAgentToolVisible(name: String): Boolean = name !in ONE_SHOT_UNAVAILABLE_TOOLS

internal fun oneShotToolContext(context: AppToolContext, registry: JobRegistry): AppToolContext = context.copy(
    pluginTools = { binding ->
        context.pluginTools(binding).filter { miniappAgentToolVisible(it.name) && !it.name.startsWith("job_") } + jobTools(binding, registry)
    },
    extraToolFactories = context.extraToolFactories.map { factory ->
        { cwd, binding -> factory(cwd, binding).filter { miniappAgentToolVisible(it.name) && !it.name.startsWith("job_") } }
    },
)

internal fun miniappAgentEventOf(sessionEvent: SessionEvent): MiniappAgentEvent? = when (sessionEvent) {
    is SessionEvent.AgentEventReceived -> miniappAgentEventOf(sessionEvent.event)
    is SessionEvent.SessionError -> MiniappAgentEvent(
        type = MiniappAgentEventType.RUN_ERROR,
        code = MiniappAgentErrorCode.AGENT_FAILED,
        message = sessionEvent.message,
    )
    else -> null
}

internal fun miniappAgentEventOf(event: AgentEvent): MiniappAgentEvent? = when (event) {
    is AgentEvent.MessageUpdate -> when (val update = event.assistantMessageEvent) {
        is TextDeltaEvent -> MiniappAgentEvent(MiniappAgentEventType.TEXT_DELTA, text = update.delta)
        is ThinkingDeltaEvent -> MiniappAgentEvent(MiniappAgentEventType.THINKING_DELTA, text = update.delta)
        else -> null
    }

    is AgentEvent.ToolExecutionStart -> MiniappAgentEvent(
        type = MiniappAgentEventType.TOOL_START,
        name = event.toolName,
    )

    is AgentEvent.ToolExecutionEnd -> MiniappAgentEvent(
        type = MiniappAgentEventType.TOOL_END,
        name = event.toolName,
        isError = event.isError,
        durationMs = event.durationMs,
    )

    is AgentEvent.TurnEnd -> MiniappAgentEvent(type = MiniappAgentEventType.TURN_END)
    else -> null
}

internal fun validateMiniappAgentRequest(request: MiniappAgentRequest) {
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

internal fun miniappAgentMaxTurns(maxTurns: Int?, default: Int = DEFAULT_MAX_TURNS): Int = maxTurns ?: default

internal fun resolveMiniappAgentConfig(
    request: MiniappAgentRequest,
    defaultConfig: ModelConfig,
    models: List<MiniappAgentModel>,
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
        return defaultConfig.copy(thinkingLevel = parseMiniappThinkingLevel(thinkingLevel))
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
    return ModelConfig(providerId, modelId, parseMiniappThinkingLevel(thinkingLevel))
}

internal fun parseMiniappThinkingLevel(value: String): ThinkingLevel {
    val normalized = value.trim().lowercase()
    return ThinkingLevel.entries.firstOrNull { it.runtimeValue() == normalized }
        ?: throw IllegalArgumentException("unknown thinkingLevel: $value")
}

internal fun selectMiniappAgentNames(
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

internal fun miniappSkillDirectoryName(name: String): String {
    val sanitized = name.trim().replace(Regex("[^A-Za-z0-9._-]"), "_").trim('.')
    return sanitized.takeIf { it.isNotEmpty() } ?: "skill"
}

internal fun copySelectedSkills(skills: List<Skill>, skillsRoot: File, targetSkillsRoot: File) {
    if (skills.isEmpty()) return
    if (!targetSkillsRoot.isDirectory && !targetSkillsRoot.mkdirs() && !targetSkillsRoot.isDirectory) {
        throw IllegalStateException("cannot create miniapp skill directory")
    }
    skills.forEach { skill ->
        val target = File(targetSkillsRoot, miniappSkillDirectoryName(skill.name)).apply { mkdirs() }
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
        throw IllegalStateException("cannot create miniapp agent directory")
    }
    val file = File(agentDir, APPEND_SYSTEM_PROMPT_FILE)
    val existing = if (file.isFile) file.readText().trim() else ""
    file.writeText(listOf(existing, content).filter { it.isNotEmpty() }.joinToString("\n\n"))
}
