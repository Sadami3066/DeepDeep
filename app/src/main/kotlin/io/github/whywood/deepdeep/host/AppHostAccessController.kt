package io.github.whywood.deepdeep.host

import android.content.Context
import io.github.whywood.deepdeep.adb.AdbBridgeState
import io.github.whywood.deepdeep.runtime.host.HostAccess
import io.github.whywood.deepdeep.runtime.host.HostAccessController
import io.github.whywood.deepdeep.runtime.host.HostAccessState
import io.github.whywood.deepdeep.runtime.host.resolveHostAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppHostAccessController(
    private val scope: CoroutineScope,
    private val preferences: HostAccessStore,
    private val rootProbe: suspend () -> Boolean,
    private val adbStorage: StateFlow<AdbBridgeState>,
    private val connectAdbAction: suspend () -> Unit,
    private val startPairingAction: () -> Unit,
    private val openWirelessDebuggingAction: (Context) -> Unit,
    private val onChanged: suspend () -> Unit,
) : HostAccessController {
    private val selected = MutableStateFlow(HostAccess.LOCAL)
    private val rootReady = MutableStateFlow(false)

    override val state: StateFlow<HostAccessState> = combine(selected, adbStorage, rootReady) { access, adb, root ->
        resolveHostAccess(access, adb.daemon, root)
    }.stateIn(scope, SharingStarted.Eagerly, HostAccessState())

    private var lastNotified = HostAccessState()

    init {
        scope.launch {
            preferences.selected.collect { access ->
                if (selected.value == access) return@collect
                selected.value = access
                if (access == HostAccess.ROOT) probeRoot()
                notifyIfChanged()
            }
        }
        scope.launch {
            adbStorage.collect { notifyIfChanged() }
        }
    }

    override suspend fun select(access: HostAccess) {
        selected.value = access
        preferences.setSelected(access)
        if (access == HostAccess.ROOT) probeRoot()
        notifyIfChanged()
    }

    override suspend fun refresh() {
        if (selected.value != HostAccess.ROOT) return
        probeRoot()
        notifyIfChanged()
    }

    override suspend fun connectAdb() {
        connectAdbAction()
    }

    override fun startAdbPairing() {
        startPairingAction()
    }

    override fun openWirelessDebugging(context: Context) {
        openWirelessDebuggingAction(context)
    }

    private suspend fun probeRoot() {
        rootReady.value = runCatching { rootProbe() }.getOrDefault(false)
    }

    private suspend fun notifyIfChanged() {
        val current = currentState()
        if (current.selected != lastNotified.selected || current.effective != lastNotified.effective) {
            lastNotified = current
            onChanged()
        }
    }

    private fun currentState(): HostAccessState =
        resolveHostAccess(selected.value, adbStorage.value.daemon, rootReady.value)
}
