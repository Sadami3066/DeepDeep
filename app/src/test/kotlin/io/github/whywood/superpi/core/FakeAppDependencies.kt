package io.github.whywood.superpi.core

import io.github.whywood.superpi.conversation.ConversationStore
import io.github.whywood.superpi.data.AgentDocStore
import io.github.whywood.superpi.data.AppPreferences
import io.github.whywood.superpi.data.AttachmentStore
import io.github.whywood.superpi.data.CustomProviderStore
import io.github.whywood.superpi.data.crash.CrashLogStore
import io.github.whywood.superpi.data.DevPackageManager
import io.github.whywood.superpi.data.HostFiles
import io.github.whywood.superpi.data.LinuxSettingsStore
import io.github.whywood.superpi.data.OverlayEntryPreferences
import io.github.whywood.superpi.data.PersonalizationStore
import io.github.whywood.superpi.data.ShareStaging
import io.github.whywood.superpi.data.SkillRepository
import io.github.whywood.superpi.data.StorageMountStore
import io.github.whywood.superpi.data.WorkspaceToolPolicyStore
import io.github.whywood.superpi.data.VoicePreferences
import io.github.whywood.superpi.data.WebSearchSettingsStore
import io.github.whywood.superpi.data.remote.RemoteConnection
import io.github.whywood.superpi.data.update.UpdateManager
import io.github.whywood.superpi.market.MarketRepository
import io.github.whywood.superpi.plugin.manager.PluginManagerFacade
import io.github.whywood.superpi.runtime.AgentRuntimeHost
import io.github.whywood.superpi.runtime.host.HostAccessController
import io.github.whywood.superpi.runtime.host.HostEnvironmentProvider
import io.github.whywood.superpi.runtime.GoalService
import io.github.whywood.superpi.runtime.JobRegistry
import io.github.whywood.superpi.runtime.McpConfigStore
import io.github.whywood.superpi.runtime.McpOAuthService
import io.github.whywood.superpi.runtime.McpServerProbe
import io.github.whywood.superpi.runtime.ModelSettings
import io.github.whywood.superpi.runtime.ProviderDirectory
import io.github.whywood.superpi.runtime.ProviderOAuthService
import io.github.whywood.superpi.runtime.QuestionCenter
import io.github.whywood.superpi.runtime.TimerService
import io.github.whywood.superpi.runtime.SdkCredentialStore
import io.github.whywood.superpi.runtime.SkillCatalog
import io.github.whywood.superpi.runtime.MiniappAgentService
import io.github.whywood.superpi.runtime.MiniappStore
import io.github.whywood.superpi.runtime.memory.MemoryRecap
import io.github.whywood.superpi.runtime.memory.MemoryStore
import io.github.whywood.superpi.service.ProcessRuntime
import io.github.whywood.superpi.shell.LinuxEnvironment
import io.github.whywood.superpi.shell.RootfsBootstrap
import io.github.whywood.superpi.theme.ThemeController
import java.io.File

internal class FakeAppDependencies(
    override val homeDir: File = File("fake-home"),
) : AppDependencies {
    override val crashLogs: CrashLogStore = CrashLogStore(File(homeDir, "crash"))

    override val host: AgentRuntimeHost get() = unavailable("host")

    override val conversationStore: ConversationStore get() = unavailable("conversationStore")

    override val memory: MemoryStore get() = unavailable("memory")

    override val memoryRecap: MemoryRecap get() = unavailable("memoryRecap")

    override val storageMounts: StorageMountStore get() = unavailable("storageMounts")

    override val workspaceToolPolicies: WorkspaceToolPolicyStore get() = unavailable("workspaceToolPolicies")

    override val remoteConnection: RemoteConnection get() = unavailable("remoteConnection")

    override val relayController: io.github.whywood.superpi.data.relay.RelayController get() = unavailable("relayController")

    override val relayLive: io.github.whywood.superpi.data.relay.RelayLive get() = unavailable("relayLive")


    override val updateManager: UpdateManager get() = unavailable("updateManager")

    override suspend fun prepare() {
        unavailable("prepare")
    }

    override val attachmentStore: AttachmentStore get() = unavailable("attachmentStore")

    override val hostFiles: HostFiles get() = unavailable("hostFiles")

    override val modelConfigStore: ModelSettings get() = unavailable("modelConfigStore")

    override val skillRepository: SkillRepository get() = unavailable("skillRepository")

    override val personalizationStore: PersonalizationStore get() = unavailable("personalizationStore")

    override val appPreferences: AppPreferences get() = unavailable("appPreferences")

    override val voicePreferences: VoicePreferences get() = unavailable("voicePreferences")

    override val overlayEntryPreferences: OverlayEntryPreferences
        get() = unavailable("overlayEntryPreferences")

    override val linuxSettings: LinuxSettingsStore get() = unavailable("linuxSettings")

    override val shareStaging: ShareStaging get() = unavailable("shareStaging")

    override val miniappStore: MiniappStore get() = unavailable("miniappStore")

    override val mcpConfigStore: McpConfigStore get() = unavailable("mcpConfigStore")

    override val agentDocStore: AgentDocStore get() = unavailable("agentDocStore")


    override val webSearchSettings: WebSearchSettingsStore get() = unavailable("webSearchSettings")

    override val linuxEnvironment: LinuxEnvironment get() = unavailable("linuxEnvironment")

    override val devPackageManager: DevPackageManager get() = unavailable("devPackageManager")

    override val skillCatalog: SkillCatalog get() = unavailable("skillCatalog")

    override val pluginManager: PluginManagerFacade get() = unavailable("pluginManager")

    override val themeController: ThemeController get() = unavailable("themeController")

    override val marketRepository: MarketRepository get() = unavailable("marketRepository")

    override val questionCenter: QuestionCenter get() = unavailable("questionCenter")

    override val goalService: GoalService get() = unavailable("goalService")

    override val jobRegistry: JobRegistry get() = unavailable("jobRegistry")

    override val timerService: TimerService get() = unavailable("timerService")

    override val providerCatalog: ProviderDirectory get() = unavailable("providerCatalog")

    override val customProviderStore: CustomProviderStore get() = unavailable("customProviderStore")

    override fun configuredProviderIds(): Set<String> = unavailable("configuredProviderIds")

    override fun reloadCustomProviders(): Nothing = unavailable("reloadCustomProviders")

    override val credentials: SdkCredentialStore get() = unavailable("credentials")

    override val providerOAuthService: ProviderOAuthService get() = unavailable("providerOAuthService")

    override val mcpServerProbe: McpServerProbe get() = unavailable("mcpServerProbe")

    override val mcpOAuthService: McpOAuthService get() = unavailable("mcpOAuthService")

    override val miniappAgentService: MiniappAgentService get() = unavailable("miniappAgentService")

    override val processRuntime: ProcessRuntime get() = unavailable("processRuntime")

    override val hostAccess: HostAccessController get() = unavailable("hostAccess")

    override val hostEnvironmentProvider: HostEnvironmentProvider
        get() = unavailable("hostEnvironmentProvider")

    override val rootfsBootstrap: RootfsBootstrap get() = unavailable("rootfsBootstrap")

    private fun unavailable(member: String): Nothing =
        error("FakeAppDependencies.$member 不可用；测试只应访问已注入的成员")
}
