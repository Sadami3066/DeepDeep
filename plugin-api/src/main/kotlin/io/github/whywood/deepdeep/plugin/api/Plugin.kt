package io.github.whywood.deepdeep.plugin.api

import android.content.Context
import java.io.File
import kotlinx.coroutines.CoroutineScope

interface DeepDeepPlugin {
    suspend fun availability(host: PluginHost): PluginAvailability

    suspend fun uninstall(host: PluginHost) = Unit

    suspend fun onEnable(host: PluginHost) = Unit

    suspend fun onDisable(host: PluginHost) = Unit

    suspend fun onUpgrade(host: PluginHost, fromVersion: String) = Unit

    fun skills(host: PluginHost): List<PluginSkill> = emptyList()

    fun promptSections(host: PluginHost): List<PromptSection> = emptyList()

    fun contributions(host: PluginHost): Contributions = Contributions()

    fun contributionsFor(host: PluginHost, session: PluginSessionInfo?): Contributions = contributions(host)

    fun ui(host: PluginHost): PluginUiContributions = PluginUiContributions.None

    suspend fun close() = Unit
}

interface PluginHost {
    val manifest: PluginManifest

    val context: Context

    val dataDir: File

    val assetsDir: File

    val scope: CoroutineScope

    fun text(source: LocalizedText, vararg args: Any): String

    fun linux(): LinuxService

    fun privilege(): PrivilegeService

    fun screen(): ScreenService

    fun notifications(): NotificationService

    fun secrets(): SecretService

    fun network(): NetworkService

    fun files(): FileService

    fun sessions(): SessionService

    fun jobs(): JobService

    fun timers(): TimerService

    fun goals(): GoalService

    fun miniapp(): MiniappService

    fun mcp(): McpService

    fun ui(): UiService

    fun app(): AppInfoService

    fun bus(): PluginBus

    fun logs(): LogService

    fun chat(): ChatService = throw PluginServiceUnavailable("chat")

    fun requestContributionsRefresh()
}

data class PluginSessionSnapshot(
    val sessionId: String,
    val file: String?,
    val cwd: String,
)

class PluginSessionInfo(
    val supportsChatDelivery: Boolean,
    private val lookup: () -> PluginSessionSnapshot?,
) {
    fun snapshot(): PluginSessionSnapshot? = lookup()
}

sealed interface PluginAvailability {
    data object Available : PluginAvailability

    data class Unavailable(val reason: String) : PluginAvailability
}

val PluginAvailability.isReady: Boolean
    get() = this is PluginAvailability.Available

data class PluginSkill(
    val id: String,
    val markdown: String,
    val files: List<PluginSkillFile> = emptyList(),
)

data class PluginSkillFile(
    val path: String,
    val content: String? = null,
    val assetPath: String? = null,
)

/**
 * 插件注入系统提示词的一段正文。
 *
 * [body] 为纯文本或 Markdown，会直接拼进 `<plugins>` 段（与其他插件正文之间空一行）。
 * 不要写 XML 标签：整段内容会被转义为实体。不要用 `#` 一级标题：`<plugins>` 已是顶层段，正文标题请从 `##` 开始。
 */
data class PromptSection(
    val id: String,
    val body: String,
    val priority: Int = 0,
)
