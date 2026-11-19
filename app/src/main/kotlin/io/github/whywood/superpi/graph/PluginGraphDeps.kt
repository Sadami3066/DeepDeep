package io.github.whywood.superpi.graph

import android.content.Context
import io.github.whywood.superpi.adb.AdbBootstrap
import io.github.whywood.superpi.arrange.ArrangeCalendarGateway
import io.github.whywood.superpi.data.CustomProviderStore
import io.github.whywood.superpi.data.HostFiles
import io.github.whywood.superpi.data.update.UpdateInstaller
import io.github.whywood.superpi.host.AppHostAccessController
import io.github.whywood.superpi.runtime.ProviderCatalog
import io.github.whywood.superpi.runtime.SdkCredentialStore
import io.github.whywood.deepdeep.sdk.mcp.oauth.FileMcpTokenStore
import io.github.whywood.superpi.shell.RootShell
import io.github.whywood.superpi.web.WebProvider
import io.github.whywood.deepdeep.sdk.ai.http.HttpTransport
import io.github.whywood.superpi.plugin.PluginStrings
import io.github.whywood.superpi.plugin.host.services.PluginBusImpl
import io.github.whywood.superpi.vscreen.VirtualScreenFeature
import io.github.whywood.superpi.runtime.AgentRuntimeHost
import io.github.whywood.superpi.runtime.EncryptedSecretStore
import io.github.whywood.superpi.runtime.GoalService
import io.github.whywood.superpi.runtime.InstallationIdStore
import io.github.whywood.superpi.runtime.JobRegistry
import io.github.whywood.superpi.runtime.McpConfigStore
import io.github.whywood.superpi.runtime.McpPluginBridge
import io.github.whywood.superpi.runtime.McpServerProbe
import io.github.whywood.superpi.runtime.TimerService
import io.github.whywood.superpi.runtime.MiniappAgentService
import io.github.whywood.superpi.runtime.MiniappStore
import io.github.whywood.superpi.runtime.host.HostEnvironmentProvider
import io.github.whywood.superpi.screen.ScreenController
import io.github.whywood.superpi.shell.LinuxEnvironment
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
