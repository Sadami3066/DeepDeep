package io.github.whywood.deepdeep.plugin.host.services

import android.content.Context
import android.os.Build
import io.github.whywood.deepdeep.sdk.ai.http.HttpTransport
import io.github.whywood.deepdeep.data.LanguagePreferences
import io.github.whywood.deepdeep.plugin.api.McpCallResult
import io.github.whywood.deepdeep.plugin.api.McpTool
import io.github.whywood.deepdeep.plugin.api.PluginBus
import io.github.whywood.deepdeep.plugin.api.PluginLogLevel
import io.github.whywood.deepdeep.plugin.api.ShareRequest
import io.github.whywood.deepdeep.plugin.host.PluginServices
import io.github.whywood.deepdeep.plugin.host.extensions.data.DataPluginServices
import io.github.whywood.deepdeep.plugin.host.extensions.device.DevicePluginServices
import io.github.whywood.deepdeep.plugin.host.interaction.PluginInteraction
import io.github.whywood.deepdeep.vscreen.VirtualScreenFeature
import io.github.whywood.deepdeep.runtime.ConversationQueries
import io.github.whywood.deepdeep.runtime.EncryptedSecretStore
import io.github.whywood.deepdeep.runtime.GoalService
import io.github.whywood.deepdeep.runtime.JobRegistry
import io.github.whywood.deepdeep.runtime.McpConfigStore
import io.github.whywood.deepdeep.runtime.McpServerProbe
import io.github.whywood.deepdeep.runtime.Maintenance
import io.github.whywood.deepdeep.runtime.SessionCommands
import io.github.whywood.deepdeep.runtime.TimerService
import io.github.whywood.deepdeep.runtime.MiniappAgentService
import io.github.whywood.deepdeep.runtime.MiniappStore
import io.github.whywood.deepdeep.runtime.host.HostEnvironmentProvider
import io.github.whywood.deepdeep.screen.ScreenController
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject
import okio.FileSystem

internal data class PluginServiceDependencies(
    val context: Context,
    val appContext: Context,
    val scope: CoroutineScope,
    val pluginId: String = "",
    val pluginFirstPageId: String? = null,
    val pluginDataDir: File = File(context.filesDir, "plugins/$pluginId/data"),
    val pluginCacheDir: File = File(context.cacheDir, "plugins/$pluginId"),
    val workspaceDir: File? = null,
    val sharedStorageDir: File? = null,
    val versionName: String = "0.0.0",
    val versionCode: Long = 0L,
    val language: () -> String = { LanguagePreferences(context).read().tag },
    val installationId: suspend () -> String = { "" },
    val hostEnvironment: HostEnvironmentProvider? = null,
    val defaultWorkspace: () -> String = { pluginDataDir.absolutePath },
    val bashAvailable: () -> Boolean = { false },
    val files: () -> FileSystem = { FileSystem.SYSTEM },
    val secrets: EncryptedSecretStore? = null,
    val network: HttpTransport? = null,
    val sessions: ConversationQueries? = null,
    val sessionCommands: SessionCommands? = null,
    val maintenance: Maintenance? = null,
    val activeSessionId: StateFlow<String?>? = null,
    val jobs: JobRegistry? = null,
    val timers: TimerService? = null,
    val goals: GoalService? = null,
    val miniappStore: MiniappStore? = null,
    val miniappAgent: MiniappAgentService? = null,
    val miniappMaxTurns: () -> Int = { 16 },
    val mcpConfig: McpConfigStore? = null,
    val mcpProbe: McpServerProbe? = null,
    val mcpTools: (suspend (String) -> List<McpTool>)? = null,
    val mcpCall: (suspend (String, String, JsonObject) -> McpCallResult)? = null,
    val screen: ScreenController? = null,
    val mainScreen: ScreenController? = null,
    val virtualScreen: VirtualScreenFeature? = null,
    val screenshotDir: File? = null,
    val openUrl: ((String) -> Unit)? = null,
    val share: ((ShareRequest) -> Unit)? = null,
    val toast: ((String) -> Unit)? = null,
    val openPage: ((String) -> Unit)? = null,
    val chatSink: (suspend (sessionId: String, customType: String, payload: JsonObject, text: String, display: Boolean) -> Boolean)? = null,
    val notificationsEnabled: () -> Boolean = {
        io.github.whywood.deepdeep.service.NotificationCenter.areNotificationsEnabled(context)
    },
    val bus: PluginBus? = null,
    val logSink: ((PluginLogLevel, String, Throwable?) -> Unit)? = null,
    val interaction: PluginInteraction? = null,
    val dataServices: DataPluginServices? = null,
    val deviceServices: DevicePluginServices? = null,
)

