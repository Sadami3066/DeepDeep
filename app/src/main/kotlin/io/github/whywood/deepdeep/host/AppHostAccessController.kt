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

    init {
        scope.launch {
            preferences.selected.collect { selected.value = it }
        }
    }

    override suspend fun select(access: HostAccess) {
        val before = currentState()
        selected.value = access
        preferences.setSelected(access)
        applyRootProbe(before)
    }

    override suspend fun refresh() {
        applyRootProbe(currentState())
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

    private suspend fun applyRootProbe(before: HostAccessState) {
        val beforeRootReady = rootReady.value
        rootReady.value = runCatching { rootProbe() }.getOrDefault(false)
        val after = currentState()
        if (beforeRootReady != rootReady.value || before.effective != after.effective) {
            onChanged()
        }
    }

    private fun currentState(): HostAccessState =
        resolveHostAccess(selected.value, adbStorage.value.daemon, rootReady.value)
}
