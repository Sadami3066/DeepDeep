package io.github.whywood.deepdeep.privilege

import android.app.Activity
import android.content.Context
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
    private val shizukuClient: ShizukuClient,
    private val scope: CoroutineScope,
    private val onChanged: suspend () -> Unit = {},
) : PrivilegeController {
    private val stateFlow = MutableStateFlow(PrivilegeState(shizuku = shizukuClient.state.value))
    override val state: StateFlow<PrivilegeState> = stateFlow.asStateFlow()

    val shizukuExecutor: RootCommandExecutor = shizukuClient

    init {
        scope.launch {
            preferences.migrateLegacyRoot()
            val level = preferences.readLevel()
            publish { it.copy(selected = level) }
            if (level == PrivilegeLevel.ROOT) probeRoot()
        }
        scope.launch {
            shizukuClient.state.collect { shizuku -> publish { it.copy(shizuku = shizuku) } }
        }
    }

    override suspend fun select(level: PrivilegeLevel) {
        preferences.writeLevel(level)
        publish { it.copy(selected = level) }
        if (level == PrivilegeLevel.ROOT) probeRoot()
    }

    override suspend fun refresh() {
        publish { it.copy(shizuku = shizukuClient.refresh()) }
    }

    override suspend fun probeRoot(): RootStatus {
        val status = rootShell.probe()
        publish { it.copy(root = status) }
        return status
    }

    override fun requestShizukuPermission(activity: Activity) {
        shizukuClient.requestPermission(activity)
    }

    override fun openShizuku(context: Context) {
        shizukuClient.openApp(context)
    }

    override fun openShizukuInstall(context: Context) {
        shizukuClient.openInstallPage(context)
    }

    private fun publish(transform: (PrivilegeState) -> PrivilegeState) {
        var effectiveChanged = false
        stateFlow.update { current ->
            val next = transform(current).resolveEffective()
            if (current.effective != next.effective) effectiveChanged = true
            next
        }
        if (effectiveChanged) scope.launch { onChanged() }
    }
}
