package io.github.whywood.deepdeep.plugin.host.services

import io.github.whywood.deepdeep.adb.AdbBootstrap
import io.github.whywood.deepdeep.plugin.api.LinuxResult
import io.github.whywood.deepdeep.plugin.api.PrivilegeLevel as PluginPrivilegeLevel
import io.github.whywood.deepdeep.plugin.api.PrivilegeService
import io.github.whywood.deepdeep.plugin.api.PrivilegeState
import io.github.whywood.deepdeep.privilege.PrivilegeController
import io.github.whywood.deepdeep.privilege.PrivilegeLevel as AppPrivilegeLevel
import io.github.whywood.deepdeep.privilege.PrivilegeState as AppPrivilegeState
import io.github.whywood.deepdeep.shell.RootResult
import io.github.whywood.deepdeep.shell.RootShell
import io.github.whywood.deepdeep.shell.RootStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class PrivilegePluginService(
    private val privilege: PrivilegeController,
    private val rootShell: RootShell?,
    private val adbBridge: AdbBootstrap?,
) : PrivilegeService {
    override val state: PrivilegeState
        get() = privilege.state.value.toPluginState()

    override fun observe(): Flow<PrivilegeState> = privilege.state.map { it.toPluginState() }

    override suspend fun shell(command: String, timeoutMs: Long): LinuxResult = when (privilege.state.value.effective) {
        AppPrivilegeLevel.ROOT -> {
            val shell = rootShell ?: throw IllegalStateException("root shell is not available")
            shell.execute(command, timeoutMs).toPluginResult()
        }

        AppPrivilegeLevel.ADB -> {
            val bridge = adbBridge ?: throw IllegalStateException("ADB bridge is not available")
            val result = bridge.exec(command, timeoutMs)
            LinuxResult(
                exitCode = result.exitCode,
                stdout = result.stdout,
                stderr = result.stderr,
                timedOut = result.timedOut,
                truncated = result.truncated,
            )
        }

        AppPrivilegeLevel.NORMAL -> throw IllegalStateException("root or ADB privilege is required for privileged shell commands")
    }

    private fun RootResult.toPluginResult(): LinuxResult = LinuxResult(
        exitCode = exitCode,
        stdout = stdout,
        stderr = stderr,
        timedOut = timedOut,
        truncated = truncated,
    )

    private fun AppPrivilegeState.toPluginState(): PrivilegeState = PrivilegeState(
        selected = selected.toPluginLevel(),
        effective = effective.toPluginLevel(),
        rootGranted = root == RootStatus.Granted,
        adbConnected = adb.daemon,
    )

    private fun AppPrivilegeLevel.toPluginLevel(): PluginPrivilegeLevel = when (this) {
        AppPrivilegeLevel.NORMAL -> PluginPrivilegeLevel.NORMAL
        AppPrivilegeLevel.ADB -> PluginPrivilegeLevel.ADB
        AppPrivilegeLevel.ROOT -> PluginPrivilegeLevel.ROOT
    }
}
