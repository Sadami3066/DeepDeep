package io.github.whywood.superpi.graph

import android.content.Intent
import android.os.Process
import android.provider.Settings
import android.system.Os
import io.github.whywood.superpi.adb.AdbBootstrap
import io.github.whywood.superpi.adb.AdbBridgeRuntime
import io.github.whywood.superpi.host.AdbHostEnvironment
import io.github.whywood.superpi.host.AppHostAccessController
import io.github.whywood.superpi.host.AppHostEnvironmentProvider
import io.github.whywood.superpi.host.HostAccessPreferences
import io.github.whywood.superpi.host.LinuxStreamingShellExecutor
import io.github.whywood.superpi.host.LocalHostEnvironment
import io.github.whywood.superpi.host.RootBashReadiness
import io.github.whywood.superpi.host.RootHostEnvironment
import io.github.whywood.superpi.runtime.McpPluginBridge
import io.github.whywood.superpi.runtime.RootCommandResult
import io.github.whywood.superpi.runtime.createRootFileSystem
import io.github.whywood.superpi.runtime.host.HostAccess
import io.github.whywood.superpi.screen.PrivilegedMainScreenShell
import io.github.whywood.superpi.screen.ScreenController
import io.github.whywood.superpi.screen.ScreenToolset
import io.github.whywood.deepdeep.sdk.ai.RunSignal
import io.github.whywood.deepdeep.sdk.coding.tools.ShellExecutionException
import io.github.whywood.deepdeep.sdk.coding.tools.ShellExecutor
import io.github.whywood.deepdeep.sdk.coding.tools.ShellResult
import io.github.whywood.superpi.service.AccessibilityBootstrapRunner
import io.github.whywood.superpi.service.AppShellExecutor
import io.github.whywood.superpi.service.accessibilityServiceComponent
import io.github.whywood.superpi.shell.ChrootExecutor
import io.github.whywood.superpi.shell.LinuxInstallState
import io.github.whywood.superpi.shell.LinuxEnvironment
import io.github.whywood.superpi.shell.RootShell
import io.github.whywood.superpi.shell.RootStatus
import io.github.whywood.superpi.shell.RootfsState
import io.github.whywood.superpi.shell.RootfsBootstrap
import io.github.whywood.superpi.vscreen.VirtualScreenFeature
import java.io.File
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal class PlatformGraph(private val deps: PlatformGraphDeps) {
    val rootShell = RootShell()
    val adbBridge: AdbBootstrap = AdbBridgeRuntime.install(deps.appContext)

    val linuxEnvironment = LinuxEnvironment(
        deps.appContext,
        deps.homeDir,
        sharedStorageEnabled = { deps.sharedStorage.value },
        extraBinds = linuxBinds(),
    )

    private val shellEnvironments = mutableMapOf<String, LinuxEnvironment>()
    private val chrootExecutors = mutableMapOf<String, ChrootExecutor>()

    val rootfsBootstrap: RootfsBootstrap by lazy {
        RootfsBootstrap(
            appRootfsDir = File(deps.appContext.filesDir, "linux/rootfs"),
            statusFile = File(deps.appContext.filesDir, "linux/rootfs-bootstrap.state"),
            scratchDir = File(deps.appContext.cacheDir, "root-bash"),
        )
    }

    private val hostAccessPreferences: HostAccessPreferences by lazy {
        HostAccessPreferences(deps.appContext)
    }

    val hostAccess: AppHostAccessController by lazy {
        AppHostAccessController(
            scope = deps.scope,
            preferences = hostAccessPreferences,
            rootProbe = { rootShell.probe() == RootStatus.Granted },
            adbStorage = adbBridge.state,
            connectAdbAction = { adbBridge.connectAndStart() },
            startPairingAction = { deps.scope.launch { adbBridge.startPairing() } },
            openWirelessDebuggingAction = { context ->
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
            onChanged = {
                deps.scope.launch { deps.pluginManager().refreshAndSyncSkills() }
                deps.syncBundledSkills()
                deps.host().reconfigure()
                accessibilityBootstrap.onPrivilegeReady()
            },
        )
    }

    private val accessibilityBootstrap by lazy {
        AccessibilityBootstrapRunner(
            component = accessibilityServiceComponent(),
            exec = { command, timeoutMillis ->
                if (hostAccess.state.value.effective == HostAccess.ROOT) {
                    val result = rootShell.execute(command, timeoutMillis)
                    RootCommandResult(
                        exitCode = result.exitCode,
                        stdout = result.stdout,
                        stderr = result.stderr,
                        truncated = result.truncated,
                        timedOut = result.timedOut,
                    )
                } else {
                    adbBridge.exec(command, timeoutMillis)
                }
            },
            isEnabled = { hostAccess.state.value.effective != HostAccess.LOCAL },
            scope = deps.scope,
        )
    }

    init {
        deps.scope.launch {
            hostAccess.state.map { it.effective }.distinctUntilChanged().collect { effective ->
                if (effective == HostAccess.ROOT) rootfsBootstrap.ensureReady()
                accessibilityBootstrap.onPrivilegeReady()
            }
        }
    }

    val hostEnvironmentProvider: AppHostEnvironmentProvider by lazy {
        val sharedStorage = { deps.sharedStorage.value }
        val rootDirectory: (String) -> File = { cwd ->
            cwd.takeIf { it.isNotBlank() }?.let(::File) ?: deps.homeDir
        }
        val prootBash: (String) -> ShellExecutor = { cwd ->
            AppShellExecutor(linuxEnvironmentFor(cwd.ifBlank { null }))
        }
        val prootStreaming = { cwd: String ->
            val environment = linuxEnvironmentFor(cwd.ifBlank { null })
            LinuxStreamingShellExecutor { command, timeoutMillis, onOutputLine ->
                environment.executeStreaming(command, timeoutMillis, onOutputLine)
            }
        }
        val chrootStreaming = { cwd: String ->
            val executor = chrootExecutorFor(rootDirectory(cwd))
            LinuxStreamingShellExecutor { command, timeoutMillis, onOutputLine ->
                executor.executeStreaming(command, timeoutMillis, onOutputLine)
            }
        }
        AppHostEnvironmentProvider(
            accessController = hostAccess,
            local = LocalHostEnvironment(
                prootBash = prootBash,
                sharedStorageEnabled = sharedStorage,
                prootBashStreaming = prootStreaming,
                bashAvailable = { linuxInstalled() },
            ),
            adb = AdbHostEnvironment(
                prootBash = prootBash,
                sharedStorageEnabled = sharedStorage,
                adbShell = adbSystemShell,
                prootBashStreaming = prootStreaming,
                bashAvailable = { linuxInstalled() },
            ),
            root = RootHostEnvironment(
                chrootBash = { cwd -> AppShellExecutor(chrootExecutorFor(rootDirectory(cwd))) },
                rootFiles = createRootFileSystem(Process.myUid(), Os.getgid()),
                rootShell = rootSystemShell,
                sharedStorageEnabled = sharedStorage,
                readiness = {
                    when (val state = rootfsBootstrap.status.value.state) {
                        RootfsState.Ready -> RootBashReadiness.READY
                        RootfsState.Preparing -> RootBashReadiness.PREPARING
                        is RootfsState.Failed -> RootBashReadiness.FAILED
                        RootfsState.Idle -> RootBashReadiness.PREPARING
                    }
                },
                chrootStreaming = chrootStreaming,
            ),
        )
    }

    private fun linuxInstalled(): Boolean =
        linuxEnvironment.state.value is LinuxInstallState.Installed

    private val adbSystemShell: ShellExecutor = object : ShellExecutor {
        override suspend fun run(command: String, timeoutSeconds: Long?, signal: RunSignal?): ShellResult {
            signal?.throwIfAborted()
            val seconds = normalizedTimeoutSeconds(timeoutSeconds)
            val result = adbBridge.exec(command, seconds * 1000)
            signal?.throwIfAborted()
            if (result.timedOut) throw ShellExecutionException("command timed out after ${seconds}s")
            return ShellResult(
                output = commandOutput(result.stdout, result.stderr),
                exitCode = result.exitCode.takeIf { it >= 0 },
                truncated = result.truncated,
            )
        }
    }

    private val rootSystemShell: ShellExecutor = object : ShellExecutor {
        override suspend fun run(command: String, timeoutSeconds: Long?, signal: RunSignal?): ShellResult {
            signal?.throwIfAborted()
            val seconds = normalizedTimeoutSeconds(timeoutSeconds)
            val result = rootShell.execute(command, seconds * 1000)
            signal?.throwIfAborted()
            if (result.timedOut) throw ShellExecutionException("command timed out after ${seconds}s")
            return ShellResult(
                output = commandOutput(result.stdout, result.stderr),
                exitCode = result.exitCode.takeIf { it >= 0 },
                truncated = result.truncated,
            )
        }
    }

    private fun normalizedTimeoutSeconds(timeoutSeconds: Long?): Long =
        (timeoutSeconds ?: DEFAULT_SHELL_TIMEOUT_SECONDS)
            .coerceIn(MIN_SHELL_TIMEOUT_SECONDS, MAX_SHELL_TIMEOUT_SECONDS)

    private fun commandOutput(stdout: String, stderr: String): String = listOfNotNull(
        stdout.takeIf { it.isNotEmpty() },
        stderr.takeIf { it.isNotEmpty() },
    ).joinToString("\n")

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
                sharedStorageEnabled = { deps.sharedStorage.value },
                resolvConf = { linuxEnvironmentFor(cwd.absolutePath).exportResolvConf() },
                logDir = File(deps.appContext.cacheDir, "tool-output"),
            )
        }
    }

    val virtualScreenFeature: VirtualScreenFeature by lazy {
        VirtualScreenFeature.install(deps.uiContext, hostAccess, adbBridge)
    }

    private val mainShell: PrivilegedMainScreenShell by lazy {
        PrivilegedMainScreenShell(hostEnvironmentProvider)
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

    private companion object {
        private const val DEFAULT_SHELL_TIMEOUT_SECONDS = 60L
        private const val MIN_SHELL_TIMEOUT_SECONDS = 1L
        private const val MAX_SHELL_TIMEOUT_SECONDS = 600L
    }
}
