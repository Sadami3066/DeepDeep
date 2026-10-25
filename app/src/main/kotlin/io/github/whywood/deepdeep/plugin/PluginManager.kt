package io.github.whywood.deepdeep.plugin

import dev.pi.coding.tools.CodingTool
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.runtime.PluginController
import io.github.whywood.deepdeep.runtime.PluginSetupResult
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PluginManager(
    plugins: List<OfficialPlugin>,
    private val recordStore: PluginRecordStore,
    private val skillStore: PluginSkillStore,
    private val strings: PluginStrings,
    private val scope: CoroutineScope,
    private val isForeground: () -> Boolean,
    private val notifyInstall: (label: String, stage: String?, progress: Int?) -> Unit = { _, _, _ -> },
    private val notifyInstallFinished: (label: String, success: Boolean, error: String?) -> Unit = { _, _, _ -> },
    private val onContributionsChanged: () -> Unit = {},
    private val onSetupReady: (label: String) -> Unit = { _ -> },
) : PluginController {
    private val plugins = plugins.associateBy { it.id }
    private val _state = MutableStateFlow(PluginsUiState())
    val state: StateFlow<PluginsUiState> = _state.asStateFlow()

    private val busy = AtomicBoolean(false)
    private val confirmWaiter = AtomicReference<CompletableDeferred<Boolean>?>(null)
    private val setupJob = AtomicReference<Job?>(null)
    private val refreshJob = AtomicReference<Job?>(null)
    private val externalReady = AtomicBoolean(false)
    @Volatile private var contributionsPending = false
    @Volatile private var cancelRequested = false
    @Volatile private var lastRefreshAt = 0L

    suspend fun start() {
        _state.update { it.copy(loading = true) }
        val records = recordStore.load().records
        plugins.values.forEach { plugin ->
            runCatching { plugin.recover() }
            val record = records[plugin.id] ?: return@forEach
            val skills = runCatching { plugin.skills() }.getOrDefault(emptyList())
            runCatching { skillStore.ensureRepo(plugin.id, skills) }
            if (!record.enabled) {
                runCatching { skillStore.setActive(plugin.id, skills, false) }
            }
        }
        refreshStatuses()
    }

    fun refresh() {
        if (busy.get()) return
        if (refreshJob.get()?.isActive == true) return
        refreshJob.set(
            scope.launch {
                runCatching { refreshStatuses() }
            },
        )
    }

    fun refreshIfStale(minIntervalMillis: Long = REFRESH_STALE_INTERVAL_MS) {
        if (System.currentTimeMillis() - lastRefreshAt < minIntervalMillis) return
        refresh()
    }

    fun tools(): List<CodingTool> = _state.value.plugins
        .filter { it.installed && it.enabled }
        .flatMap { snapshot -> snapshot.plugin.tools() }
        .distinctBy { it.name }

    fun setEnabled(pluginId: String, enabled: Boolean) {
        val plugin = plugins[pluginId] ?: return
        if (_state.value.snapshot(pluginId)?.installed != true) return
        _state.update { state ->
            state.copy(
                plugins = state.plugins.map { snapshot ->
                    if (snapshot.plugin.id != pluginId) {
                        snapshot
                    } else {
                        snapshot.copy(enabled = enabled, status = snapshot.status.takeIf { enabled })
                    }
                },
            )
        }
        scope.launch {
            runCatching {
                recordStore.setEnabled(pluginId, enabled)
                skillStore.setActive(pluginId, plugin.skills(), enabled)
                refreshStatuses()
                onContributionsChanged()
            }.onFailure { error ->
                _state.update { it.copy(error = errorMessage(error)) }
            }
        }
    }

    fun install(pluginId: String) {
        val plugin = plugins[pluginId] ?: return
        launchBusy { installInternal(plugin) }
    }

    fun update(pluginId: String) {
        val plugin = plugins[pluginId] ?: return
        launchBusy { installInternal(plugin) }
    }

    fun uninstall(pluginId: String) {
        val plugin = plugins[pluginId] ?: return
        launchBusy {
            val record = recordStore.load().records[pluginId]
            plugin.uninstall()
            val skillIds = record?.skillIds ?: plugin.skills().map { it.id }
            runCatching { skillStore.remove(plugin.id, skillIds) }
            recordStore.remove(plugin.id)
            refreshStatuses()
            contributionsPending = true
        }
    }

    fun connect(pluginId: String) {
        scope.launch {
            runCatching { setup(pluginId) }
        }
    }

    fun runAction(pluginId: String, actionId: String) {
        val plugin = plugins[pluginId] ?: return
        if (!enabled(pluginId)) {
            _state.update { it.copy(error = strings.get(R.string.plugins_error_disabled)) }
            return
        }
        launchBusy {
            plugin.runAction(actionId, ManagerUi(plugin))
            if (!isForeground()) {
                val status = runCatching { plugin.status() }.getOrNull()
                if (status is PluginStatus.Ready) onSetupReady(strings.get(plugin.nameRes))
            }
        }
    }

    fun onAppForeground() {
        if (!busy.get()) return
        val pluginId = _state.value.interaction?.pluginId ?: return
        val plugin = plugins[pluginId] ?: return
        scope.launch {
            val status = runCatching { plugin.status() }.getOrNull()
            if (status !is PluginStatus.Ready) return@launch
            val job = setupJob.getAndSet(null) ?: return@launch
            externalReady.set(true)
            _state.update { it.copy(interaction = null) }
            job.cancel(CancellationException("plugin setup completed"))
        }
    }

    override suspend fun setup(pluginId: String): PluginSetupResult {
        val plugin = plugins[pluginId]
            ?: return PluginSetupResult(false, pluginId, false, false, error = "unknown plugin")
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
        _state.update { it.copy(error = null, busy = true) }
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
                    installInternal(plugin)
                }
            } else {
                if (!awaitInstallConfirm(plugin)) {
                    return cancelledResult(pluginId, installed = false)
                }
                installInternal(plugin)
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
            _state.update { it.copy(error = message) }
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
            _state.update { it.copy(busy = false, interaction = null, operation = null) }
            runCatching { refreshStatuses() }
            flushContributions()
        }
    }

    fun confirmInstall() {
        confirmWaiter.get()?.complete(true)
    }

    fun cancelInteraction() {
        cancelRequested = true
        externalReady.set(false)
        confirmWaiter.getAndSet(null)?.complete(false)
        setupJob.getAndSet(null)?.cancel(CancellationException("plugin setup cancelled"))
        _state.update { it.copy(interaction = null) }
    }

    fun promptBody(): String? {
        val bodies = _state.value.plugins
            .filter { it.installed && it.enabled }
            .mapNotNull { snapshot ->
                snapshot.status?.let { status -> snapshot.plugin.promptBody(status) }
            }
            .filter { it.isNotBlank() }
        return bodies.takeIf { it.isNotEmpty() }?.joinToString("\n\n")
    }

    private fun launchBusy(block: suspend () -> Unit) {
        if (!busy.compareAndSet(false, true)) {
            _state.update { it.copy(error = strings.get(R.string.plugins_error_busy)) }
            return
        }
        cancelRequested = false
        externalReady.set(false)
        _state.update { it.copy(error = null, busy = true) }
        val job = scope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update { it.copy(error = errorMessage(error)) }
            } finally {
                externalReady.set(false)
                _state.update { it.copy(busy = false, operation = null, interaction = null) }
                busy.set(false)
                runCatching { refreshStatuses() }
                flushContributions()
            }
        }
        setupJob.set(job)
        job.invokeOnCompletion { setupJob.compareAndSet(job, null) }
    }

    private suspend fun installInternal(plugin: OfficialPlugin) {
        val availability = runCatching { plugin.availability() }.getOrElse { error ->
            PluginAvailability.Unavailable(error.message ?: error.javaClass.simpleName)
        }
        if (availability is PluginAvailability.Unavailable) {
            throw IllegalStateException(availability.reason)
        }
        val previous = recordStore.load().records[plugin.id]
        val previousSkills = previous?.skillIds.orEmpty()
        val enabled = previous?.enabled ?: true
        val name = strings.get(plugin.nameRes)
        val title = strings.format(R.string.plugins_operation_install, name)
        setOperation(plugin.id, title, strings.get(R.string.plugins_stage_preparing), null)
        notifyInstall(name, strings.get(R.string.plugins_stage_preparing), null)
        try {
            plugin.install(progressAdapter(plugin, title))
            val skills = plugin.skills()
            recordStore.upsert(
                PluginRecord(
                    id = plugin.id,
                    version = plugin.version,
                    installedAt = System.currentTimeMillis(),
                    skillIds = skills.map { it.id },
                    enabled = enabled,
                ),
            )
            val skillError = runCatching {
                skillStore.sync(plugin.id, skills, previousSkills, enabled)
            }.exceptionOrNull()
            if (skillError != null) {
                _state.update { it.copy(error = errorMessage(skillError)) }
            }
            contributionsPending = true
            notifyInstallFinished(name, true, null)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            runCatching { plugin.uninstall() }
            runCatching { skillStore.remove(plugin.id, previousSkills) }
            notifyInstallFinished(name, false, errorMessage(error))
            throw error
        } finally {
            _state.update { it.copy(operation = null) }
        }
    }

    private suspend fun awaitInstallConfirm(plugin: OfficialPlugin): Boolean {
        cancelRequested = false
        val waiter = CompletableDeferred<Boolean>()
        confirmWaiter.set(waiter)
        val name = strings.get(plugin.nameRes)
        _state.update {
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
            _state.update {
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
            _state.update { it.copy(interaction = null) }
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

    private suspend fun refreshStatuses() {
        if (busy.get()) return
        val records = recordStore.load()
        val snapshots = plugins.values.map { plugin ->
            val availability = runCatching { plugin.availability() }.getOrElse { error ->
                PluginAvailability.Unavailable(error.message ?: error.javaClass.simpleName)
            }
            val record = records.records[plugin.id]
            val enabled = record?.enabled ?: true
            val status = if (record == null || !enabled) {
                null
            } else {
                runCatching { plugin.status() }.getOrElse { error ->
                    PluginStatus.Failed(error.message ?: error.javaClass.simpleName)
                }
            }
            PluginSnapshot(plugin, record?.version, availability, status, enabled)
        }
        lastRefreshAt = System.currentTimeMillis()
        _state.update {
            it.copy(
                plugins = snapshots,
                loading = false,
                error = records.error ?: it.error,
            )
        }
    }

    private fun progressAdapter(plugin: OfficialPlugin, title: String): PluginProgress = object : PluginProgress {
        override fun report(percent: Int?, labelRes: Int) {
            val stage = strings.get(labelRes)
            _state.update { state ->
                state.copy(
                    operation = PluginOperation(plugin.id, title, stage, percent),
                    interaction = state.interaction
                        ?.takeIf { it.pluginId == plugin.id && !it.confirmInstall }
                        ?.copy(progress = percent),
                )
            }
            notifyInstall(strings.get(plugin.nameRes), stage, percent)
        }
    }

    private fun setOperation(pluginId: String, title: String, stage: String?, progress: Int?) {
        _state.update { it.copy(operation = PluginOperation(pluginId, title, stage, progress)) }
    }

    private fun enabled(pluginId: String): Boolean =
        _state.value.snapshot(pluginId)?.enabled ?: true

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
            _state.update { state ->
                val base = state.interaction?.takeIf { it.pluginId == plugin.id }
                    ?: PluginInteraction(
                        pluginId = plugin.id,
                        title = strings.format(R.string.plugins_setup_title, strings.get(plugin.nameRes)),
                        stageTitle = strings.get(titleRes),
                    )
                state.copy(
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
            _state.update { state ->
                state.copy(
                    operation = PluginOperation(plugin.id, title, stage, percent),
                    interaction = state.interaction
                        ?.takeIf { it.pluginId == plugin.id && !it.confirmInstall }
                        ?.copy(progress = percent),
                )
            }
            notifyInstall(strings.get(plugin.nameRes), stage, percent)
        }

        override fun cancelled(): Boolean = cancelRequested
    }

    private fun errorMessage(error: Throwable): String =
        error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName

    private fun flushContributions() {
        if (!contributionsPending) return
        contributionsPending = false
        onContributionsChanged()
    }

    private companion object {
        const val REFRESH_STALE_INTERVAL_MS = 30_000L
    }
}