internal fun buildPluginServices(deps: PluginServiceDependencies): PluginServices {
    deps.pluginDataDir.mkdirs()
    deps.pluginCacheDir.mkdirs()
    val logs = LogPluginService(deps.pluginId, deps.logSink)
    return PluginServices(
        linux = deps.hostEnvironment?.let { hostEnvironment ->
            LinuxPluginService(
                hostEnvironment = hostEnvironment,
                defaultWorkspace = deps.defaultWorkspace,
                availableProvider = deps.bashAvailable,
            )
        },
        screen = if (deps.screen != null || deps.virtualScreen != null) {
            ScreenPluginService(
                virtual = deps.screen,
                main = deps.mainScreen,
                virtualScreen = deps.virtualScreen,
                screenshotDir = deps.screenshotDir,
                scope = deps.scope,
            )
        } else {
            null
        },
        notifications = NotificationPluginService(
            context = deps.context,
            pluginId = deps.pluginId,
            notificationsEnabled = deps.notificationsEnabled,
            firstPageId = deps.pluginFirstPageId,
        ),
        secrets = deps.secrets?.let { SecretPluginService(it, deps.pluginId) },
        network = deps.network?.let { NetworkPluginService(it) },
        files = FilePluginService(
            root = deps.pluginDataDir,
            workspace = deps.workspaceDir,
            sharedStorage = deps.sharedStorageDir,
            files = deps.files,
        ),
        sessions = if (deps.sessions != null && deps.activeSessionId != null) {
            SessionPluginService(
                runtime = deps.sessions,
                commands = deps.sessionCommands,
                activeSessionId = deps.activeSessionId,
                maintenance = deps.maintenance,
            )
        } else {
            null
        },
        jobs = deps.jobs?.let { JobPluginService(it, deps.activeSessionId) },
        timers = deps.timers?.let {
            TimerPluginService(
                timers = it,
                sessions = deps.sessions,
                activeSessionId = deps.activeSessionId,
            )
        },
        goals = deps.goals?.let { GoalPluginService(it) },
        miniapp = deps.miniappStore?.let {
            MiniappPluginService(
                store = it,
                agent = deps.miniappAgent,
                scope = deps.scope,
                maxTurns = deps.miniappMaxTurns,
            )
        },
        mcp = deps.mcpConfig?.let {
            McpPluginService(
                config = it,
                probe = deps.mcpProbe,
                toolsProvider = deps.mcpTools,
                caller = deps.mcpCall,
            )
        },
        ui = if (
            deps.openUrl != null || deps.share != null || deps.toast != null ||
            deps.openPage != null || deps.interaction != null
        ) {
            UiPluginService(
                openUrlCallback = deps.openUrl,
                shareCallback = deps.share,
                toastCallback = deps.toast,
                openPageCallback = deps.openPage,
                interaction = deps.interaction,
                context = deps.context,
            )
        } else {
            null
        },
        app = AppInfoPluginService(
            versionName = deps.versionName,
            versionCode = deps.versionCode,
            language = deps.language,
            sdkInt = { Build.VERSION.SDK_INT },
            deviceModel = { Build.MODEL },
            installationId = deps.installationId,
            scope = deps.scope,
        ),
        bus = deps.bus?.let { ScopedPluginBus(it, deps.pluginId) },
        logs = logs,
        chat = deps.chatSink?.let { sink ->
            deps.sessionCommands?.let { commands -> ChatPluginService(sink, commands) }
        },
        settings = deps.dataServices?.settings,
        location = deps.dataServices?.location,
        contacts = deps.dataServices?.contacts,
        sms = deps.dataServices?.sms,
        calendar = deps.dataServices?.calendar,
        media = deps.dataServices?.media,
        speech = deps.dataServices?.speech,
        web = deps.dataServices?.web,
        credentials = deps.dataServices?.credentials,
        accessibility = deps.dataServices?.accessibility,
        device = deps.deviceServices?.device,
        clipboard = deps.deviceServices?.clipboard,
        permissions = deps.deviceServices?.permissions,
        packages = deps.deviceServices?.packages,
        privilege = deps.deviceServices?.privilege,
        intents = deps.deviceServices?.intents,
        components = deps.deviceServices?.components,
        background = deps.deviceServices?.background,
    )
}
