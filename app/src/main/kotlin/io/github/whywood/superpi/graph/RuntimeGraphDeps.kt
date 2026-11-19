package io.github.whywood.superpi.graph

import android.content.Context
import io.github.whywood.deepdeep.sdk.ai.http.HttpTransport
import io.github.whywood.deepdeep.sdk.coding.tools.CodingTool
import io.github.whywood.superpi.conversation.ConversationState
import io.github.whywood.superpi.data.AppPreferences
import io.github.whywood.superpi.data.LinuxSettingsStore
import io.github.whywood.superpi.data.StorageMountStore
import io.github.whywood.superpi.data.SkillRepository
import io.github.whywood.superpi.data.WorkspaceToolPolicyStore
import io.github.whywood.superpi.data.PersonalizationStore
import io.github.whywood.superpi.plugin.manager.PluginHostManager
import io.github.whywood.superpi.runtime.GoalService
import io.github.whywood.superpi.runtime.JobRegistry
import io.github.whywood.superpi.runtime.MiniappStore
import io.github.whywood.superpi.runtime.McpConfigStore
import io.github.whywood.superpi.runtime.ModelConfigStore
import io.github.whywood.superpi.runtime.ProviderCatalog
import io.github.whywood.superpi.runtime.QuestionCenter
import io.github.whywood.superpi.runtime.SdkCredentialStore
import io.github.whywood.superpi.runtime.SessionCatalog
import io.github.whywood.superpi.runtime.TimerService
import io.github.whywood.superpi.runtime.WorkspaceStore
import io.github.whywood.superpi.runtime.host.HostAccessController
import io.github.whywood.superpi.runtime.host.HostEnvironmentProvider
import java.io.File
import kotlinx.coroutines.CoroutineScope

internal class RuntimeGraphDeps(
    val appContext: Context,
    val scope: CoroutineScope,
    val homeDir: File,
    val workspacesDir: File,
    val agentDir: File,
    val modelConfigStore: ModelConfigStore,
    val sessionCatalog: SessionCatalog,
    val workspaceStore: WorkspaceStore,
    val providerCatalog: ProviderCatalog,
    val credentials: SdkCredentialStore,
    val configuredProviderIds: () -> Set<String>,
    val httpTransport: HttpTransport,
    val personalizationStore: PersonalizationStore,
    val linuxSettings: LinuxSettingsStore,
    val storageMountStore: StorageMountStore,

    val workspaceToolPolicies: WorkspaceToolPolicyStore,
    val skillRepository: SkillRepository,
    val mcpConfigStore: McpConfigStore,
    val questionCenter: QuestionCenter,
    val goalService: GoalService,
    val miniappStore: MiniappStore,
    val pluginManager: () -> PluginHostManager,
    val hostAccess: HostAccessController,
    val hostEnvironment: HostEnvironmentProvider,
    val decorateInterrupted: suspend (ConversationState, String) -> ConversationState,
    val timerService: () -> TimerService,
    val jobRegistry: () -> JobRegistry,
    val appPreferences: AppPreferences,
    val apiKeyProvider: suspend () -> String?,
    val webSearchKeyProvider: suspend (String) -> String?,
    val postCustomMessage: suspend (String, String, String, String) -> Boolean,
    val screenTools: () -> List<CodingTool>,
    val virtualScreenTools: () -> List<CodingTool>,
)
