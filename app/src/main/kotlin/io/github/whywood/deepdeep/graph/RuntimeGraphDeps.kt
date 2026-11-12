package io.github.whywood.deepdeep.graph

import android.content.Context
import io.github.whywood.deepdeep.sdk.ai.http.HttpTransport
import io.github.whywood.deepdeep.sdk.coding.tools.CodingTool
import io.github.whywood.deepdeep.conversation.ConversationState
import io.github.whywood.deepdeep.data.AppPreferences
import io.github.whywood.deepdeep.data.CapabilitySettingsStore
import io.github.whywood.deepdeep.data.LinuxSettingsStore
import io.github.whywood.deepdeep.data.StorageMountStore
import io.github.whywood.deepdeep.data.WorkspaceToolPolicyStore
import io.github.whywood.deepdeep.data.PersonalizationStore
import io.github.whywood.deepdeep.plugin.manager.PluginHostManager
import io.github.whywood.deepdeep.purge.PurgeController
import io.github.whywood.deepdeep.runtime.GoalService
import io.github.whywood.deepdeep.runtime.JobRegistry
import io.github.whywood.deepdeep.runtime.MiniappStore
import io.github.whywood.deepdeep.runtime.ModelConfigStore
import io.github.whywood.deepdeep.runtime.ProviderCatalog
import io.github.whywood.deepdeep.runtime.QuestionCenter
import io.github.whywood.deepdeep.runtime.SdkCredentialStore
import io.github.whywood.deepdeep.runtime.SessionCatalog
import io.github.whywood.deepdeep.runtime.TimerService
import io.github.whywood.deepdeep.runtime.WorkspaceStore
import io.github.whywood.deepdeep.runtime.host.HostAccessController
import io.github.whywood.deepdeep.runtime.host.HostEnvironmentProvider
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
    val questionCenter: QuestionCenter,
    val goalService: GoalService,
    val miniappStore: MiniappStore,
    val pluginManager: () -> PluginHostManager,
    val purge: () -> PurgeController,
    val hostAccess: HostAccessController,
    val hostEnvironment: HostEnvironmentProvider,
    val decorateInterrupted: suspend (ConversationState, String) -> ConversationState,
    val timerService: () -> TimerService,
    val jobRegistry: () -> JobRegistry,
    val capabilitySettingsStore: CapabilitySettingsStore,
    val appPreferences: AppPreferences,
    val apiKeyProvider: suspend () -> String?,
    val webSearchKeyProvider: suspend (String) -> String?,
    val postCustomMessage: suspend (String, String, String, String) -> Boolean,
    val screenTools: () -> List<CodingTool>,
    val virtualScreenTools: () -> List<CodingTool>,
)
