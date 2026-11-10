package io.github.whywood.deepdeep.vscreen

import android.content.Context
import io.github.whywood.deepdeep.adb.AdbBootstrap
import io.github.whywood.deepdeep.runtime.host.HostAccess
import io.github.whywood.deepdeep.runtime.host.HostAccessController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PrivilegedLauncher(
    context: Context,
    private val adb: AdbBootstrap,
    private val access: HostAccessController,
) {
    private val appContext = context.applicationContext
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun backends(): List<String> {
        val state = access.state.value
        return orderedBackends(
            preferredBackend = preferredBackendOf(state.selected),
            adbAvailable = state.adbReady,
            rootAvailable = state.rootReady,
        )
    }

    suspend fun start(backend: String, port: Int, nonce: String): HelperProcess = when (backend) {
        BACKEND_ADB -> startAdb(port, nonce)
        BACKEND_ROOT -> startRoot(port, nonce)
        else -> throw IllegalArgumentException("Unknown privileged backend: $backend")
    }

    private suspend fun startAdb(port: Int, nonce: String): HelperProcess {
        val pid = adb.startHelper(port, nonce)
            ?: throw IllegalStateException("Wireless debugging daemon failed to start the virtual screen helper")
        return HelperProcess(BACKEND_ADB, pid = pid.takeIf { it > 0 }) {
            if (pid > 0) cleanupScope.launch { adb.killHelper(pid) }
        }
    }

    private fun startRoot(port: Int, nonce: String): HelperProcess {
        val process = ProcessBuilder("su", "-c", "su $SHELL_UID -c '${helperCommand(port, nonce)}'")
            .redirectErrorStream(true)
            .start()
        return HelperProcess(BACKEND_ROOT, process = process)
    }

    private fun helperCommand(port: Int, nonce: String): String {
        val apk = appContext.applicationInfo.sourceDir
        return "CLASSPATH=$apk /system/bin/setsid /system/bin/app_process /system/bin --nice-name=deepdeep-vscreen " +
            "${VirtualScreenHelperMain::class.java.name} --port $port --nonce $nonce"
    }

    companion object {
        const val BACKEND_ADB = "adb"
        const val BACKEND_ROOT = "root"

        private const val SHELL_UID = 2000
    }
}

internal fun preferredBackendOf(selected: HostAccess): String? = when (selected) {
    HostAccess.LOCAL -> null
    HostAccess.ADB -> PrivilegedLauncher.BACKEND_ADB
    HostAccess.ROOT -> PrivilegedLauncher.BACKEND_ROOT
}

internal fun orderedBackends(
    preferredBackend: String?,
    adbAvailable: Boolean,
    rootAvailable: Boolean,
): List<String> {
    if (preferredBackend == null) return emptyList()
    val available = buildList {
        if (adbAvailable) add(PrivilegedLauncher.BACKEND_ADB)
        if (rootAvailable) add(PrivilegedLauncher.BACKEND_ROOT)
    }
    if (preferredBackend !in available) return available
    return listOf(preferredBackend) + available.filter { it != preferredBackend }
}

class HelperProcess internal constructor(
    val backend: String,
    val pid: Int? = null,
    private val process: Process? = null,
    private val onDestroy: () -> Unit = {},
) {
    fun destroy() {
        runCatching { process?.destroy() }
        runCatching { onDestroy() }
    }
}
