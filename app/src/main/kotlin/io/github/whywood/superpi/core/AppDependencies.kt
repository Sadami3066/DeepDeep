package io.github.whywood.superpi.core

import io.github.whywood.superpi.conversation.ConversationStore
import io.github.whywood.superpi.data.AgentDocStore
import io.github.whywood.superpi.data.AppPreferences
import io.github.whywood.superpi.data.CustomProviderStore
import io.github.whywood.superpi.data.crash.CrashLogStore
import io.github.whywood.superpi.data.AttachmentStore
import io.github.whywood.superpi.data.DevPackageManager
import io.github.whywood.superpi.data.HostFiles
import io.github.whywood.superpi.data.LinuxSettingsStore
import io.github.whywood.superpi.data.StorageMountStore
import io.github.whywood.superpi.data.WorkspaceToolPolicyStore
import io.github.whywood.superpi.data.OverlayEntryPreferences
import io.github.whywood.superpi.data.PersonalizationStore
import io.github.whywood.superpi.data.ShareStaging
import io.github.whywood.superpi.data.SkillRepository
import io.github.whywood.superpi.data.VoicePreferences
import io.github.whywood.superpi.data.WebSearchSettingsStore
import io.github.whywood.superpi.data.remote.RemoteConnection
import io.github.whywood.superpi.data.relay.RelayController
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

interface AppDependencies :
    RuntimeDependencies,
    StorageDependencies,
    FeatureDependencies,
    ServiceDependencies,
    AdvancedDependencies

interface AdvancedDependencies {
    val crashLogs: CrashLogStore

    val hostAccess: HostAccessController

    val hostEnvironmentProvider: HostEnvironmentProvider

    val rootfsBootstrap: RootfsBootstrap
}

interface RuntimeDependencies {
    val host: AgentRuntimeHost

    val conversationStore: ConversationStore

    suspend fun prepare()
}

interface StorageDependencies {
    val homeDir: File

    val attachmentStore: AttachmentStore

    val hostFiles: HostFiles

    val modelConfigStore: ModelSettings

    val skillRepository: SkillRepository

    val personalizationStore: PersonalizationStore

    val appPreferences: AppPreferences

    val voicePreferences: VoicePreferences

    val overlayEntryPreferences: OverlayEntryPreferences

    val linuxSettings: LinuxSettingsStore

    val storageMounts: StorageMountStore

    val workspaceToolPolicies: WorkspaceToolPolicyStore

    val shareStaging: ShareStaging

    val miniappStore: MiniappStore

    val mcpConfigStore: McpConfigStore

    val agentDocStore: AgentDocStore


    val webSearchSettings: WebSearchSettingsStore
}

interface FeatureDependencies {
    val linuxEnvironment: LinuxEnvironment

    val devPackageManager: DevPackageManager

    val skillCatalog: SkillCatalog

    val pluginManager: PluginManagerFacade

    val themeController: ThemeController

    val marketRepository: MarketRepository

    val questionCenter: QuestionCenter

    val goalService: GoalService

    val jobRegistry: JobRegistry

    val timerService: TimerService

    val memory: MemoryStore

    val memoryRecap: MemoryRecap

    val remoteConnection: RemoteConnection

    val relayController: RelayController

    val updateManager: UpdateManager
}

interface ServiceDependencies {
    val providerCatalog: ProviderDirectory

    val customProviderStore: CustomProviderStore

    fun reloadCustomProviders()

    fun configuredProviderIds(): Set<String>

    val credentials: SdkCredentialStore

    val providerOAuthService: ProviderOAuthService

    val mcpServerProbe: McpServerProbe

    val mcpOAuthService: McpOAuthService

    val miniappAgentService: MiniappAgentService

    val processRuntime: ProcessRuntime
}

internal fun resolveAttachmentRoot(cwd: String, homeDir: File): File =
    cwd.takeIf { it.isNotBlank() }?.let(::File) ?: homeDir
