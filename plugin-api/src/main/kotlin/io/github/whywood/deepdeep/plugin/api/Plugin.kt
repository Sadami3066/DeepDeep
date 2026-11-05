package io.github.whywood.deepdeep.plugin.api

import android.content.Context
import java.io.File
import kotlinx.coroutines.CoroutineScope

interface DeepDeepPlugin {
    suspend fun availability(host: PluginHost): PluginAvailability

    suspend fun status(host: PluginHost): PluginStatus

    suspend fun install(host: PluginHost, progress: PluginProgress) = Unit

    suspend fun uninstall(host: PluginHost) = Unit

    suspend fun onEnable(host: PluginHost) = Unit

    suspend fun onDisable(host: PluginHost) = Unit

    suspend fun onUpgrade(host: PluginHost, fromVersion: String) = Unit

    suspend fun setup(host: PluginHost, ui: PluginSetupUi) = Unit

    fun actions(host: PluginHost, status: PluginStatus): List<PluginAction> = emptyList()

    suspend fun runAction(host: PluginHost, actionId: String, ui: PluginSetupUi) = Unit

    fun detailRows(host: PluginHost, status: PluginStatus): List<PluginDetailRow> = emptyList()

    fun skills(host: PluginHost): List<PluginSkill> = emptyList()

    fun promptSections(host: PluginHost): List<PromptSection> = emptyList()

    fun settings(): List<SettingSpec> = emptyList()

    suspend fun onSettingsChanged(host: PluginHost, values: Map<String, String>) = Unit

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

    fun log(level: Int, message: String)

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

    fun workshop(): WorkshopService

    fun mcp(): McpService

    fun ui(): UiService

    fun app(): AppInfoService

    fun bus(): PluginBus

    fun logs(): LogService

    fun chat(): ChatService = throw PluginServiceUnavailable("chat")
}

data class PluginSessionInfo(
    val sessionId: String,
    val cwd: String,
    val file: String? = null,
    val supportsChatDelivery: Boolean = true,
)

sealed interface PluginAvailability {
    data object Available : PluginAvailability

    data class Unavailable(val reason: String) : PluginAvailability
}

sealed interface PluginStatus {
    val label: String

    data class Ready(
        override val label: String,
        val account: String? = null,
    ) : PluginStatus

    data class NotReady(override val label: String) : PluginStatus

    data class Failed(override val label: String) : PluginStatus
}

interface PluginProgress {
    fun report(percent: Int?, label: LocalizedText)
}

interface PluginSetupUi {
    fun stage(
        title: LocalizedText,
        message: LocalizedText? = null,
        url: String? = null,
        imagePath: String? = null,
        progress: Int? = null,
    )

    fun progress(percent: Int?, label: LocalizedText)

    fun cancelled(): Boolean
}

enum class PluginActionStyle {
    NORMAL,
    DESTRUCTIVE,
}

data class PluginAction(
    val id: String,
    val label: LocalizedText,
    val style: PluginActionStyle = PluginActionStyle.NORMAL,
    val enabled: Boolean = true,
)

data class PluginDetailRow(
    val label: LocalizedText,
    val value: String,
)

data class PluginSkill(
    val id: String,
    val markdown: String,
    val files: List<PluginSkillFile> = emptyList(),
    val legacyOwned: Boolean = false,
)

data class PluginSkillFile(
    val path: String,
    val content: String? = null,
    val assetPath: String? = null,
)

data class PromptSection(
    val id: String,
    val body: String,
    val priority: Int = 0,
)

val PluginStatus.isReady: Boolean
    get() = this is PluginStatus.Ready
