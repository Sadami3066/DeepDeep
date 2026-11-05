package io.github.whywood.deepdeep.plugin

import android.content.Context
import android.content.res.AssetManager
import io.github.whywood.deepdeep.adb.AdbBootstrap
import io.github.whywood.deepdeep.data.LinuxSettingsStore
import io.github.whywood.deepdeep.plugin.api.DeepDeepPlugin
import io.github.whywood.deepdeep.plugin.api.PluginManifest
import io.github.whywood.deepdeep.plugin.host.ModernPluginAdapter
import io.github.whywood.deepdeep.plugin.host.builtinPluginHost
import io.github.whywood.deepdeep.plugin.virtualscreen.VirtualScreenFeature
import io.github.whywood.deepdeep.privilege.PrivilegeController
import io.github.whywood.deepdeep.runtime.GoalService
import io.github.whywood.deepdeep.runtime.JobRegistry
import io.github.whywood.deepdeep.runtime.ProviderCatalog
import io.github.whywood.deepdeep.runtime.SdkCredentialStore
import io.github.whywood.deepdeep.runtime.TimerService
import io.github.whywood.deepdeep.runtime.WorkshopStore
import io.github.whywood.deepdeep.screen.MainScreenShell
import io.github.whywood.deepdeep.shell.LinuxEnvironment
import java.io.File
import kotlinx.coroutines.CoroutineScope

internal class BuiltinPluginWiring(
    val uiContext: Context,
    val filesDir: File,
    val cacheDir: File,
    val assets: AssetManager,
    val scope: CoroutineScope,
    val language: () -> String,
    val pluginStrings: PluginStrings,
    val linux: LinuxEnvironment,
    val linuxSettings: LinuxSettingsStore,
    val workspaceDir: File,
    val credentials: SdkCredentialStore,
    val providerCatalog: ProviderCatalog,
    val goalService: GoalService,
    val jobRegistry: JobRegistry,
    val timerService: TimerService,
    val workshopStore: WorkshopStore,
    val privilege: PrivilegeController,
    val adbBridge: AdbBootstrap,
    val mainShell: MainScreenShell,
    val virtualScreen: VirtualScreenFeature,
    val screenshotDir: File,
    val onCapabilitiesChanged: () -> Unit,
    val onSessionInvalidated: () -> Unit,
) {
    fun modern(plugin: DeepDeepPlugin, manifest: PluginManifest): OfficialPlugin =
        ModernPluginAdapter(
            modern = plugin,
            manifest = manifest,
            host = builtinPluginHost(
                manifest = manifest,
                context = uiContext,
                filesDir = filesDir,
                scope = scope,
                language = language,
            ),
        )
}

internal fun builtinPlugins(wiring: BuiltinPluginWiring): List<OfficialPlugin> = listOf(
    cliFeishu(wiring),
    internalWebSearch(wiring),
    androidGui(wiring),
    androidArrange(wiring),
    androidMiCloud(wiring),
    cliWecom(wiring),
    cliJwxt(wiring),
    internalGoal(wiring),
    internalJobs(wiring),
    internalTimer(wiring),
    internalWorkshop(wiring),
)
