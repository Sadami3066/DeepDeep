package io.github.whywood.deepdeep.plugin.api

import android.content.Context
import java.io.File
import kotlinx.coroutines.CoroutineScope

public interface DeepDeepPlugin {
    public suspend fun availability(host: PluginHost): PluginAvailability

    public suspend fun uninstall(host: PluginHost): Unit = Unit

    public suspend fun onEnable(host: PluginHost): Unit = Unit

    public suspend fun onDisable(host: PluginHost): Unit = Unit

    public suspend fun onUpgrade(host: PluginHost, fromVersion: String): Unit = Unit

    public fun skills(host: PluginHost): List<PluginSkill> = emptyList()

    public fun promptSections(host: PluginHost): List<PromptSection> = emptyList()

    public fun contributions(host: PluginHost): Contributions = contributions()

    public fun contributionsFor(host: PluginHost, session: PluginSessionInfo?): Contributions =
        contributions(host)

    public fun ui(host: PluginHost): PluginUiContributions = PluginUiContributions.None

    public suspend fun close(): Unit = Unit
}

public interface PluginHost {
    public val manifest: PluginManifest

    public val context: Context

    public val dataDir: File

    public val assetsDir: File

    public val scope: CoroutineScope

    public fun text(source: LocalizedText, vararg args: Any): String

    public fun linux(): LinuxService

    public fun screen(): ScreenService

    public fun notifications(): NotificationService

    public fun secrets(): SecretService

    public fun network(): NetworkService

    public fun files(): FileService

    public fun sessions(): SessionService

    public fun jobs(): JobService

    public fun timers(): TimerService

    public fun goals(): GoalService

    public fun miniapp(): MiniappService

    public fun mcp(): McpService

    public fun ui(): UiService

    public fun app(): AppInfoService

    public fun bus(): PluginBus

    public fun logs(): LogService

    public fun chat(): ChatService = throw PluginServiceUnavailable("chat")

    public fun settings(): PluginSettingsService

    public fun device(): DeviceService

    public fun clipboard(): ClipboardService

    public fun permissions(): PermissionService

    public fun packages(): PackageService

    public fun privilege(): PrivilegeService

    public fun intents(): IntentsService

    @PluginExperimentalApi
    public fun components(): ComponentService

    public fun background(): BackgroundService

    public fun location(): LocationService

    public fun contacts(): ContactsService

    public fun sms(): SmsService

    public fun calendar(): CalendarService

    public fun media(): MediaService

    public fun speech(): SpeechService

    public fun web(): WebService

    @PluginExperimentalApi
    public fun credentials(): CredentialService

    @PluginExperimentalApi
    public fun accessibility(): AccessibilityServicePlugin

    public fun requestContributionsRefresh()
}

public data class PluginSessionSnapshot(
    public val sessionId: String,
    public val file: String?,
    public val cwd: String,
)

public interface PluginSessionInfo {
    public val supportsChatDelivery: Boolean

    public fun snapshot(): PluginSessionSnapshot?
}

public interface PluginAvailability {
    public val available: Boolean

    public val reason: String?
}

public fun available(): PluginAvailability = PluginAvailabilityImpl(available = true, reason = null)

public fun unavailable(reason: String): PluginAvailability =
    PluginAvailabilityImpl(available = false, reason = reason)

internal data class PluginAvailabilityImpl(
    override val available: Boolean,
    override val reason: String?,
) : PluginAvailability

public val PluginAvailability.isReady: Boolean
    get() = available

public interface PluginSkill {
    public val id: String
    public val markdown: String
    public val files: List<PluginSkillFile>
}

public fun pluginSkill(
    id: String,
    markdown: String,
    files: List<PluginSkillFile> = emptyList(),
): PluginSkill = PluginSkillImpl(id, markdown, files)

internal data class PluginSkillImpl(
    override val id: String,
    override val markdown: String,
    override val files: List<PluginSkillFile>,
) : PluginSkill

public interface PluginSkillFile {
    public val path: String
    public val content: String?
    public val assetPath: String?
}

public fun pluginSkillFile(
    path: String,
    content: String? = null,
    assetPath: String? = null,
): PluginSkillFile = PluginSkillFileImpl(path, content, assetPath)

internal data class PluginSkillFileImpl(
    override val path: String,
    override val content: String?,
    override val assetPath: String?,
) : PluginSkillFile

public interface PromptSection {
    public val id: String
    public val body: String
    public val priority: Int
}

public fun promptSection(id: String, body: String, priority: Int = 0): PromptSection =
    PromptSectionImpl(id, body, priority)

internal data class PromptSectionImpl(
    override val id: String,
    override val body: String,
    override val priority: Int,
) : PromptSection

@PluginExperimentalApi
public interface PromptOverride {
    public val preamble: String?
    public val rules: String?
    public val sections: List<PromptSection>
    public val priority: Int
}

@PluginExperimentalApi
public fun promptOverride(
    preamble: String? = null,
    rules: String? = null,
    sections: List<PromptSection> = emptyList(),
    priority: Int = 0,
): PromptOverride = PromptOverrideImpl(preamble, rules, sections, priority)

@OptIn(PluginExperimentalApi::class)
internal data class PromptOverrideImpl(
    override val preamble: String?,
    override val rules: String?,
    override val sections: List<PromptSection>,
    override val priority: Int,
) : PromptOverride

@PluginExperimentalApi
public interface PromptOverridePlugin {
    public fun promptOverride(host: PluginHost): PromptOverride
}
