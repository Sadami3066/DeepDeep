package io.github.whywood.deepdeep.data

import android.content.Context
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.service.NotificationCenter
import io.github.whywood.deepdeep.shell.LinuxEnvironment
import io.github.whywood.deepdeep.shell.LinuxInstallState
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DevPackageOperation(
    val label: String,
    val stage: String? = null,
    val progress: Int? = null,
)

data class DevPackagesState(
    val installed: Set<String> = emptySet(),
    val checking: Boolean = false,
    val operation: DevPackageOperation? = null,
    val error: String? = null,
) {
    val busy: Boolean get() = operation != null
}

class DevPackageManager(
    private val context: Context,
    private val linux: LinuxEnvironment,
    private val notifications: NotificationCenter,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(DevPackagesState())
    val state: StateFlow<DevPackagesState> = _state.asStateFlow()
    private val running = AtomicBoolean(false)

    init {
        notifications.clearInstallNotification()
    }

    fun refresh() {
        if (running.get()) return
        if (linux.state.value !is LinuxInstallState.Installed) {
            _state.update { it.copy(installed = emptySet(), checking = false) }
            return
        }
        running.set(true)
        scope.launch {
            _state.update { it.copy(checking = true, error = null) }
            try {
                val installed = queryInstalled()
                _state.update { it.copy(installed = installed, checking = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        checking = false,
                        error = error.message ?: error.javaClass.simpleName,
                    )
                }
            } finally {
                running.set(false)
            }
        }
    }

    fun installBundle(bundle: DevBundle) {
        install(bundle.packageIds, context.getString(bundle.titleRes))
    }

    fun installPackage(packageId: String) {
        val packageInfo = DevPackageCatalog.packageById(packageId) ?: return
        install(listOf(packageId), packageInfo.displayName)
    }

    fun uninstallPackage(packageId: String) {
        val packageInfo = DevPackageCatalog.packageById(packageId) ?: return
        if (running.get()) return
        if (linux.state.value !is LinuxInstallState.Installed) {
            _state.update { it.copy(error = context.getString(R.string.dev_error_linux_missing)) }
            return
        }
        running.set(true)
        scope.launch {
            _state.update {
                it.copy(
                    operation = DevPackageOperation(context.getString(R.string.dev_uninstalling, packageInfo.displayName)),
                    error = null,
                )
            }
            try {
                val result = linux.execute("apk del $packageId", TIMEOUT_UNINSTALL)
                if (result.exitCode != 0) {
                    val message = result.output.takeLast(600).trim().ifBlank {
                        context.getString(R.string.dev_error_generic)
                    }
                    _state.update { it.copy(error = message) }
                } else {
                    _state.update { it.copy(installed = queryInstalled()) }
                    runCatching { linux.refreshState() }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update { it.copy(error = error.message ?: error.javaClass.simpleName) }
            } finally {
                _state.update { it.copy(operation = null) }
                running.set(false)
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun install(packageIds: List<String>, label: String) {
        val ids = packageIds.distinct().filter { DevPackageCatalog.packageById(it) != null }
        if (ids.isEmpty()) return
        if (running.get()) {
            _state.update { it.copy(error = context.getString(R.string.dev_error_busy)) }
            return
        }
        if (linux.state.value !is LinuxInstallState.Installed) {
            _state.update { it.copy(error = context.getString(R.string.dev_error_linux_missing)) }
            return
        }
        running.set(true)
        scope.launch {
            _state.update {
                it.copy(
                    operation = DevPackageOperation(
                        label = label,
                        stage = context.getString(R.string.dev_stage_updating),
                    ),
                    error = null,
                )
            }
            notifications.notifyInstall(label, context.getString(R.string.dev_stage_updating), null)
            try {
                val update = linux.executeStreaming("apk update", TIMEOUT_UPDATE) { }
                if (update.exitCode != 0) {
                    throw IllegalStateException(
                        update.output.takeLast(600).trim().ifBlank { context.getString(R.string.dev_error_generic) },
                    )
                }
                _state.update {
                    it.copy(operation = it.operation?.copy(stage = context.getString(R.string.dev_stage_installing)))
                }
                notifications.notifyInstall(label, context.getString(R.string.dev_stage_installing), null)
                val command = "apk add ${ids.joinToString(" ")}"
                val result = linux.executeStreaming(command, TIMEOUT_INSTALL) { line ->
                    val match = PROGRESS.find(line) ?: return@executeStreaming
                    val current = match.groupValues[1].toIntOrNull() ?: return@executeStreaming
                    val total = match.groupValues[2].toIntOrNull() ?: return@executeStreaming
                    if (total <= 0) return@executeStreaming
                    val progress = (current * 100 / total).coerceIn(0, 100)
                    val stage = context.getString(R.string.dev_stage_progress, current, total)
                    _state.update { it.copy(operation = it.operation?.copy(stage = stage, progress = progress)) }
                    notifications.notifyInstall(label, stage, progress)
                }
                if (result.exitCode != 0) {
                    throw IllegalStateException(
                        result.output.takeLast(600).trim().ifBlank { context.getString(R.string.dev_error_generic) },
                    )
                }
                val installed = queryInstalled()
                _state.update { it.copy(installed = installed, operation = null) }
                runCatching { linux.refreshState() }
                notifications.notifyInstallFinished(label, success = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val message = error.message ?: error.javaClass.simpleName
                _state.update { it.copy(error = message, operation = null) }
                notifications.notifyInstallFinished(label, success = false, error = message)
            } finally {
                running.set(false)
            }
        }
    }

    private suspend fun queryInstalled(): Set<String> {
        val names = DevPackageCatalog.packages.joinToString(" ") { it.id }
        val command = "for p in $names; do apk info -e \"\$p\" >/dev/null 2>&1 && echo \"\$p\"; done; true"
        val result = linux.execute(command, TIMEOUT_QUERY)
        if (result.exitCode != 0) return emptySet()
        return result.output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
            .intersect(DevPackageCatalog.packages.mapTo(mutableSetOf()) { it.id })
    }

    companion object {
        private const val TIMEOUT_QUERY = 60_000L
        private const val TIMEOUT_UPDATE = 120_000L
        private const val TIMEOUT_INSTALL = 15 * 60_000L
        private const val TIMEOUT_UNINSTALL = 120_000L
        private val PROGRESS = Regex("""^\((\d+)/(\d+)\)""")
    }
}
