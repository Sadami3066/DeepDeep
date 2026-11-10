package io.github.whywood.deepdeep.data

import androidx.annotation.StringRes
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.runtime.host.HostEnvironmentProvider
import io.github.whywood.deepdeep.sdk.coding.tools.ShellResult
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

fun interface DevPackageStrings {
    fun text(@StringRes resId: Int, vararg args: Any): String
}

interface DevPackageNotifier {
    fun clearInstallNotification()
    fun notifyInstall(label: String, stage: String?, progress: Int?)
    fun notifyInstallFinished(label: String, success: Boolean, error: String? = null)
}

class DevPackageManager(
    private val strings: DevPackageStrings,
    private val hostEnvironment: HostEnvironmentProvider,
    private val cwd: () -> String,
    private val ready: () -> Boolean,
    private val notifier: DevPackageNotifier,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(DevPackagesState())
    val state: StateFlow<DevPackagesState> = _state.asStateFlow()
    private val running = AtomicBoolean(false)

    init {
        notifier.clearInstallNotification()
    }

    fun refresh() {
        if (running.get()) return
        if (!ready()) {
            _state.update { it.copy(installed = emptySet(), checking = false) }
            return
        }
        running.set(true)
        _state.update { it.copy(checking = true, error = null) }
        scope.launch {
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

    suspend fun refreshAndAwait() {
        refresh()
        state.first { !it.checking && !it.busy }
    }

    fun installBundle(bundle: DevBundle) {
        install(
            packageIds = bundle.packageIds,
            label = strings.text(bundle.titleRes),
            postInstall = bundle.postInstallCommand,
            mirrorUrls = bundle.mirrorUrls,
            downloads = bundle.downloads,
        )
    }

    fun installPackage(packageId: String) {
        val packageInfo = DevPackageCatalog.packageById(packageId) ?: return
        install(listOf(packageId), packageLabel(packageInfo))
    }

    fun uninstallPackage(packageId: String) {
        val packageInfo = DevPackageCatalog.packageById(packageId) ?: return
        if (running.get()) return
        if (!ready()) {
            _state.update { it.copy(error = strings.text(R.string.dev_error_linux_missing)) }
            return
        }
        running.set(true)
        scope.launch {
            _state.update {
                it.copy(
                    operation = DevPackageOperation(strings.text(R.string.dev_uninstalling, packageLabel(packageInfo))),
                    error = null,
                )
            }
            try {
                val result = execute("apk del $packageId", TIMEOUT_UNINSTALL_SECONDS)
                if (result.exitCode != 0) {
                    _state.update { it.copy(error = failureMessage(result)) }
                } else {
                    _state.update { it.copy(installed = queryInstalled()) }
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

    private fun install(
        packageIds: List<String>,
        label: String,
        postInstall: String? = null,
        mirrorUrls: List<String> = emptyList(),
        downloads: List<DevDownload> = emptyList(),
    ) {
        val ids = packageIds.distinct().filter { DevPackageCatalog.packageById(it) != null }
        if (ids.isEmpty() && mirrorUrls.isEmpty() && downloads.isEmpty()) return
        if (running.get()) {
            _state.update { it.copy(error = strings.text(R.string.dev_error_busy)) }
            return
        }
        if (!ready()) {
            _state.update { it.copy(error = strings.text(R.string.dev_error_linux_missing)) }
            return
        }
        running.set(true)
        scope.launch {
            _state.update {
                it.copy(
                    operation = DevPackageOperation(
                        label = label,
                        stage = strings.text(R.string.dev_stage_updating),
                    ),
                    error = null,
                )
            }
            notifier.notifyInstall(label, strings.text(R.string.dev_stage_updating), null)
            try {
                if (mirrorUrls.isNotEmpty()) {
                    val stage = strings.text(R.string.dev_stage_mirror)
                    _state.update { it.copy(operation = it.operation?.copy(stage = stage)) }
                    notifier.notifyInstall(label, stage, null)
                    val mirror = execute(writeRepositoriesCommand(mirrorUrls), TIMEOUT_UPDATE_SECONDS)
                    if (mirror.exitCode != 0) throw IllegalStateException(failureMessage(mirror))
                }
                val updateStage = strings.text(R.string.dev_stage_updating)
                _state.update { it.copy(operation = it.operation?.copy(stage = updateStage)) }
                notifier.notifyInstall(label, updateStage, null)
                val update = execute("apk update", TIMEOUT_UPDATE_SECONDS)
                if (update.exitCode != 0) throw IllegalStateException(failureMessage(update))
                if (ids.isNotEmpty()) {
                    val stage = strings.text(R.string.dev_stage_installing)
                    _state.update { it.copy(operation = it.operation?.copy(stage = stage)) }
                    notifier.notifyInstall(label, stage, null)
                    val result = execute("apk add ${ids.joinToString(" ")}", TIMEOUT_INSTALL_SECONDS)
                    if (result.exitCode != 0) throw IllegalStateException(failureMessage(result))
                }
                downloads.forEachIndexed { index, download ->
                    val stage = strings.text(
                        R.string.dev_stage_download_progress,
                        index + 1,
                        downloads.size,
                    )
                    val progress = (index * 100 / downloads.size).coerceIn(0, 100)
                    _state.update {
                        it.copy(operation = it.operation?.copy(stage = stage, progress = progress))
                    }
                    notifier.notifyInstall(label, stage, progress)
                    val downloadResult = execute(download.installCommand(), TIMEOUT_DOWNLOAD_SECONDS)
                    if (downloadResult.exitCode != 0) throw IllegalStateException(failureMessage(downloadResult))
                }
                if (!postInstall.isNullOrBlank()) {
                    val stage = strings.text(R.string.dev_stage_python)
                    _state.update { it.copy(operation = it.operation?.copy(stage = stage, progress = null)) }
                    notifier.notifyInstall(label, stage, null)
                    val post = execute(postInstall, TIMEOUT_POST_SECONDS)
                    if (post.exitCode != 0) throw IllegalStateException(failureMessage(post))
                }
                val installed = queryInstalled()
                _state.update { it.copy(installed = installed, operation = null) }
                notifier.notifyInstallFinished(label, success = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val message = error.message ?: error.javaClass.simpleName
                _state.update { it.copy(error = message, operation = null) }
                notifier.notifyInstallFinished(label, success = false, error = message)
            } finally {
                running.set(false)
            }
        }
    }

    private suspend fun queryInstalled(): Set<String> {
        val names = DevPackageCatalog.packages.joinToString(" ") { it.id }
        val command = "for p in $names; do apk info -e \"\$p\" >/dev/null 2>&1 && echo \"\$p\"; done; true"
        val result = execute(command, TIMEOUT_QUERY_SECONDS)
        if (result.exitCode != 0) return emptySet()
        return result.output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
            .intersect(DevPackageCatalog.packages.mapTo(mutableSetOf()) { it.id })
    }

    private suspend fun execute(command: String, timeoutSeconds: Long): ShellResult =
        hostEnvironment.current().bash(workingDirectory()).run(command, timeoutSeconds, null)

    private fun workingDirectory(): String = cwd().ifBlank { "/" }

    private fun packageLabel(packageInfo: DevPackage): String =
        packageInfo.displayNameRes?.let(strings::text) ?: packageInfo.displayName

    private fun failureMessage(result: ShellResult): String =
        result.output.takeLast(600).trim().ifBlank { strings.text(R.string.dev_error_generic) }

    private fun writeRepositoriesCommand(lines: List<String>): String {
        val content = lines.joinToString(separator = "\n", postfix = "\n")
        return "mkdir -p /etc/apk && printf '%s' ${shellQuote(content)} > /etc/apk/repositories"
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

    companion object {
        private const val TIMEOUT_QUERY_SECONDS = 60L
        private const val TIMEOUT_UPDATE_SECONDS = 120L
        private const val TIMEOUT_INSTALL_SECONDS = 600L
        private const val TIMEOUT_UNINSTALL_SECONDS = 120L
        private const val TIMEOUT_POST_SECONDS = 600L
        private const val TIMEOUT_DOWNLOAD_SECONDS = 600L
    }
}
