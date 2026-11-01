package io.github.whywood.deepdeep.plugin

import dev.pi.coding.tools.CodingTool
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.runtime.PluginController
import io.github.whywood.deepdeep.runtime.PluginSetupResult
import io.github.whywood.deepdeep.runtime.SessionBinding
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PluginManager(
    plugins: List<OfficialPlugin>,
    private val recordStore: PluginRecordStore,
    private val skillStore: PluginSkillStore,
    private val settingsStore: PluginSettingsStore,
    private val strings: PluginStrings,
    private val scope: CoroutineScope,
    private val isForeground: () -> Boolean,
    private val notifyInstall: (label: String, stage: String?, progress: Int?) -> Unit = { _, _, _ -> },
    private val notifyInstallFinished: (label: String, success: Boolean, error: String?) -> Unit = { _, _, _ -> },
    private val onContributionsChanged: () -> Unit = {},
    private val onSetupReady: (label: String) -> Unit = { _ -> },
) : PluginController {
    private val pluginsById = plugins.associateBy { it.id }
    private val _state = MutableStateFlow(PluginsUiState())
    val state: StateFlow<PluginsUiState> = _state.asStateFlow()

    override val setupPluginIds: List<String> = plugins
        .filter { it.kind == PluginKind.EXTERNAL }
        .map { it.id }
        .sorted()

    private val busy = AtomicBoolean(false)
    @Volatile private var contributionsPending = false

    private val statusCache = PluginStatusCache(
        plugins = pluginsById,
        recordStore = recordStore,
        settingsStore = settingsStore,
        scope = scope,
        state = _state,
        isBusy = { busy.get() },
    )

    private val installer = PluginInstaller(
        recordStore = recordStore,
        skillStore = skillStore,
        strings = strings,
        state = _state,
        notifyInstall = notifyInstall,
        notifyInstallFinished = notifyInstallFinished,
        onContributionsPending = { contributionsPending = true },
        onRefreshStatuses = { statusCache.refreshStatuses() },
    )

    private val setupSession = PluginSetupSession(
        plugins = pluginsById,
        recordStore = recordStore,
        installer = installer,
        strings = strings,
        scope = scope,
        state = _state,
        busy = busy,
        isForeground = isForeground,
        onSetupReady = onSetupReady,
        notifyInstall = notifyInstall,
        onRefreshStatuses = { statusCache.refreshStatuses() },
        onFlushContributions = { flushContributions() },
    )

    suspend fun start() {
        _state.update { it.copy(loading = true) }
        val loaded = recordStore.load()
        val recordsCorrupt = loaded.error != null
        val recordsBackedUp = recordsCorrupt &&
            runCatching { recordStore.backupCorrupt() }.getOrDefault(false)
        val records = loaded.records.toMutableMap()
        if (recordsCorrupt && !recordsBackedUp) {
            val notice = strings.get(R.string.plugins_error_records_unbacked)
            statusCache.markRecordsCorrupt(notice)
            statusCache.refreshStatuses()
            _state.update { it.copy(error = notice) }
            return
        }
        pluginsById.values.forEach { plugin ->
            runCatching { plugin.recover() }
            if (plugin.kind == PluginKind.INTERNAL && plugin.id !in records) {
                val skills = runCatching { plugin.skills() }.getOrDefault(emptyList())
                val record = PluginRecord(
                    id = plugin.id,
                    version = plugin.version,
                    installedAt = System.currentTimeMillis(),
                    skillIds = skills.map { it.id },
                    enabled = true,
                )
                runCatching { recordStore.upsert(record) }
                records[plugin.id] = record
            }
            statusCache.applySettings(plugin)
            val record = records[plugin.id] ?: return@forEach
            val skills = runCatching { plugin.skills() }.getOrDefault(emptyList())
            runCatching { skillStore.sync(plugin.id, skills, record.skillIds, record.enabled) }
            val ids = skills.map { it.id }
            if (ids != record.skillIds) {
                runCatching { recordStore.upsert(record.copy(skillIds = ids)) }
                records[plugin.id] = record.copy(skillIds = ids)
            }
        }
        statusCache.refreshStatuses()
        if (recordsCorrupt) {
            val notice = strings.get(R.string.plugins_error_records_corrupt)
            statusCache.markRecordsCorrupt(notice)
            _state.update { it.copy(error = notice) }
        }
    }

    fun refresh() {
        statusCache.refresh()
    }

    fun refreshIfStale(minIntervalMillis: Long = PLUGIN_REFRESH_STALE_INTERVAL_MS) {
        statusCache.refreshIfStale(minIntervalMillis)
    }

    fun tools(binding: SessionBinding): List<CodingTool> = _state.value.plugins
        .filter { it.installed && it.enabled }
        .flatMap { snapshot ->
            when (snapshot.plugin.kind) {
                PluginKind.EXTERNAL -> snapshot.plugin.tools()
                PluginKind.INTERNAL -> snapshot.plugin.tools(binding)
            }
        }
        .distinctBy { it.name }

    fun tools(): List<CodingTool> = tools(SessionBinding())

    fun setEnabled(pluginId: String, enabled: Boolean) {
        val plugin = pluginsById[pluginId] ?: return
        if (_state.value.snapshot(pluginId)?.installed != true) return
        _state.update { current ->
            current.copy(
                plugins = current.plugins.map { snapshot ->
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

    fun setSetting(pluginId: String, key: String, value: String) {
        val plugin = pluginsById[pluginId] ?: return
        val spec = plugin.settings().firstOrNull { it.key == key } ?: return
        val normalized = spec.normalize(value) ?: return
        val current = _state.value.snapshot(pluginId) ?: return
        if (current.settings[key] == normalized) return
        _state.update { state ->
            state.copy(
                plugins = state.plugins.map { snapshot ->
                    if (snapshot.plugin.id == pluginId) {
                        snapshot.copy(settings = snapshot.settings + (key to normalized))
                    } else {
                        snapshot
                    }
                },
            )
        }
        scope.launch {
            runCatching {
                settingsStore.set(pluginId, key, normalized)
                val values = statusCache.applySettings(plugin)
                val record = recordStore.load().records[pluginId]
                if (record != null) {
                    val skills = runCatching { plugin.skills() }.getOrDefault(emptyList())
                    runCatching { skillStore.sync(pluginId, skills, record.skillIds, record.enabled) }
                    val ids = skills.map { it.id }
                    if (ids != record.skillIds) recordStore.upsert(record.copy(skillIds = ids))
                }
                _state.update { state ->
                    state.copy(
                        plugins = state.plugins.map { snapshot ->
                            if (snapshot.plugin.id == pluginId) snapshot.copy(settings = values) else snapshot
                        },
                    )
                }
                onContributionsChanged()
            }.onFailure { error ->
                _state.update { it.copy(error = errorMessage(error)) }
            }
        }
    }

    fun install(pluginId: String) {
        val plugin = pluginsById[pluginId] ?: return
        if (plugin.kind == PluginKind.INTERNAL) return
        launchBusy { installer.install(plugin) }
    }

    fun update(pluginId: String) {
        val plugin = pluginsById[pluginId] ?: return
        if (plugin.kind == PluginKind.INTERNAL) return
        launchBusy { installer.install(plugin) }
    }

    fun uninstall(pluginId: String) {
        val plugin = pluginsById[pluginId] ?: return
        if (plugin.kind == PluginKind.INTERNAL) return
        launchBusy { installer.uninstall(plugin) }
    }

    fun connect(pluginId: String) {
        scope.launch {
            runCatching { setup(pluginId) }
        }
    }

    fun runAction(pluginId: String, actionId: String) {
        val plugin = pluginsById[pluginId] ?: return
        if (!enabled(pluginId)) {
            _state.update { it.copy(error = strings.get(R.string.plugins_error_disabled)) }
            return
        }
        launchBusy {
            plugin.runAction(actionId, setupSession.ui(plugin))
            if (!isForeground()) {
                val status = runCatching { plugin.status() }.getOrNull()
                if (status is PluginStatus.Ready) onSetupReady(strings.get(plugin.nameRes))
            }
        }
    }

    fun onAppForeground() {
        setupSession.onAppForeground()
    }

    override suspend fun setup(pluginId: String): PluginSetupResult = setupSession.setup(pluginId)

    fun confirmInstall() {
        setupSession.confirmInstall()
    }

    fun cancelInteraction() {
        setupSession.cancelInteraction()
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
        setupSession.beginOperation()
        _state.update { it.copy(error = null, busy = true) }
        val job = scope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update { it.copy(error = errorMessage(error)) }
            } finally {
                setupSession.resetExternalReady()
                _state.update { it.copy(busy = false, operation = null, interaction = null) }
                busy.set(false)
                runCatching { refreshStatuses() }
                flushContributions()
            }
        }
        setupSession.trackJob(job)
    }

    private fun enabled(pluginId: String): Boolean =
        _state.value.snapshot(pluginId)?.enabled ?: true

    private suspend fun refreshStatuses() {
        statusCache.refreshStatuses()
    }

    private fun errorMessage(error: Throwable): String =
        error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName

    private fun flushContributions() {
        if (!contributionsPending) return
        contributionsPending = false
        onContributionsChanged()
    }
}
