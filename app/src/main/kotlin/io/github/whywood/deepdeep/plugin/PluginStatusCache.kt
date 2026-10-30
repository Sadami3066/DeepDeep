package io.github.whywood.deepdeep.plugin

import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal const val PLUGIN_REFRESH_STALE_INTERVAL_MS = 120_000L

internal class PluginStatusCache(
    private val plugins: Map<String, OfficialPlugin>,
    private val recordStore: PluginRecordStore,
    private val settingsStore: PluginSettingsStore,
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<PluginsUiState>,
    private val isBusy: () -> Boolean,
) {
    private val mutex = Mutex()
    private val refreshJob = AtomicReference<Job?>(null)
    @Volatile private var recordsCorruptNotice: String? = null
    @Volatile private var lastRefreshAt = 0L

    fun refresh() {
        if (isBusy()) return
        if (refreshJob.get()?.isActive == true) return
        refreshJob.set(
            scope.launch {
                runCatching { refreshStatuses() }
            },
        )
    }

    fun refreshIfStale(minIntervalMillis: Long = PLUGIN_REFRESH_STALE_INTERVAL_MS) {
        if (minIntervalMillis > 0 && System.currentTimeMillis() - lastRefreshAt < minIntervalMillis) return
        refresh()
    }

    fun markRecordsCorrupt(notice: String) {
        recordsCorruptNotice = notice
    }

    suspend fun refreshStatuses() {
        if (isBusy()) return
        mutex.withLock {
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
                PluginSnapshot(
                    plugin = plugin,
                    installedVersion = record?.version,
                    availability = availability,
                    status = status,
                    enabled = enabled,
                    settings = resolveSettings(plugin),
                )
            }
            lastRefreshAt = System.currentTimeMillis()
            val notice = recordsCorruptNotice
            val clearCorruptNotice = records.error == null &&
                notice != null &&
                state.value.error == notice
            if (clearCorruptNotice) recordsCorruptNotice = null
            state.update {
                it.copy(
                    plugins = snapshots,
                    loading = false,
                    error = records.error ?: if (clearCorruptNotice) null else it.error,
                )
            }
        }
    }

    suspend fun applySettings(plugin: OfficialPlugin): Map<String, String> {
        val values = resolveSettings(plugin)
        runCatching { plugin.onSettingsChanged(values) }
        return values
    }

    private suspend fun resolveSettings(plugin: OfficialPlugin): Map<String, String> {
        val specs = plugin.settings()
        if (specs.isEmpty()) return emptyMap()
        val stored = runCatching { settingsStore.readAll(plugin.id) }.getOrDefault(emptyMap())
        val values = LinkedHashMap<String, String>()
        specs.forEach { spec -> values[spec.key] = stored[spec.key] ?: spec.default }
        return values
    }
}
