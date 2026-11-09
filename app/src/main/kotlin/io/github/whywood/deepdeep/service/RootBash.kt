package io.github.whywood.deepdeep.service

import io.github.whywood.deepdeep.data.LinuxSettingsStore
import io.github.whywood.deepdeep.privilege.PrivilegeController
import io.github.whywood.deepdeep.privilege.PrivilegeLevel
import io.github.whywood.deepdeep.shell.LinuxExecutor
import io.github.whywood.deepdeep.shell.LinuxResult
import io.github.whywood.deepdeep.shell.RootfsBootstrap
import io.github.whywood.deepdeep.shell.RootfsState
import io.github.whywood.deepdeep.shell.RootfsStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class RootBashMode {
    PROOT,
    PREPARING,
    CHROOT,
    FAILED,
}

internal fun resolveRootBashMode(
    enabled: Boolean,
    effective: PrivilegeLevel,
    status: RootfsState,
): RootBashMode = when {
    !enabled || effective != PrivilegeLevel.ROOT -> RootBashMode.PROOT
    status is RootfsState.Ready -> RootBashMode.CHROOT
    status is RootfsState.Failed -> RootBashMode.FAILED
    else -> RootBashMode.PREPARING
}

class RootBashController(
    private val scope: CoroutineScope,
    private val settings: LinuxSettingsStore,
    private val privilege: PrivilegeController,
    private val bootstrap: RootfsBootstrap,
) {
    val enabled: StateFlow<Boolean> = settings.rootBashEnabled
        .stateIn(scope, SharingStarted.Eagerly, false)

    val status: StateFlow<RootfsStatus> = bootstrap.status

    val mode: StateFlow<RootBashMode> = combine(enabled, privilege.state, bootstrap.status) { enabled, privilegeState, rootfsStatus ->
        resolveRootBashMode(enabled, privilegeState.effective, rootfsStatus.state)
    }.stateIn(scope, SharingStarted.Eagerly, RootBashMode.PROOT)

    init {
        scope.launch {
            combine(enabled, privilege.state) { enabled, privilegeState ->
                enabled && privilegeState.effective == PrivilegeLevel.ROOT
            }.distinctUntilChanged().collect { active ->
                if (active) bootstrap.ensureReady()
            }
        }
    }

    suspend fun setEnabled(value: Boolean) {
        settings.setRootBashEnabled(value)
    }

    suspend fun retry() {
        bootstrap.ensureReady()
    }

    fun failureReason(): String? = (status.value.state as? RootfsState.Failed)?.reason
}

class RoutingLinuxExecutor(
    private val proot: LinuxExecutor,
    private val chroot: LinuxExecutor,
    private val mode: () -> RootBashMode,
    private val failureReason: () -> String?,
) : LinuxExecutor {
    override suspend fun execute(command: String, timeoutMillis: Long): LinuxResult = when (mode()) {
        RootBashMode.PROOT -> proot.execute(command, timeoutMillis)
        RootBashMode.CHROOT -> chroot.execute(command, timeoutMillis)
        RootBashMode.PREPARING -> throw IllegalStateException("Root Linux is being prepared; try again in a moment.")
        RootBashMode.FAILED -> throw IllegalStateException("Root Linux preparation failed: ${failureReason() ?: "unknown error"}")
    }
}
