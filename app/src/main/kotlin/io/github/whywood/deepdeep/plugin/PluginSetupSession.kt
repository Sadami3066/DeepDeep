package io.github.whywood.deepdeep.plugin

import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.runtime.PluginSetupResult
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class PluginSetupSession(
    private val plugins: Map<String, OfficialPlugin>,
    private val recordStore: PluginRecordStore,
    private val installer: PluginInstaller,
    private val strings: PluginStrings,
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<PluginsUiState>,
    private val busy: AtomicBoolean,
    private val isForeground: () -> Boolean,
    private val onSetupReady: (label: String) -> Unit,
    private val notifyInstall: (label: String, stage: String?, progress: Int?) -> Unit,
    private val onRefreshStatuses: suspend () -> Unit,
    private val onFlushContributions: () -> Unit,
) {
    private val confirmWaiter = AtomicReference<CompletableDeferred<Boolean>?>(null)
    private val setupJob = AtomicReference<Job?>(null)
    private val externalReady = AtomicBoolean(false)
    @Volatile private var cancelRequested = false

    fun ui(plugin: OfficialPlugin): PluginUi = ManagerUi(plugin)

    fun beginOperation() {
        cancelRequested = false
        externalReady.set(false)
    }

    fun resetExternalReady() {
        externalReady.set(false)
    }

    fun trackJob(job: Job) {
        setupJob.set(job)
        job.invokeOnCompletion { setupJob.compareAndSet(job, null) }
    }

    fun confirmInstall() {
        confirmWaiter.get()?.complete(true)
    }

    fun cancelInteraction() {
        cancelRequested = true
        externalReady.set(false)
        confirmWaiter.getAndSet(null)?.complete(false)
        setupJob.getAndSet(null)?.cancel(CancellationException("plugin setup cancelled"))
        state.update { it.copy(interaction = null) }
    }

    fun onAppForeground() {
        if (!busy.get()) return
        val pluginId = state.value.interaction?.pluginId ?: return
        val plugin = plugins[pluginId] ?: return
        scope.launch {
            val status = runCatching { plugin.status() }.getOrNull()
            if (status !is PluginStatus.Ready) return@launch
            val job = setupJob.getAndSet(null) ?: return@launch
            externalReady.set(true)
            state.update { it.copy(interaction = null) }
            job.cancel(CancellationException("plugin setup completed"))
        }
    }

    suspend fun setup(pluginId: String): PluginSetupResult {
        val plugin = plugins[pluginId]
            ?: return PluginSetupResult(false, pluginId, false, false, error = "unknown plugin")
        if (plugin.kind == PluginKind.INTERNAL) {
            val status = runCatching { plugin.status() }.getOrNull()
            val ready = status is PluginStatus.Ready
            return PluginSetupResult(
                ok = ready,
                pluginId = pluginId,
                installed = true,
                ready = ready,
                status = status?.label,
                account = (status as? PluginStatus.Ready)?.account,
                error = status?.label?.takeIf { !ready },
            )
        }
        val records = recordStore.load().records
        var installed = records.containsKey(pluginId)
        if (records[pluginId]?.enabled == false) {
            return PluginSetupResult(
                ok = false,
                pluginId = pluginId,
                installed = installed,
                ready = false,
                error = strings.get(R.string.plugins_error_disabled),
            )
        }
        if (!isForeground()) {
            return PluginSetupResult(
                ok = false,
                pluginId = pluginId,
                installed = installed,
                ready = false,
                error = strings.get(R.string.plugins_error_foreground),
            )
        }
        if (!busy.compareAndSet(false, true)) {
            return PluginSetupResult(
                ok = false,
                pluginId = pluginId,
                installed = installed,
                ready = false,
                error = strings.get(R.string.plugins_error_busy),
            )
        }
        state.update { it.copy(error = null, busy = true) }
        cancelRequested = false
        externalReady.set(false)
        try {
            val record = recordStore.load().records[pluginId]
            if (record != null) {
                val current = runCatching { plugin.status() }.getOrNull()
                if (current is PluginStatus.Ready) {
                    return setupResult(plugin, current, installed = true)
                }
                if (current is PluginStatus.Failed) {
                    installer.install(plugin)
                }
            } else {
                if (!awaitInstallConfirm(plugin)) {
                    return cancelledResult(pluginId, installed = false)
                }
                installer.install(plugin)
            }
            installed = recordStore.load().records.containsKey(pluginId)
            if (cancelRequested) return cancelledResult(pluginId, installed)
            val before = runCatching { plugin.status() }.getOrElse { error ->
                PluginStatus.Failed(error.message ?: error.javaClass.simpleName)
            }
            if (before !is PluginStatus.Ready) {
                runSetupBody(plugin)
            }
            val finalStatus = runCatching { plugin.status() }.getOrElse { error ->
                PluginStatus.Failed(error.message ?: error.javaClass.simpleName)
            }
            if (finalStatus is PluginStatus.Ready && !isForeground()) {
                onSetupReady(strings.get(plugin.nameRes))
            }
            return setupResult(plugin, finalStatus, installed)
        } catch (error: CancellationException) {
            if (!currentCoroutineContext().isActive) throw error
            if (externalReady.get()) {
                installed = recordStore.load().records.containsKey(pluginId)
                val status = runCatching { plugin.status() }.getOrNull()
                if (status is PluginStatus.Ready) return setupResult(plugin, status, installed)
            }
            return cancelledResult(pluginId, installed)
        } catch (error: Exception) {
            val message = errorMessage(error)
            state.update { it.copy(error = message) }
            return PluginSetupResult(
                ok = false,
                pluginId = pluginId,
                installed = recordStore.load().records.containsKey(pluginId),
                ready = false,
                error = message,
            )
        } finally {
            cancelRequested = false
            externalReady.set(false)
            busy.set(false)
            state.update { it.copy(busy = false, interaction = null, operation = null) }
            runCatching { onRefreshStatuses() }
            onFlushContributions()
        }
    }

    private suspend fun awaitInstallConfirm(plugin: OfficialPlugin): Boolean {
        cancelRequested = false
        val waiter = CompletableDeferred<Boolean>()
        confirmWaiter.set(waiter)
        val name = strings.get(plugin.nameRes)
        state.update {
            it.copy(
                interaction = PluginInteraction(
                    pluginId = plugin.id,
                    title = strings.format(R.string.plugins_setup_title, name),
                    stageTitle = strings.get(R.string.plugins_confirm_install_title),
                    stageMessage = strings.format(
                        R.string.plugins_confirm_install_message,
                        name,
                        strings.get(plugin.sizeLabelRes),
                    ),
                    confirmInstall = true,
                    cancellable = true,
                ),
            )
        }
        val confirmed = try {
            waiter.await()
        } finally {
            confirmWaiter.compareAndSet(waiter, null)
        }
        if (confirmed && !cancelRequested) {
            state.update {
                it.copy(
                    interaction = it.interaction?.copy(
                        stageTitle = strings.get(R.string.plugins_stage_installing),
                        stageMessage = null,
                        confirmInstall = false,
                    ),
                )
            }
        }
        return confirmed && !cancelRequested
    }

    private suspend fun runSetupBody(plugin: OfficialPlugin) {
        val ui = ManagerUi(plugin)
        val done = CompletableDeferred<Unit>()
        val job = scope.launch {
            try {
                plugin.setup(ui)
                done.complete(Unit)
            } catch (error: Throwable) {
                done.completeExceptionally(error)
            }
        }
        setupJob.set(job)
        try {
            done.await()
        } catch (error: CancellationException) {
            job.cancel()
            throw error
        } finally {
            setupJob.compareAndSet(job, null)
            state.update { it.copy(interaction = null) }
        }
    }

    private fun setupResult(plugin: OfficialPlugin, status: PluginStatus, installed: Boolean): PluginSetupResult {
        val ready = status is PluginStatus.Ready
        return PluginSetupResult(
            ok = ready,
            pluginId = plugin.id,
            installed = installed,
            ready = ready,
            status = status.label,
            account = (status as? PluginStatus.Ready)?.account,
            error = status.label.takeIf { !ready },
        )
    }

    private fun cancelledResult(pluginId: String, installed: Boolean): PluginSetupResult = PluginSetupResult(
        ok = false,
        pluginId = pluginId,
        installed = installed,
        ready = false,
        error = strings.get(R.string.plugins_setup_cancelled),
    )

    private fun errorMessage(error: Throwable): String =
        error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName

    private inner class ManagerUi(
        private val plugin: OfficialPlugin,
    ) : PluginUi {
        override fun stage(
            titleRes: Int,
            messageRes: Int?,
            url: String?,
            progress: Int?,
            imagePath: String?,
        ) {
            if (cancelRequested) return
            state.update { current ->
                val base = current.interaction?.takeIf { it.pluginId == plugin.id }
                    ?: PluginInteraction(
                        pluginId = plugin.id,
                        title = strings.format(R.string.plugins_setup_title, strings.get(plugin.nameRes)),
                        stageTitle = strings.get(titleRes),
                    )
                current.copy(
                    interaction = base.copy(
                        stageTitle = strings.get(titleRes),
                        stageMessage = messageRes?.let { strings.get(it) },
                        url = url,
                        imagePath = imagePath,
                        progress = progress,
                        confirmInstall = false,
                    ),
                )
            }
        }

        override fun progress(percent: Int?, labelRes: Int) {
            val stage = strings.get(labelRes)
            val title = strings.format(R.string.plugins_operation_install, strings.get(plugin.nameRes))
            state.update { current ->
                current.copy(
                    operation = PluginOperation(plugin.id, title, stage, percent),
                    interaction = current.interaction
                        ?.takeIf { it.pluginId == plugin.id && !it.confirmInstall }
                        ?.copy(progress = percent),
                )
            }
            notifyInstall(strings.get(plugin.nameRes), stage, percent)
        }

        override fun cancelled(): Boolean = cancelRequested
    }
}
