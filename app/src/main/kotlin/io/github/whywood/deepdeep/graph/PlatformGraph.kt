package io.github.whywood.deepdeep.graph

import io.github.whywood.deepdeep.adb.AdbBootstrap
import io.github.whywood.deepdeep.adb.AdbBridgeRuntime
import io.github.whywood.deepdeep.data.PrivilegePreferences
import io.github.whywood.deepdeep.privilege.PrivilegeController
import io.github.whywood.deepdeep.privilege.PrivilegeControllerImpl
import io.github.whywood.deepdeep.runtime.McpPluginBridge
import io.github.whywood.deepdeep.runtime.RootCommandExecutor
import io.github.whywood.deepdeep.screen.PrivilegedMainScreenShell
import io.github.whywood.deepdeep.screen.ScreenController
import io.github.whywood.deepdeep.screen.ScreenToolset
import io.github.whywood.deepdeep.service.AppShellExecutor
import io.github.whywood.deepdeep.service.RootBashController
import io.github.whywood.deepdeep.service.RoutingLinuxExecutor
import io.github.whywood.deepdeep.shell.ChrootExecutor
import io.github.whywood.deepdeep.shell.LinuxEnvironment
import io.github.whywood.deepdeep.shell.RootShell
import io.github.whywood.deepdeep.shell.RootfsBootstrap
import io.github.whywood.deepdeep.vscreen.VirtualScreenFeature
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
            deps.syncBundledSkills()
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
    private val chrootExecutors = mutableMapOf<String, ChrootExecutor>()

    private val rootfsBootstrap: RootfsBootstrap by lazy {
        RootfsBootstrap(
            appRootfsDir = File(deps.appContext.filesDir, "linux/rootfs"),
            statusFile = File(deps.appContext.filesDir, "linux/rootfs-bootstrap.state"),
            scratchDir = File(deps.appContext.cacheDir, "root-bash"),
        )
    }

    private val rootBashController: RootBashController by lazy {
        RootBashController(
            scope = deps.scope,
            settings = deps.linuxSettings,
            privilege = privilegeImpl,
            bootstrap = rootfsBootstrap,
        )
    }

    val rootBash: RootBashController get() = rootBashController

    fun linuxBinds(): () -> List<Pair<File, String>> = {
        listOf(File("/data") to "/data") + deps.storageMountStore.cached().map { mount ->
            File(mount.path) to mount.path
        }
    }

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

    private fun chrootExecutorFor(cwd: File): ChrootExecutor = synchronized(chrootExecutors) {
        chrootExecutors.getOrPut(cwd.absolutePath) {
            ChrootExecutor(
                linuxDir = File(RootfsBootstrap.DEFAULT_LINUX_DIR),
                workspaceDir = cwd,
                logDir = File(deps.appContext.cacheDir, "tool-output"),
                sharedStorageEnabled = { deps.sharedStorage.value },
                resolvConf = { linuxEnvironmentFor(cwd.absolutePath).exportResolvConf() },
            )
        }
    }

    val shellExecutorFor: suspend (File) -> AppShellExecutor? = { cwd ->
        if (!deps.linuxSettings.isEnabled()) {
            null
        } else {
            val routing = RoutingLinuxExecutor(
                proot = linuxEnvironmentFor(cwd.absolutePath),
                chroot = chrootExecutorFor(cwd),
                mode = { rootBashController.mode.value },
                failureReason = { rootBashController.failureReason() },
            )
            AppShellExecutor(routing)
        }
    }

    val virtualScreenFeature: VirtualScreenFeature by lazy {
        VirtualScreenFeature.install(deps.uiContext, privilege, adbBridge)
    }

    private val mainShell: PrivilegedMainScreenShell by lazy {
        PrivilegedMainScreenShell(privilege, rootShell, adbBridge)
    }

    val screenToolset: ScreenToolset by lazy {
        ScreenToolset(
            context = deps.appContext,
            screenshotDir = File(deps.appContext.filesDir, "gui/screenshots"),
            mainShell = mainShell,
        )
    }

    val pluginScreenController: ScreenController get() = screenToolset.virtual

    val pluginMainScreenController: ScreenController get() = screenToolset.main

    val mcpPluginBridge: McpPluginBridge by lazy {
        McpPluginBridge(deps.httpTransport, deps.mcpTokenStore)
    }
}
