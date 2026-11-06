package io.github.whywood.deepdeep.plugin.host

import android.content.Context
import io.github.whywood.deepdeep.plugin.api.AppInfoService
import io.github.whywood.deepdeep.plugin.api.ChatService
import io.github.whywood.deepdeep.plugin.api.FileService
import io.github.whywood.deepdeep.plugin.api.GoalService
import io.github.whywood.deepdeep.plugin.api.JobService
import io.github.whywood.deepdeep.plugin.api.LinuxService
import io.github.whywood.deepdeep.plugin.api.LocalizedText
import io.github.whywood.deepdeep.plugin.api.LogService
import io.github.whywood.deepdeep.plugin.api.McpService
import io.github.whywood.deepdeep.plugin.api.NetworkService
import io.github.whywood.deepdeep.plugin.api.NotificationService
import io.github.whywood.deepdeep.plugin.api.PluginBus
import io.github.whywood.deepdeep.plugin.api.PluginHost
import io.github.whywood.deepdeep.plugin.api.PluginManifest
import io.github.whywood.deepdeep.plugin.api.PluginServiceUnavailable
import io.github.whywood.deepdeep.plugin.api.PrivilegeService
import io.github.whywood.deepdeep.plugin.api.ScreenService
import io.github.whywood.deepdeep.plugin.api.SecretService
import io.github.whywood.deepdeep.plugin.api.SessionService
import io.github.whywood.deepdeep.plugin.api.TimerService
import io.github.whywood.deepdeep.plugin.api.UiService
import io.github.whywood.deepdeep.plugin.api.MiniappService
import java.io.File
import kotlinx.coroutines.CoroutineScope

internal data class PluginServices(
    val linux: LinuxService? = null,
    val privilege: PrivilegeService? = null,
    val screen: ScreenService? = null,
    val notifications: NotificationService? = null,
    val secrets: SecretService? = null,
    val network: NetworkService? = null,
    val files: FileService? = null,
    val sessions: SessionService? = null,
    val jobs: JobService? = null,
    val timers: TimerService? = null,
    val goals: GoalService? = null,
    val miniapp: MiniappService? = null,
    val mcp: McpService? = null,
    val ui: UiService? = null,
    val app: AppInfoService? = null,
    val bus: PluginBus? = null,
    val logs: LogService? = null,
    val chat: ChatService? = null,
) {
    companion object {
        val None: PluginServices = PluginServices()
    }
}

internal class PluginHostImpl(
    override val manifest: PluginManifest,
    override val context: Context,
    override val scope: CoroutineScope,
    private val services: PluginServices,
    private val textResolver: PluginTextResolver,
    pluginsRoot: File,
    private val onRefreshRequest: () -> Unit = {},
) : PluginHost {
    override val dataDir: File = PluginPaths.dataDir(pluginsRoot, manifest.id)

    override val assetsDir: File = PluginPaths.assetsDir(pluginsRoot, manifest.id, manifest.version)

    override fun text(source: LocalizedText, vararg args: Any): String = textResolver.resolve(source, args)

    override fun linux(): LinuxService = service("linux", services.linux)

    override fun privilege(): PrivilegeService = service("privilege", services.privilege)

    override fun screen(): ScreenService = service("screen", services.screen)

    override fun notifications(): NotificationService = service("notifications", services.notifications)

    override fun secrets(): SecretService = service("secrets", services.secrets)

    override fun network(): NetworkService = service("network", services.network)

    override fun files(): FileService = service("files", services.files)

    override fun sessions(): SessionService = service("sessions", services.sessions)

    override fun jobs(): JobService = service("jobs", services.jobs)

    override fun timers(): TimerService = service("timers", services.timers)

    override fun goals(): GoalService = service("goals", services.goals)

    override fun miniapp(): MiniappService = service("miniapp", services.miniapp)

    override fun mcp(): McpService = service("mcp", services.mcp)

    override fun ui(): UiService = service("ui", services.ui)

    override fun app(): AppInfoService = service("app", services.app)

    override fun bus(): PluginBus = service("bus", services.bus)

    override fun logs(): LogService = service("logs", services.logs)

    override fun chat(): ChatService = service("chat", services.chat)

    override fun requestContributionsRefresh() = onRefreshRequest()

    private fun <T : Any> service(name: String, value: T?): T = value ?: throw PluginServiceUnavailable(name)
}

