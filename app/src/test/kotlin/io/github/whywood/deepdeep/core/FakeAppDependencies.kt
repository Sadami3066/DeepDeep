package io.github.whywood.deepdeep.core

import io.github.whywood.deepdeep.data.AgentDocStore
import io.github.whywood.deepdeep.data.AppPreferences
import io.github.whywood.deepdeep.data.AttachmentStore
import io.github.whywood.deepdeep.data.DevPackageManager
import io.github.whywood.deepdeep.data.LinuxSettingsStore
import io.github.whywood.deepdeep.data.OverlayEntryPreferences
import io.github.whywood.deepdeep.data.PersonalizationStore
import io.github.whywood.deepdeep.data.ShareStaging
import io.github.whywood.deepdeep.data.SkillRepository
import io.github.whywood.deepdeep.data.VoicePreferences
import io.github.whywood.deepdeep.plugin.PluginManager
import io.github.whywood.deepdeep.privilege.PrivilegeController
import io.github.whywood.deepdeep.runtime.AgentRuntimeHost
import io.github.whywood.deepdeep.runtime.GoalService
import io.github.whywood.deepdeep.runtime.JobRegistry
import io.github.whywood.deepdeep.runtime.McpConfigStore
import io.github.whywood.deepdeep.runtime.McpOAuthService
import io.github.whywood.deepdeep.runtime.McpServerProbe
import io.github.whywood.deepdeep.runtime.ModelSettings
import io.github.whywood.deepdeep.runtime.ProviderDirectory
import io.github.whywood.deepdeep.runtime.ProviderOAuthService
import io.github.whywood.deepdeep.runtime.QuestionCenter
import io.github.whywood.deepdeep.runtime.TimerService
import io.github.whywood.deepdeep.runtime.SdkCredentialStore
import io.github.whywood.deepdeep.runtime.SkillCatalog
import io.github.whywood.deepdeep.runtime.WorkshopAgentService
import io.github.whywood.deepdeep.runtime.WorkshopStore
import io.github.whywood.deepdeep.shell.LinuxEnvironment
import java.io.File

internal class FakeAppDependencies(
    override val homeDir: File = File("fake-home"),
) : AppDependencies {
    override val host: AgentRuntimeHost get() = unavailable("host")

    override suspend fun prepare() {
        unavailable("prepare")
    }

    override val attachmentStore: AttachmentStore get() = unavailable("attachmentStore")

    override val modelConfigStore: ModelSettings get() = unavailable("modelConfigStore")

    override val skillRepository: SkillRepository get() = unavailable("skillRepository")

    override val personalizationStore: PersonalizationStore get() = unavailable("personalizationStore")

    override val appPreferences: AppPreferences get() = unavailable("appPreferences")

    override val voicePreferences: VoicePreferences get() = unavailable("voicePreferences")

    override val overlayEntryPreferences: OverlayEntryPreferences
        get() = unavailable("overlayEntryPreferences")

    override val linuxSettings: LinuxSettingsStore get() = unavailable("linuxSettings")

    override val shareStaging: ShareStaging get() = unavailable("shareStaging")

    override val workshopStore: WorkshopStore get() = unavailable("workshopStore")

    override val mcpConfigStore: McpConfigStore get() = unavailable("mcpConfigStore")

    override val agentDocStore: AgentDocStore get() = unavailable("agentDocStore")

    override val linuxEnvironment: LinuxEnvironment get() = unavailable("linuxEnvironment")

    override val devPackageManager: DevPackageManager get() = unavailable("devPackageManager")

    override val skillCatalog: SkillCatalog get() = unavailable("skillCatalog")

    override val pluginManager: PluginManager get() = unavailable("pluginManager")

    override val questionCenter: QuestionCenter get() = unavailable("questionCenter")

    override val goalService: GoalService get() = unavailable("goalService")

    override val jobRegistry: JobRegistry get() = unavailable("jobRegistry")

    override val timerService: TimerService get() = unavailable("timerService")

    override val providerCatalog: ProviderDirectory get() = unavailable("providerCatalog")

    override val credentials: SdkCredentialStore get() = unavailable("credentials")

    override val providerOAuthService: ProviderOAuthService get() = unavailable("providerOAuthService")

    override val mcpServerProbe: McpServerProbe get() = unavailable("mcpServerProbe")

    override val mcpOAuthService: McpOAuthService get() = unavailable("mcpOAuthService")

    override val workshopAgentService: WorkshopAgentService get() = unavailable("workshopAgentService")

    override val privilege: PrivilegeController get() = unavailable("privilege")

    private fun unavailable(member: String): Nothing =
        error("FakeAppDependencies.$member 不可用；测试只应访问已注入的成员")
}
