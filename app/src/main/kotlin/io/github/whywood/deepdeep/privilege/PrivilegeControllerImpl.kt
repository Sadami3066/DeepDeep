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
            refreshBackends()
        }
        scope.launch {
            adbBridge.state.collect { bridge -> publish { it.copy(adb = bridge) } }
        }
    }

    override suspend fun select(level: PrivilegeLevel) {
        preferences.writeLevel(level)
        publish { it.copy(selected = level) }
        if (level != PrivilegeLevel.NORMAL) refreshBackends()
    }

    override suspend fun refresh() {
        refreshBackends()
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

    private suspend fun refreshBackends() {
        runCatching { adbBridge.refreshState() }
        val root = stateFlow.value.root
        if (root != RootStatus.Denied && root != RootStatus.Unsupported) probeRoot()
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
