package io.github.whywood.deepdeep.privilege

import android.content.Context
import android.content.Intent
import android.provider.Settings
import io.github.whywood.deepdeep.adb.AdbBootstrap
import io.github.whywood.deepdeep.data.PrivilegePreferences
import io.github.whywood.deepdeep.runtime.RootCommandExecutor
import io.github.whywood.deepdeep.shell.RootShell
import io.github.whywood.deepdeep.shell.RootStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PrivilegeControllerImpl(
    private val preferences: PrivilegePreferences,
    private val rootShell: RootShell,
    private val adbBridge: AdbBootstrap,
    private val scope: CoroutineScope,
    private val onChanged: suspend () -> Unit = {},
) : PrivilegeController {
    private val stateFlow = MutableStateFlow(PrivilegeState())
    override val state: StateFlow<PrivilegeState> = stateFlow.asStateFlow()

    val adbExecutor: RootCommandExecutor = RootCommandExecutor { command, timeoutMillis ->
        adbBridge.exec(command, timeoutMillis)
    }

    init {
        scope.launch {
            preferences.migrateLegacyRoot()
            val level = preferences.readLevel()
            publish { it.copy(selected = level) }
            refreshBackends(probeRoot = shouldProbeRootOnStartup(level))
        }
        scope.launch {
            adbBridge.state.collect { bridge -> publish { it.copy(adb = bridge) } }
        }
    }

    override suspend fun select(level: PrivilegeLevel) {
        preferences.writeLevel(level)
        publish { it.copy(selected = level) }
        if (level != PrivilegeLevel.NORMAL) refreshBackends(probeRoot = true)
    }

    override suspend fun refresh() {
        refreshBackends(probeRoot = true)
    }

    override suspend fun probeRoot(): RootStatus {
        val status = rootShell.probe()
        publish { it.copy(root = status) }
        return status
    }

    override suspend fun connect() {
        runCatching { adbBridge.connectAndStart() }
    }

    override fun startPairing() {
        scope.launch { runCatching { adbBridge.startPairing() } }
    }

    override fun openWirelessDebugging(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private suspend fun refreshBackends(probeRoot: Boolean) {
        runCatching { adbBridge.refreshState() }
        if (probeRoot && shouldAutoProbeRoot(stateFlow.value.root)) probeRoot()
    }

    private fun publish(transform: (PrivilegeState) -> PrivilegeState) {
        var changed = false
        stateFlow.update { current ->
            val next = transform(current).resolveEffective()
            if (
                current.selected != next.selected ||
                current.effective != next.effective ||
                current.availableBackends != next.availableBackends
            ) {
                changed = true
            }
            next
        }
        if (changed) scope.launch { onChanged() }
    }
}

internal fun shouldProbeRootOnStartup(selected: PrivilegeLevel): Boolean =
    selected != PrivilegeLevel.NORMAL

internal fun shouldAutoProbeRoot(status: RootStatus): Boolean =
    status != RootStatus.Denied && status != RootStatus.Unsupported

internal fun shellBackendsFor(effective: PrivilegeLevel): List<PrivilegeLevel> =
    if (effective == PrivilegeLevel.NORMAL) emptyList() else listOf(effective)
