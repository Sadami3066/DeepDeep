package io.github.whywood.deepdeep.runtime

import dev.pi.agent.AgentMessage
import dev.pi.ai.Model
import dev.pi.ai.auth.CredentialStore
import dev.pi.ai.http.HttpTransport
import dev.pi.coding.compaction.buildSessionProjection
import dev.pi.coding.compaction.estimateProjectedContextTokens
import dev.pi.coding.AgentSessionOptions
import dev.pi.coding.createAgentSession
import dev.pi.coding.mcp.McpConfigLoader
import dev.pi.coding.mcp.McpOptions
import dev.pi.coding.paths.AgentPaths
import dev.pi.coding.settings.SettingsManager
import dev.pi.mcp.config.McpServerConfig
import dev.pi.coding.session.AgentSession
import dev.pi.coding.session.CodingHooks
import dev.pi.coding.session.QueueBehavior
import dev.pi.coding.session.SessionEvent
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
    private val transport: HttpTransport,
    private val shellExecutor: ShellExecutor?,
    private val providers: ProviderCatalog,
    private val personalization: suspend () -> Personalization = { Personalization() },
    private val sharedStorage: suspend () -> Boolean = { false },
    private val pluginPromptBody: suspend () -> String? = { null },
    private val toolContext: AppToolContext? = null,
    private val toolFs: FileSystem = FileSystem.SYSTEM,
    private val fs: FileSystem = toolFs,
    private val supportsChatDelivery: Boolean = true,
    internal val hooks: RuntimeHooks = RuntimeHooks.None,
) : SessionEngine {
    val cwd: Path = workspace.absolutePath.toPath()
    val agentDirectory: Path = agentDir.absolutePath.toPath()
    val paths = AgentPaths(cwd, agentDirectory)

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
        val binding = SessionBinding(supportsChatDelivery)
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
                extra = mapOf(
                    "paths" to wrapPromptSection(
                        "paths",
                        "技能目录：${agentDirectory / "skills"}。App 私有数据目录：${agentDirectory.parent?.parent ?: agentDirectory}（/data/data 等价），bash 与文件工具按同一真实路径读写。",
                    ),
                ) + extraSystemPromptSections + buildToolRuleSection(tools),
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
                hooks = CodingHooks(extraSystemPromptSections = { currentSections() })
                    .withPluginHooks(hooks.promptTransform),
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
        session.agent.installToolExecutionHooks(hooks.toolHooks)
        binding.attach(session)
        hooks.onSessionBound?.invoke(binding)
        return session
    }

    internal suspend fun prepareInput(text: String, images: List<ImagePart>): PreparedPrompt {
        val handler = hooks.inputHandler ?: return PreparedPrompt(text, images)
        return when (val decision = handler(InputRequest(text, images))) {
            is InputDecision.Continue -> PreparedPrompt(decision.text, decision.images)
            InputDecision.Handled -> PreparedPrompt.Handled
        }
    }

    override suspend fun create(file: String?, config: ModelConfig): SessionHandle =
        PiSessionHandle(this, createSession(file?.toPath(), config))

    fun availableTools(): List<CodingTool> = buildTools(shellExecutor, SessionBinding(supportsChatDelivery))

    private fun buildTools(activeShell: ShellExecutor?, binding: SessionBinding): List<CodingTool> = buildList {
        val defaults = defaultCodingTools(
            cwd = cwd,
            fs = toolFs,
            mutationQueue = FileMutationQueue(),
            shellExecutor = null,
        )
        defaults.forEach { tool ->
            when (tool.name) {
                "bash" -> activeShell?.let { add(deepDeepBashTool(it)) }
                "ls", "grep", "find" -> Unit
                else -> add(tool.withAbsolutePaths())
            }
        }
        toolContext?.let { context ->
            addAll(appTools(cwd, context, binding))
        }
    }

    override suspend fun loadMcpServers(): Map<String, McpServerConfig> {
        val settings = SettingsManager.forPaths(paths, fs)
        val loaded = McpConfigLoader(paths, fs, settings).load()
        return loaded.servers.toMap()
    }

    suspend fun listSessions(): List<ConversationSummary> = SessionManager.list(paths, fs).map { info ->
        info.toSummary()
    }

    fun history(session: AgentSession): List<AssistantMessage> =
        HistoryMapper.mapEntries(session.sessionManager.getBranch())

    suspend fun deleteSessionFile(filePath: String) {
        runCatching { fs.delete(filePath.toPath()) }
    }

    override fun titleFor(text: String): String {
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

internal sealed interface PreparedPrompt {
    data class Ready(
        val text: String,
        val images: List<ImagePart>,
    ) : PreparedPrompt

    data object Handled : PreparedPrompt

    companion object {
        operator fun invoke(text: String, images: List<ImagePart>): PreparedPrompt = Ready(text, images)
    }
}

internal class PiSessionHandle(
    private val engine: PiSessionEngine,
    private val session: AgentSession,
) : SessionHandle {
    override val sessionId: String get() = session.sessionId

    override val sessionFile: String? get() = session.sessionFile?.toString()

    override val isBusy: Boolean get() = session.isBusy

    override val isStreaming: Boolean get() = session.isStreaming

    override val messages: List<AgentMessage> get() = session.messages

    override val activeToolNames: List<String> get() = session.activeToolNames

    override val model: Model get() = session.model

    override val thinkingLevel: String get() = session.thinkingLevel

    override suspend fun prompt(
        text: String,
        images: List<ImagePart>?,
        behavior: QueueBehavior?,
    ) {
        when (val prepared = engine.prepareInput(text, images.orEmpty())) {
            is PreparedPrompt.Ready -> session.prompt(prepared.text, prepared.images.toImageContents(), behavior)
            PreparedPrompt.Handled -> Unit
        }
    }

    override fun abort() {
        session.abort()
    }

    override suspend fun close() {
        val sessionId = session.sessionId
        session.close()
        engine.hooks.onSessionClosed?.let { it(sessionId) }
    }

    override fun subscribe(listener: suspend (SessionEvent) -> Unit): () -> Unit =
        session.subscribe(listener)

    override fun clearQueues() {
        session.agent.clearAllQueues()
    }

    override fun canRetryLastAssistant(): Boolean = session.canRetryLastAssistant()

    override suspend fun retryLastAssistant() {
        session.retryLastAssistant()
    }

    override fun lastAssistantText(): String? = session.getLastAssistantText()

    override suspend fun setModel(model: Model, thinkingLevel: String?) {
        session.setModel(model, thinkingLevel)
    }

    override suspend fun setThinkingLevel(level: String) {
        session.setThinkingLevel(level)
    }

    override suspend fun compact(): SessionCompaction? = session.compact()?.let { result ->
        SessionCompaction(result.tokensBefore, result.estimatedTokensAfter)
    }

    override fun history(): List<AssistantMessage> = engine.history(session)

    override fun usage(): SessionUsage? = runCatching {
        val manager = session.sessionManager
        val branch = manager.getBranch()
        val stats = manager.getSessionStats()
        SessionUsage(
            contextTokens = estimateProjectedContextTokens(buildSessionProjection(branch), branch).tokens,
            contextWindow = session.model.contextWindow,
            inputTokens = stats.inputTokens,
            outputTokens = stats.outputTokens,
            cacheReadTokens = stats.cacheReadTokens,
            cacheWriteTokens = stats.cacheWriteTokens,
            cost = stats.totalCost,
        )
    }.getOrNull()

    override fun sessionName(): String? = session.sessionManager.getSessionName()

    override suspend fun appendTitle(title: String) {
        session.sessionManager.appendSessionInfo(title)
    }

    override suspend fun appendCustomMessage(customType: String, payloadJson: String, text: String, display: Boolean) {
        val details = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(payloadJson) }.getOrNull()
        session.appendCustomMessage(customType, dev.pi.ai.PlainTextContent(text), display, details)
    }

    override suspend fun reloadMcp(servers: Map<String, McpServerConfig>) {
        session.mcp.reload(servers)
    }

    override suspend fun reconnectMcp(server: String) {
        session.mcp.reconnect(server)
    }

    override fun mcpServers(): List<SessionMcpServerStatus> = session.mcp.servers.map { status ->
        SessionMcpServerStatus(
            name = status.name,
            state = status.state.name,
            toolCount = status.toolCount,
            lastError = status.lastError,
        )
    }

    override fun deferredMcpToolCount(): Int = session.mcp.deferredCatalog().size
}
