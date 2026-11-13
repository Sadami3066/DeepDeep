package io.github.whywood.deepdeep.graph

import android.content.Context
import io.github.whywood.deepdeep.adb.AdbBootstrap
import io.github.whywood.deepdeep.arrange.ArrangeCalendarGateway
import io.github.whywood.deepdeep.data.CustomProviderStore
import io.github.whywood.deepdeep.data.HostFiles
import io.github.whywood.deepdeep.data.update.UpdateInstaller
import io.github.whywood.deepdeep.host.AppHostAccessController
import io.github.whywood.deepdeep.runtime.ProviderCatalog
import io.github.whywood.deepdeep.runtime.SdkCredentialStore
import io.github.whywood.deepdeep.sdk.mcp.oauth.FileMcpTokenStore
import io.github.whywood.deepdeep.shell.RootShell
import io.github.whywood.deepdeep.web.WebProvider
import io.github.whywood.deepdeep.sdk.ai.http.HttpTransport
import io.github.whywood.deepdeep.plugin.PluginStrings
import io.github.whywood.deepdeep.plugin.host.services.PluginBusImpl
import io.github.whywood.deepdeep.vscreen.VirtualScreenFeature
import io.github.whywood.deepdeep.runtime.AgentRuntimeHost
import io.github.whywood.deepdeep.runtime.EncryptedSecretStore
import io.github.whywood.deepdeep.runtime.GoalService
import io.github.whywood.deepdeep.runtime.InstallationIdStore
import io.github.whywood.deepdeep.runtime.JobRegistry
import io.github.whywood.deepdeep.runtime.McpConfigStore
import io.github.whywood.deepdeep.runtime.McpPluginBridge
import io.github.whywood.deepdeep.runtime.McpServerProbe
import io.github.whywood.deepdeep.runtime.TimerService
import io.github.whywood.deepdeep.runtime.MiniappAgentService
import io.github.whywood.deepdeep.runtime.MiniappStore
import io.github.whywood.deepdeep.runtime.host.HostEnvironmentProvider
import io.github.whywood.deepdeep.screen.ScreenController
import io.github.whywood.deepdeep.shell.LinuxEnvironment
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

internal class PluginGraphDeps(
    val appContext: Context,
    val uiContext: Context,
    val scope: CoroutineScope,
    val agentDir: File,
    val homeDir: File,
    val pluginStrings: PluginStrings,
    val host: AgentRuntimeHost,
    val installationId: InstallationIdStore,
    val linuxEnvironment: LinuxEnvironment,
    val hostEnvironment: HostEnvironmentProvider,
    val secretStore: EncryptedSecretStore,
    val httpTransport: HttpTransport,
    val activeSessionId: MutableStateFlow<String?>,
    val pluginBus: PluginBusImpl,
    val mcpConfigStore: McpConfigStore,
    val mcpServerProbe: McpServerProbe,
    val miniappStore: MiniappStore,
    val miniappAgentService: () -> MiniappAgentService,
    val jobRegistry: () -> JobRegistry,
    val timerService: () -> TimerService,
    val goalService: () -> GoalService,
    val virtualScreenFeature: () -> VirtualScreenFeature,
    val pluginScreenController: () -> ScreenController,
    val pluginMainScreenController: () -> ScreenController,
    val mcpPluginBridge: () -> McpPluginBridge,
    val hostFiles: HostFiles,
    val arrangeCalendar: ArrangeCalendarGateway,
    val webProvider: WebProvider,
    val credentials: SdkCredentialStore,
    val customProviderStore: CustomProviderStore,
    val providerCatalog: ProviderCatalog,
    val mcpTokenStore: FileMcpTokenStore,
    val rootShell: RootShell,
    val adbBridge: AdbBootstrap,
    val hostAccess: AppHostAccessController,
    val updateInstaller: UpdateInstaller,
)
