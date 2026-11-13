package io.github.whywood.deepdeep.plugin.host

import android.content.Context
import io.github.whywood.deepdeep.plugin.api.AppInfoService
import io.github.whywood.deepdeep.plugin.api.AccessibilityServicePlugin
import io.github.whywood.deepdeep.plugin.api.BackgroundService
import io.github.whywood.deepdeep.plugin.api.CalendarService
import io.github.whywood.deepdeep.plugin.api.ChatService
import io.github.whywood.deepdeep.plugin.api.ClipboardService
import io.github.whywood.deepdeep.plugin.api.ComponentService
import io.github.whywood.deepdeep.plugin.api.ContactsService
import io.github.whywood.deepdeep.plugin.api.CredentialService
import io.github.whywood.deepdeep.plugin.api.DeviceService
import io.github.whywood.deepdeep.plugin.api.FileService
import io.github.whywood.deepdeep.plugin.api.IntentsService
import io.github.whywood.deepdeep.plugin.api.LocationService
import io.github.whywood.deepdeep.plugin.api.MediaService
import io.github.whywood.deepdeep.plugin.api.PackageService
import io.github.whywood.deepdeep.plugin.api.PermissionService
import io.github.whywood.deepdeep.plugin.api.PluginSettingsService
import io.github.whywood.deepdeep.plugin.api.PrivilegeService
import io.github.whywood.deepdeep.plugin.api.SmsService
import io.github.whywood.deepdeep.plugin.api.SpeechService
import io.github.whywood.deepdeep.plugin.api.WebService
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
import io.github.whywood.deepdeep.plugin.api.PluginCapability
import io.github.whywood.deepdeep.plugin.api.PluginManifest
import io.github.whywood.deepdeep.plugin.api.PluginServiceUnavailable
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
    val settings: PluginSettingsService? = null,
    val device: DeviceService? = null,
    val clipboard: ClipboardService? = null,
    val permissions: PermissionService? = null,
    val packages: PackageService? = null,
    val privilege: PrivilegeService? = null,
    val intents: IntentsService? = null,
    val components: ComponentService? = null,
    val background: BackgroundService? = null,
    val location: LocationService? = null,
    val contacts: ContactsService? = null,
    val sms: SmsService? = null,
    val calendar: CalendarService? = null,
    val media: MediaService? = null,
    val speech: SpeechService? = null,
    val web: WebService? = null,
    val credentials: CredentialService? = null,
    val accessibility: AccessibilityServicePlugin? = null,
) {
    companion object {
        val None: PluginServices = PluginServices()
    }
}

internal fun PluginServices.declaredOnly(capabilities: Set<PluginCapability>): PluginServices = copy(
    linux = linux.takeIf { PluginCapability.LINUX in capabilities },
    screen = screen.takeIf { PluginCapability.SCREEN in capabilities },
    notifications = notifications.takeIf { PluginCapability.NOTIFICATIONS in capabilities },
    secrets = secrets.takeIf { PluginCapability.SECRETS in capabilities },
    network = network.takeIf { PluginCapability.NETWORK in capabilities },
    files = files.takeIf { PluginCapability.FILES in capabilities },
    sessions = sessions.takeIf { PluginCapability.SESSIONS in capabilities },
    jobs = jobs.takeIf { PluginCapability.JOBS in capabilities },
    timers = timers.takeIf { PluginCapability.TIMERS in capabilities },
    goals = goals.takeIf { PluginCapability.GOALS in capabilities },
    miniapp = miniapp.takeIf { PluginCapability.MINIAPP in capabilities },
    mcp = mcp.takeIf { PluginCapability.MCP in capabilities },
    ui = ui.takeIf { PluginCapability.UI in capabilities },
    app = app.takeIf { PluginCapability.APP in capabilities },
    bus = bus.takeIf { PluginCapability.BUS in capabilities },
    logs = logs.takeIf { PluginCapability.LOGS in capabilities },
    chat = chat.takeIf { PluginCapability.CHAT in capabilities },
    settings = settings.takeIf { PluginCapability.SETTINGS in capabilities },
    device = device.takeIf { PluginCapability.DEVICE in capabilities },
    clipboard = clipboard.takeIf { PluginCapability.CLIPBOARD in capabilities },
    permissions = permissions.takeIf { PluginCapability.PERMISSIONS in capabilities },
    packages = packages.takeIf { PluginCapability.PACKAGES in capabilities },
    privilege = privilege.takeIf { PluginCapability.PRIVILEGE in capabilities },
    intents = intents.takeIf { PluginCapability.INTENTS in capabilities },
    components = components.takeIf { PluginCapability.COMPONENTS in capabilities },
    background = background.takeIf { PluginCapability.BACKGROUND in capabilities },
    location = location.takeIf { PluginCapability.LOCATION in capabilities },
    contacts = contacts.takeIf { PluginCapability.CONTACTS in capabilities },
    sms = sms.takeIf { PluginCapability.SMS in capabilities },
    calendar = calendar.takeIf { PluginCapability.CALENDAR in capabilities },
    media = media.takeIf { PluginCapability.MEDIA in capabilities },
    speech = speech.takeIf { PluginCapability.SPEECH in capabilities },
    web = web.takeIf { PluginCapability.WEB in capabilities },
    credentials = credentials.takeIf { PluginCapability.CREDENTIALS in capabilities },
    accessibility = accessibility.takeIf { PluginCapability.ACCESSIBILITY in capabilities },
)

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

    override fun settings(): PluginSettingsService = service("settings", services.settings)

    override fun device(): DeviceService = service("device", services.device)

    override fun clipboard(): ClipboardService = service("clipboard", services.clipboard)

    override fun permissions(): PermissionService = service("permissions", services.permissions)

    override fun packages(): PackageService = service("packages", services.packages)

    override fun privilege(): PrivilegeService = service("privilege", services.privilege)

    override fun intents(): IntentsService = service("intents", services.intents)

    override fun components(): ComponentService = service("components", services.components)

    override fun background(): BackgroundService = service("background", services.background)

    override fun location(): LocationService = service("location", services.location)

    override fun contacts(): ContactsService = service("contacts", services.contacts)

    override fun sms(): SmsService = service("sms", services.sms)

    override fun calendar(): CalendarService = service("calendar", services.calendar)

    override fun media(): MediaService = service("media", services.media)

    override fun speech(): SpeechService = service("speech", services.speech)

    override fun web(): WebService = service("web", services.web)

    override fun credentials(): CredentialService = service("credentials", services.credentials)

    override fun accessibility(): AccessibilityServicePlugin = service("accessibility", services.accessibility)

    override fun requestContributionsRefresh() = onRefreshRequest()

    private val declaredCapabilities: Set<PluginCapability> = manifest.capabilities

    private fun <T : Any> service(name: String, value: T?): T =
        declaredService(name, declaredCapabilities, value)
}

internal fun <T : Any> declaredService(name: String, declared: Set<PluginCapability>, value: T?): T {
    if (declared.none { it.wire == name }) throw PluginServiceUnavailable(name, "not declared in manifest")
    return value ?: throw PluginServiceUnavailable(name, "unavailable")
}

