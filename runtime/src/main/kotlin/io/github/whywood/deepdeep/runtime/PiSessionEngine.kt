package io.github.whywood.deepdeep.runtime

import dev.pi.ai.auth.CredentialStore
import dev.pi.ai.http.okHttpTransport
import dev.pi.coding.AgentSessionOptions
import dev.pi.coding.createAgentSession
import dev.pi.coding.mcp.McpConfigLoader
import dev.pi.coding.mcp.McpOptions
import dev.pi.coding.paths.AgentPaths
import dev.pi.coding.settings.SettingsManager
import dev.pi.mcp.config.McpServerConfig
import dev.pi.coding.session.AgentSession
import dev.pi.coding.session.CodingHooks
import dev.pi.coding.session.SessionInfo
import dev.pi.coding.session.SessionManager
import dev.pi.coding.tools.CodingTool
import dev.pi.coding.tools.FileMutationQueue
import dev.pi.coding.tools.ShellExecutor
import dev.pi.coding.tools.defaultCodingTools
import java.io.File
import java.time.OffsetDateTime
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

class PiSessionEngine(
    workspace: File,
    agentDir: File,
    private val credentials: CredentialStore,
    private val shellExecutor: ShellExecutor?,
    private val providers: ProviderCatalog,
    private val personalization: suspend () -> Personalization = { Personalization() },
    private val sharedStorage: suspend () -> Boolean = { false },
    private val pluginPromptBody: suspend () -> String? = { null },
    private val toolContext: AppToolContext? = null,
    private val toolFs: FileSystem = FileSystem.SYSTEM,
    private val fs: FileSystem = toolFs,
) {
    val cwd: Path = workspace.absolutePath.toPath()
    val agentDirectory: Path = agentDir.absolutePath.toPath()
    val paths = AgentPaths(cwd, agentDirectory)

    private val transport = okHttpTransport()

    suspend fun createSession(
        file: Path?,
        config: ModelConfig?,
        shellEnabled: Boolean = true,
        toolsFilter: ((CodingTool) -> Boolean)? = null,
        mcpEnabled: Boolean = true,
        extraSystemPromptSections: Map<String, String> = emptyMap(),
    ): AgentSession {
        ensureSystemPromptFile(agentDirectory.toFile())
        val fallback = providers.resolveConfig(config ?: ModelConfig())
        val restored = if (file == null) {
            null
        } else {
            runCatching { SessionManager.open(file, fs).buildSessionContext() }.getOrNull()
        }
        val restoredProviderId = restored?.provider?.takeIf { it.isNotBlank() }
        val restoredModelId = restored?.modelId?.takeIf { it.isNotBlank() }
        val restoredModel = restoredProviderId != null &&
            restoredModelId != null &&
            providers.sdkModel(restoredProviderId, restoredModelId) != null
        val explicitModel = if (restoredModel) null else fallback
        val providerId = if (restoredModel) restoredProviderId!! else fallback.providerId
        val modelId = if (restoredModel) restoredModelId!! else fallback.modelId
        val activeShell = if (shellEnabled) shellExecutor else null
        val binding = SessionBinding()
        val allTools = buildTools(activeShell, binding)
        val tools = if (toolsFilter == null) allTools else allTools.filter(toolsFilter)
        var created: AgentSession? = null
        val currentSections: suspend () -> Map<String, String> = {
            val session = created
            buildSessionSystemPromptSections(
                personalization = runCatching { personalization() }.getOrDefault(Personalization()),
                providerId = session?.model?.provider ?: providerId,
                modelId = session?.model?.id ?: modelId,
                cwd = cwd.toString(),
                now = OffsetDateTime.now(),
                sharedStorage = runCatching { sharedStorage() }.getOrDefault(false),
                pluginBody = runCatching { pluginPromptBody() }.getOrNull(),
                extra = extraSystemPromptSections,
            )
        }
        val session = createAgentSession(
            AgentSessionOptions(
                cwd = cwd,
                agentDir = agentDirectory,
                transport = transport,
                fs = fs,
                model = explicitModel?.let { providers.modelReference(it.providerId, it.modelId) },
                thinkingLevel = explicitModel?.thinkingLevel?.runtimeValue(),
                sessionFile = file,
                tools = tools,
                shellExecutor = activeShell,
                hooks = CodingHooks(extraSystemPromptSections = { currentSections() }),
                credentials = credentials,
                extraSystemPromptSections = buildToolRuleSection(tools),
                initialSystemPromptSections = currentSections(),
                projectTrusted = true,
                mcp = McpOptions(
                    enabled = mcpEnabled,
                    loadConfig = true,
                    samplingEnabled = false,
                    rootsEnabled = true,
                    autoEnableToolSearch = true,
                ),
            ),
        )
        created = session
        binding.attach(session)
        return session
    }

    fun availableTools(): List<CodingTool> = buildTools(shellExecutor, SessionBinding())

    private fun buildTools(activeShell: ShellExecutor?, binding: SessionBinding): List<CodingTool> = buildList {
        addAll(
            defaultCodingTools(
                cwd = cwd,
                fs = toolFs,
                mutationQueue = FileMutationQueue(),
                shellExecutor = activeShell,
            ),
        )
        toolContext?.let { context ->
            addAll(appTools(cwd, context, binding))
        }
    }

    suspend fun loadMcpServers(): Map<String, McpServerConfig> {
        val settings = SettingsManager.forPaths(paths, fs)
        val loaded = McpConfigLoader(paths, fs, settings).load()
        return loaded.servers.toMap()
    }

    suspend fun listSessions(): List<ConversationSummary> = SessionManager.list(paths, fs).map { info ->
        info.toSummary()
    }

    fun history(session: AgentSession): List<AssistantMessage> = HistoryMapper.mapHistory(session.messages)

    suspend fun deleteSessionFile(filePath: String) {
        runCatching { fs.delete(filePath.toPath()) }
    }

    fun titleFor(text: String): String {
        val normalized = text.trim().replace(Regex("\\s+"), " ")
        if (normalized.isEmpty()) return "新对话"
        return if (normalized.length <= 28) normalized else normalized.take(28) + "…"
    }

    private fun SessionInfo.toSummary(): ConversationSummary = ConversationSummary(
        id = sessionId,
        title = name?.takeIf { it.isNotBlank() } ?: "新对话",
        updatedAt = parseTimestamp(timestamp),
        filePath = sessionFile.toString(),
    )

    private fun parseTimestamp(value: String): Long = runCatching {
        OffsetDateTime.parse(value).toInstant().toEpochMilli()
    }.getOrDefault(0L)
}

fun ThinkingLevel.runtimeValue(): String = when (this) {
    ThinkingLevel.OFF -> "off"
    ThinkingLevel.LOW -> "low"
    ThinkingLevel.MEDIUM -> "medium"
    ThinkingLevel.HIGH -> "high"
    ThinkingLevel.MAX -> "max"
}

internal fun thinkingLevelOf(value: String?): ThinkingLevel = when (value?.trim()?.lowercase()) {
    "minimal", "low" -> ThinkingLevel.LOW
    "medium" -> ThinkingLevel.MEDIUM
    "high", "xhigh" -> ThinkingLevel.HIGH
    "max" -> ThinkingLevel.MAX
    else -> ThinkingLevel.OFF
}

fun ThinkingLevel.displayName(): String = when (this) {
    ThinkingLevel.OFF -> "关闭"
    ThinkingLevel.LOW -> "低"
    ThinkingLevel.MEDIUM -> "中"
    ThinkingLevel.HIGH -> "高"
    ThinkingLevel.MAX -> "最大"
}
