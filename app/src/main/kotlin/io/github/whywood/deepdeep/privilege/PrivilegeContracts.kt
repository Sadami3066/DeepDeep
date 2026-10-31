package io.github.whywood.deepdeep.privilege

import android.content.Context
import io.github.whywood.deepdeep.adb.AdbBridgeState
import io.github.whywood.deepdeep.shell.RootStatus
import kotlinx.coroutines.flow.StateFlow

enum class PrivilegeLevel {
    NORMAL,
    ADB,
    ROOT,
}

data class PrivilegeState(
    val selected: PrivilegeLevel = PrivilegeLevel.NORMAL,
    val effective: PrivilegeLevel = PrivilegeLevel.NORMAL,
    val root: RootStatus = RootStatus.NotRequested,
    val adb: AdbBridgeState = AdbBridgeState(),
) {
    val availableBackends: Set<PrivilegeLevel>
        get() = resolveAvailableBackends(adbAvailable = adb.daemon, rootAvailable = root == RootStatus.Granted)

    val virtualScreenAvailable: Boolean
        get() = selected != PrivilegeLevel.NORMAL && availableBackends.isNotEmpty()
}

interface PrivilegeController {
    val state: StateFlow<PrivilegeState>

    suspend fun select(level: PrivilegeLevel)

    suspend fun refresh()

    suspend fun probeRoot(): RootStatus

    suspend fun connect()

    fun startPairing()

    fun openWirelessDebugging(context: Context)
}
