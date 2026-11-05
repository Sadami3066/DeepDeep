package io.github.whywood.deepdeep.plugin.host.services

import android.content.Context
import android.os.Build
import dev.pi.ai.http.HttpTransport
import io.github.whywood.deepdeep.adb.AdbBootstrap
import io.github.whywood.deepdeep.data.LanguagePreferences
import io.github.whywood.deepdeep.plugin.api.McpCallResult
import io.github.whywood.deepdeep.plugin.api.McpTool
import io.github.whywood.deepdeep.plugin.api.PluginBus
import io.github.whywood.deepdeep.plugin.api.PluginLogLevel
import io.github.whywood.deepdeep.plugin.api.ShareRequest
import io.github.whywood.deepdeep.plugin.host.PluginServices
import io.github.whywood.deepdeep.plugin.virtualscreen.VirtualScreenFeature
import io.github.whywood.deepdeep.privilege.PrivilegeController
import io.github.whywood.deepdeep.runtime.ConversationQueries
import io.github.whywood.deepdeep.runtime.EncryptedSecretStore
import io.github.whywood.deepdeep.runtime.GoalService
import io.github.whywood.deepdeep.runtime.JobRegistry
import io.github.whywood.deepdeep.runtime.McpConfigStore
import io.github.whywood.deepdeep.runtime.McpServerProbe
import io.github.whywood.deepdeep.runtime.SessionCommands
import io.github.whywood.deepdeep.runtime.TimerService
import io.github.whywood.deepdeep.runtime.WorkshopAgentService
import io.github.whywood.deepdeep.runtime.WorkshopStore
import io.github.whywood.deepdeep.screen.ScreenController
import io.github.whywood.deepdeep.shell.LinuxEnvironment
import io.github.whywood.deepdeep.shell.RootShell
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject

data class PluginServiceDependencies(
    val context: Context,
    val scope: CoroutineScope,
    val pluginId: String = "",
    val pluginDataDir: File = File(context.filesDir, "plugins/$pluginId/data"),
    val pluginCacheDir: File = File(context.cacheDir, "plugins/$pluginId"),
    val workspaceDir: File? = null,
    val sharedStorageDir: File? = null,
    val versionName: String = "0.0.0",
    val versionCode: Long = 0L,
    val language: () -> String = { LanguagePreferences(context).read().tag },
    val installationId: suspend () -> String = { "" },
    val linux: LinuxEnvironment? = null,
    val privilege: PrivilegeController? = null,
    val rootShell: RootShell? = null,
    val adbBridge: AdbBootstrap? = null,
    val secrets: EncryptedSecretStore? = null,
    val network: HttpTransport? = null,
    val sessions: ConversationQueries? = null,
    val sessionCommands: SessionCommands? = null,
    val activeSessionId: StateFlow<String?>? = null,
    val jobs: JobRegistry? = null,
    val timers: TimerService? = null,
    val goals: GoalService? = null,
    val workshopStore: WorkshopStore? = null,
    val workshopAgent: WorkshopAgentService? = null,
    val workshopMaxTurns: () -> Int = { 16 },
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
    val notificationsEnabled: () -> Boolean = {
        io.github.whywood.deepdeep.service.NotificationCenter.areNotificationsEnabled(context)
    },
    val bus: PluginBus? = null,
    val logSink: ((PluginLogLevel, String, Throwable?) -> Unit)? = null,
)

internal fun buildPluginServices(deps: PluginServiceDependencies): PluginServices {
    deps.pluginDataDir.mkdirs()
    deps.pluginCacheDir.mkdirs()
    return PluginServices(
        linux = deps.linux?.let { LinuxPluginService(it) },
        privilege = deps.privilege?.let {
            PrivilegePluginService(
                privilege = it,
                rootShell = deps.rootShell,
                adbBridge = deps.adbBridge,
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
        ),
        secrets = deps.secrets?.let { SecretPluginService(it, deps.pluginId) },
        network = deps.network?.let { NetworkPluginService(it) },
        files = FilePluginService(
            root = deps.pluginDataDir,
            workspace = deps.workspaceDir,
            sharedStorage = deps.sharedStorageDir,
        ),
        sessions = if (deps.sessions != null && deps.activeSessionId != null) {
            SessionPluginService(
                runtime = deps.sessions,
                commands = deps.sessionCommands,
                activeSessionId = deps.activeSessionId,
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
        workshop = deps.workshopStore?.let {
            WorkshopPluginService(
                store = it,
                agent = deps.workshopAgent,
                scope = deps.scope,
                maxTurns = deps.workshopMaxTurns,
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
            deps.openUrl != null || deps.share != null || deps.toast != null || deps.openPage != null
        ) {
            UiPluginService(
                openUrlCallback = deps.openUrl,
                shareCallback = deps.share,
                toastCallback = deps.toast,
                openPageCallback = deps.openPage,
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
        logs = LogPluginService(deps.pluginId, deps.logSink),
    )
}
