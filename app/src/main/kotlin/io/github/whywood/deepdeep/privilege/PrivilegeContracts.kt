package io.github.whywood.deepdeep.privilege

import android.app.Activity
import android.content.Context
import io.github.whywood.deepdeep.shell.RootStatus
import kotlinx.coroutines.flow.StateFlow

enum class PrivilegeLevel {
    NORMAL,
    SHIZUKU,
    ROOT,
}

data class ShizukuState(
    val installed: Boolean = false,
    val running: Boolean = false,
    val granted: Boolean = false,
    val version: Int = -1,
    val uid: Int = -1,
)

data class PrivilegeState(
    val selected: PrivilegeLevel = PrivilegeLevel.NORMAL,
    val effective: PrivilegeLevel = PrivilegeLevel.NORMAL,
    val root: RootStatus = RootStatus.NotRequested,
    val shizuku: ShizukuState = ShizukuState(),
) {
    val virtualScreenAvailable: Boolean
        get() = effective != PrivilegeLevel.NORMAL
}

interface PrivilegeController {
    val state: StateFlow<PrivilegeState>

    suspend fun select(level: PrivilegeLevel)

    suspend fun refresh()

    suspend fun probeRoot(): RootStatus

    fun requestShizukuPermission(activity: Activity)

    fun openShizuku(context: Context)

    fun openShizukuInstall(context: Context)
}
