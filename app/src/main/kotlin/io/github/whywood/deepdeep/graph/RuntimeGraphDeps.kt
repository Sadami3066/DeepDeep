package io.github.whywood.deepdeep.graph

import android.content.Context
import dev.pi.ai.http.HttpTransport
import dev.pi.coding.tools.CodingTool
import io.github.whywood.deepdeep.conversation.ConversationState
import io.github.whywood.deepdeep.data.CapabilitySettingsStore
import io.github.whywood.deepdeep.data.LinuxSettingsStore
import io.github.whywood.deepdeep.data.PersonalizationStore
import io.github.whywood.deepdeep.plugin.manager.PluginHostManager
import io.github.whywood.deepdeep.privilege.PrivilegeController
import io.github.whywood.deepdeep.runtime.GoalService
import io.github.whywood.deepdeep.runtime.JobRegistry
import io.github.whywood.deepdeep.runtime.MiniappStore
import io.github.whywood.deepdeep.runtime.ModelConfigStore
import io.github.whywood.deepdeep.runtime.ProviderCatalog
import io.github.whywood.deepdeep.runtime.QuestionCenter
import io.github.whywood.deepdeep.runtime.RootCommandExecutor
import io.github.whywood.deepdeep.runtime.SdkCredentialStore
import io.github.whywood.deepdeep.runtime.SessionCatalog
import io.github.whywood.deepdeep.runtime.TimerService
import io.github.whywood.deepdeep.runtime.WorkspaceStore
import io.github.whywood.deepdeep.service.AppShellExecutor
import io.github.whywood.deepdeep.shell.LinuxEnvironment
import io.github.whywood.deepdeep.shell.RootShell
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
    val httpTransport: HttpTransport,
    val personalizationStore: PersonalizationStore,
    val linuxSettings: LinuxSettingsStore,
    val questionCenter: QuestionCenter,
    val goalService: GoalService,
    val miniappStore: MiniappStore,
    val rootShell: RootShell,
    val adbExecutor: RootCommandExecutor,
    val privilege: () -> PrivilegeController,
    val pluginManager: () -> PluginHostManager,
    val shellExecutorFor: suspend (File) -> AppShellExecutor?,
    val linuxEnvironmentFor: (String?) -> LinuxEnvironment,
    val decorateInterrupted: suspend (ConversationState, String) -> ConversationState,
    val timerService: () -> TimerService,
    val jobRegistry: () -> JobRegistry,
    val capabilitySettingsStore: CapabilitySettingsStore,
    val apiKeyProvider: suspend () -> String?,
    val screenTools: () -> List<CodingTool>,
    val virtualScreenTools: () -> List<CodingTool>,
)
