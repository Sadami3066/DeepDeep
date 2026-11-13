package io.github.whywood.deepdeep.plugin.host.extensions.device

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import io.github.whywood.deepdeep.plugin.api.BackgroundService
import io.github.whywood.deepdeep.plugin.api.ForegroundServiceOptions
import io.github.whywood.deepdeep.plugin.api.PluginBackgroundKind
import io.github.whywood.deepdeep.plugin.api.PluginBackgroundTaskInfo
import io.github.whywood.deepdeep.plugin.host.components.PluginHostService
import io.github.whywood.deepdeep.plugin.host.external.ExternalPluginRecordStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class BackgroundPluginService(private val deps: DeviceServiceDeps) : BackgroundService {
    private val store = PluginBackgroundStore(backgroundStoreFile(deps.rootDir))
    private val scheduler = PluginBackgroundScheduler(deps.appContext, deps.pluginId)

    init {
        deps.scope.coroutineContext[Job]?.invokeOnCompletion { runCatching { scheduler.cancelScheduled() } }
        deps.scope.launch {
            runCatching { if (pluginEnabled()) restore() }
        }
    }

    override suspend fun schedule(
        id: String,
        kind: PluginBackgroundKind,
        triggerAtMs: Long?,
        intervalMs: Long?,
        requiresNetwork: Boolean,
        requiresCharging: Boolean,
    ): Boolean = withContext(Dispatchers.IO) {
        if (!pluginEnabled()) return@withContext false
        val taskId = id.trim()
        if (taskId.isEmpty() || taskId.length > MAX_TASK_ID_LENGTH) return@withContext false
        if (taskId.any { it.isISOControl() || it.isWhitespace() }) return@withContext false
        val now = System.currentTimeMillis()
        val exact = when (kind) {
            PluginBackgroundKind.EXACT -> scheduler.canScheduleExactAlarms()
            else -> true
        }
        val record = BackgroundTaskRecord(
            id = taskId,
            kind = kind,
            triggerAtMs = when (kind) {
                PluginBackgroundKind.ONCE, PluginBackgroundKind.EXACT ->
                    (triggerAtMs ?: now).coerceAtLeast(now)

                PluginBackgroundKind.PERIODIC ->
                    (triggerAtMs ?: (now + (intervalMs ?: MIN_PERIODIC_INTERVAL_MS).coerceAtLeast(MIN_PERIODIC_INTERVAL_MS)))
                        .coerceAtLeast(now)

                PluginBackgroundKind.BOOT -> null
                else -> triggerAtMs?.coerceAtLeast(now)
            },
            intervalMs = when (kind) {
                PluginBackgroundKind.PERIODIC ->
                    (intervalMs ?: MIN_PERIODIC_INTERVAL_MS).coerceAtLeast(MIN_PERIODIC_INTERVAL_MS)

                else -> intervalMs?.takeIf { it > 0L }
            },
            requiresNetwork = requiresNetwork,
            requiresCharging = requiresCharging,
            exact = exact,
            lastRunAtMs = null,
        )
        store.upsert(record)
        scheduler.enqueue(record)
    }

    override suspend fun cancel(id: String): Boolean = withContext(Dispatchers.IO) {
        val taskId = id.trim()
        if (taskId.isEmpty()) return@withContext false
        scheduler.cancel(taskId)
        store.remove(taskId)
    }

    override suspend fun tasks(): List<PluginBackgroundTaskInfo> = withContext(Dispatchers.IO) {
        store.load().map(::taskInfo)
    }

    override fun startForeground(options: ForegroundServiceOptions): Boolean =
        PluginHostService.start(deps.appContext, deps.pluginId, options, deps.firstPageId)

    override fun updateForeground(options: ForegroundServiceOptions): Boolean =
        PluginHostService.update(deps.appContext, deps.pluginId, options)

    override fun stopForeground(id: String): Boolean =
        PluginHostService.stop(deps.appContext, deps.pluginId)

    override val batteryOptimizationIgnored: Boolean
        get() = runCatching {
            powerManager()?.isIgnoringBatteryOptimizations(deps.appContext.packageName) == true
        }.getOrDefault(false)

    override fun requestIgnoreBatteryOptimizations(): Boolean = startActivitySafely(
        deps.appContext,
        Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${deps.appContext.packageName}"),
        ),
    )

    override fun openBatteryOptimizationSettings(): Boolean = startActivitySafely(
        deps.appContext,
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
    )

    private suspend fun restore() {
        store.load().forEach { task ->
            if (task.kind == PluginBackgroundKind.BOOT) return@forEach
            scheduler.enqueue(task)
        }
    }

    private suspend fun pluginEnabled(): Boolean {
        val provider = deps.pluginEnabled
        if (provider != null) return runCatching { provider(deps.pluginId) }.getOrDefault(true)
        return runCatching {
            ExternalPluginRecordStore(File(deps.appContext.filesDir, "plugins/plugins.json"))
                .get(deps.pluginId)
                ?.enabled
                ?: true
        }.getOrDefault(true)
    }

    private fun powerManager(): PowerManager? =
        deps.appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private companion object {
        const val MAX_TASK_ID_LENGTH = 128
    }
}
