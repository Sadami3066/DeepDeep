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
) {
    val cwd: Path = workspace.absolutePath.toPath()
    val agentDirectory: Path = agentDir.absolutePath.toPath()
    val paths = AgentPaths(cwd, agentDirectory)

    private val fs = FileSystem.SYSTEM
    private val transport = okHttpTransport()

    suspend fun createSession(
        file: Path?,
        config: ModelConfig,
        shellEnabled: Boolean = true,
        mcpEnabled: Boolean = true,
        toolsOverride: List<CodingTool>? = null,
    ): AgentSession {
        ensureSystemPromptFile(agentDirectory.toFile())
        val resolved = providers.resolveConfig(config)
        val modelId = resolved.modelId
        val activeShell = if (shellEnabled) shellExecutor else null
        val binding = SessionBinding()
        val tools = toolsOverride ?: buildList {
            addAll(
                defaultCodingTools(
                    cwd = cwd,
                    fs = fs,
                    mutationQueue = FileMutationQueue(),
                    shellExecutor = activeShell,
                ),
            )
            toolContext?.let { context ->
                addAll(appTools(cwd, context, binding))
            }
        }
        val session = createAgentSession(
            AgentSessionOptions(
                cwd = cwd,
                agentDir = agentDirectory,
                transport = transport,
                fs = fs,
                model = providers.modelReference(resolved.providerId, modelId),
                thinkingLevel = resolved.thinkingLevel.runtimeValue(),
                sessionFile = file,
                tools = tools,
                shellExecutor = activeShell,
                hooks = CodingHooks(
                    extraSystemPromptSections = {
                        val current = runCatching { personalization() }.getOrDefault(Personalization())
                        val sections = buildPersonalizationSections(
                            personalization = current,
                            providerId = resolved.providerId,
                            modelId = modelId,
                            cwd = cwd.toString(),
                            now = OffsetDateTime.now(),
                        )
                        val withShared = if (runCatching { sharedStorage() }.getOrDefault(false)) {
                            sections + sharedStorageSection()
                        } else {
                            sections
                        }
                        val pluginBody = runCatching { pluginPromptBody() }.getOrNull()?.trim()
                        if (pluginBody.isNullOrEmpty()) {
                            withShared
                        } else {
                            withShared + (
                                "official_plugins" to wrapPromptSection(
                                    "official_plugins",
                                    escapePromptXml(pluginBody),
                                )
                            )
                        }
                    },
                ),
                credentials = credentials,
                extraSystemPromptSections = buildToolRuleSection(tools),
                projectTrusted = true,
                mcp = if (mcpEnabled) {
                    McpOptions(
                        enabled = true,
                        loadConfig = true,
                        samplingEnabled = false,
                        rootsEnabled = true,
                        autoEnableToolSearch = true,
                    )
                } else {
                    McpOptions(enabled = false)
                },
            ),
        )
        if (toolsOverride == null) {
            binding.attach(session)
        }
        return session
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


private fun sharedStorageSection(): Map<String, String> = mapOf(
    "shared_storage" to wrapPromptSection(
        "shared_storage",
        listOf(
            "Android shared storage is mounted read-write at /storage/emulated/0; /sdcard points to the same place.",
            "Download, Documents, Pictures, DCIM, Music and Movies under it are readable and writable.",
            "Treat these as the user's real files: look before changing, and do not delete or overwrite anything unless the user asked for it.",
        ).joinToString("\n") { "- $it" },
    ),
)

fun ThinkingLevel.runtimeValue(): String = when (this) {
    ThinkingLevel.OFF -> "off"
    ThinkingLevel.LOW -> "low"
    ThinkingLevel.MEDIUM -> "medium"
    ThinkingLevel.HIGH -> "high"
    ThinkingLevel.MAX -> "max"
}

fun ThinkingLevel.displayName(): String = when (this) {
    ThinkingLevel.OFF -> "关闭"
    ThinkingLevel.LOW -> "低"
    ThinkingLevel.MEDIUM -> "中"
    ThinkingLevel.HIGH -> "高"
    ThinkingLevel.MAX -> "最大"
}
