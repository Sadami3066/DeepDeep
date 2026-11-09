package io.github.whywood.deepdeep.core

import io.github.whywood.deepdeep.conversation.ConversationStore
import io.github.whywood.deepdeep.data.AgentDocStore
import io.github.whywood.deepdeep.data.AppPreferences
import io.github.whywood.deepdeep.data.AttachmentStore
import io.github.whywood.deepdeep.data.CapabilitySettingsStore
import io.github.whywood.deepdeep.data.DevPackageManager
import io.github.whywood.deepdeep.data.LinuxSettingsStore
import io.github.whywood.deepdeep.data.StorageMountStore
import io.github.whywood.deepdeep.data.WorkspaceToolPolicyStore
import io.github.whywood.deepdeep.data.OverlayEntryPreferences
import io.github.whywood.deepdeep.data.PersonalizationStore
import io.github.whywood.deepdeep.data.ShareStaging
import io.github.whywood.deepdeep.data.SkillRepository
import io.github.whywood.deepdeep.data.VoicePreferences
import io.github.whywood.deepdeep.data.WebSearchSettingsStore
import io.github.whywood.deepdeep.data.remote.RemoteConnection
import io.github.whywood.deepdeep.market.MarketRepository
import io.github.whywood.deepdeep.plugin.manager.PluginManagerFacade
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
import io.github.whywood.deepdeep.runtime.MiniappAgentService
import io.github.whywood.deepdeep.runtime.MiniappStore
import io.github.whywood.deepdeep.runtime.memory.MemoryRecap
import io.github.whywood.deepdeep.runtime.memory.MemoryStore
import io.github.whywood.deepdeep.service.ProcessRuntime
import io.github.whywood.deepdeep.service.RootBashController
import io.github.whywood.deepdeep.shell.LinuxEnvironment
import java.io.File

interface AppDependencies :
    RuntimeDependencies,
    StorageDependencies,
    FeatureDependencies,
    ServiceDependencies,
    AdvancedDependencies

interface AdvancedDependencies {
    val privilege: PrivilegeController

    val rootBash: RootBashController
}

interface RuntimeDependencies {
    val host: AgentRuntimeHost

    val conversationStore: ConversationStore

    suspend fun prepare()
}

interface StorageDependencies {
    val homeDir: File

    val attachmentStore: AttachmentStore

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

    val capabilitySettingsStore: CapabilitySettingsStore

    val webSearchSettings: WebSearchSettingsStore
}

interface FeatureDependencies {
    val linuxEnvironment: LinuxEnvironment

    val devPackageManager: DevPackageManager

    val skillCatalog: SkillCatalog

    val pluginManager: PluginManagerFacade

    val marketRepository: MarketRepository

    val questionCenter: QuestionCenter

    val goalService: GoalService

    val jobRegistry: JobRegistry

    val timerService: TimerService

    val memory: MemoryStore

    val memoryRecap: MemoryRecap

    val remoteConnection: RemoteConnection
}

interface ServiceDependencies {
    val providerCatalog: ProviderDirectory

    val credentials: SdkCredentialStore

    val providerOAuthService: ProviderOAuthService

    val mcpServerProbe: McpServerProbe

    val mcpOAuthService: McpOAuthService

    val miniappAgentService: MiniappAgentService

    val processRuntime: ProcessRuntime
}

internal fun resolveAttachmentRoot(cwd: String, homeDir: File): File =
    cwd.takeIf { it.isNotBlank() }?.let(::File) ?: homeDir
