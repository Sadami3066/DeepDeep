package io.github.whywood.deepdeep.graph

import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import io.github.whywood.deepdeep.adb.AdbBootstrap
import io.github.whywood.deepdeep.adb.AdbBridgeRuntime
import io.github.whywood.deepdeep.data.PrivilegePreferences
import io.github.whywood.deepdeep.plugin.gui.GuiAccessibilityActions
import io.github.whywood.deepdeep.plugin.virtualscreen.VirtualScreenFeature
import io.github.whywood.deepdeep.privilege.PrivilegeController
import io.github.whywood.deepdeep.privilege.PrivilegeControllerImpl
import io.github.whywood.deepdeep.runtime.McpPluginBridge
import io.github.whywood.deepdeep.runtime.RootCommandExecutor
import io.github.whywood.deepdeep.screen.AccessibilityScreenBackend
import io.github.whywood.deepdeep.screen.AndroidScreenAppCatalog
import io.github.whywood.deepdeep.screen.MainScreenController
import io.github.whywood.deepdeep.screen.ScreenController
import io.github.whywood.deepdeep.screen.ScreenRouter
import io.github.whywood.deepdeep.screen.VirtualEngineBackend
import io.github.whywood.deepdeep.service.AppShellExecutor
import io.github.whywood.deepdeep.service.GuiAccessibilityState
import io.github.whywood.deepdeep.shell.LinuxEnvironment
import io.github.whywood.deepdeep.shell.RootShell
import io.github.whywood.deepdeep.vscreen.VirtualScreenRuntime
import java.io.File
import kotlinx.coroutines.launch

internal class PlatformGraph(private val deps: PlatformGraphDeps) {
    val rootShell = RootShell()
    val adbBridge: AdbBootstrap = AdbBridgeRuntime.install(deps.appContext)

    private val privilegeImpl by lazy {
        PrivilegeControllerImpl(
            preferences = PrivilegePreferences(deps.appContext) { deps.linuxSettings.isRootEnabled() },
            rootShell = rootShell,
            adbBridge = adbBridge,
            scope = deps.scope,
        ) {
            deps.scope.launch { deps.pluginManager().refreshAndSyncSkills() }
            deps.host().reconfigure()
        }
    }
    val privilege: PrivilegeController get() = privilegeImpl
    val adbExecutor: RootCommandExecutor get() = privilegeImpl.adbExecutor

    val linuxEnvironment = LinuxEnvironment(
        deps.appContext,
        deps.homeDir,
        sharedStorageEnabled = { deps.sharedStorage.value },
        extraBinds = linuxBinds(),
    )

    private val shellEnvironments = mutableMapOf<String, LinuxEnvironment>()

    fun linuxBinds(): List<Pair<File, String>> = listOf(File("/data") to "/data")

    fun linuxEnvironmentFor(cwd: String?): LinuxEnvironment {
        val directory = cwd?.takeIf { it.isNotBlank() }?.let(::File) ?: deps.homeDir
        return synchronized(shellEnvironments) {
            shellEnvironments.getOrPut(directory.absolutePath) {
                LinuxEnvironment(
                    deps.appContext,
                    directory,
                    sharedStorageEnabled = { deps.sharedStorage.value },
                    extraBinds = linuxBinds(),
                )
            }
        }
    }

    val shellExecutorFor: suspend (File) -> AppShellExecutor? = { cwd ->
        if (!deps.linuxSettings.isEnabled()) {
            null
        } else {
            AppShellExecutor(linuxEnvironmentFor(cwd.absolutePath))
        }
    }

    val virtualScreenFeature by lazy {
        VirtualScreenFeature.install(deps.uiContext, privilege, adbBridge)
    }

    private val screenActions = GuiAccessibilityActions()

    val pluginScreenController: ScreenController by lazy {
        ScreenRouter(
            accessibility = AccessibilityScreenBackend(screenActions),
            virtual = VirtualEngineBackend { VirtualScreenRuntime.engine() },
            apps = AndroidScreenAppCatalog(deps.appContext),
            accessibilityEnabled = { GuiAccessibilityState.isEnabled(deps.appContext) },
        )
    }

    val pluginMainScreenController: ScreenController by lazy {
        MainScreenController(
            actions = screenActions,
            apps = AndroidScreenAppCatalog(deps.appContext),
            displaySize = {
                val manager = deps.appContext.getSystemService(android.content.Context.DISPLAY_SERVICE) as? DisplayManager
                val metrics = DisplayMetrics()
                checkNotNull(manager?.getDisplay(0)) { "Main screen is unavailable" }.getRealMetrics(metrics)
                metrics.widthPixels to metrics.heightPixels
            },
            virtualStatus = { pluginScreenController.status() },
            shell = deps.mainShell(),
            screenshotSupported = { android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R },
        )
    }

    val mcpPluginBridge: McpPluginBridge by lazy {
        McpPluginBridge(deps.httpTransport, deps.mcpTokenStore)
    }
}
