package io.github.whywood.deepdeep.plugin.host.extensions.device

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.github.whywood.deepdeep.plugin.api.PluginBackgroundKind
import io.github.whywood.deepdeep.plugin.host.components.PluginSystemEventReceiver
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal const val MIN_PERIODIC_INTERVAL_MS: Long = 15 * 60_000L

@SuppressLint("ScheduleExactAlarm")
internal class PluginBackgroundScheduler(
    private val context: Context,
    private val pluginId: String,
) {
    private val scheduledAlarms = ConcurrentHashMap.newKeySet<String>()

    fun enqueue(task: BackgroundTaskRecord): Boolean = runCatching {
        when (task.kind) {
            PluginBackgroundKind.ONCE -> enqueueOnce(task)
            PluginBackgroundKind.PERIODIC -> enqueuePeriodic(task)
            PluginBackgroundKind.EXACT -> scheduleAlarm(task)
            PluginBackgroundKind.BOOT -> true
            else -> false
        }
    }.getOrDefault(false)

    fun cancel(taskId: String) {
        runCatching { WorkManager.getInstance(context).cancelUniqueWork(uniqueName(taskId)) }
        cancelAlarm(taskId)
    }

    fun cancelScheduled() {
        runCatching { WorkManager.getInstance(context).cancelAllWorkByTag(tag) }
        scheduledAlarms.toList().forEach(::cancelAlarm)
    }

    fun canScheduleExactAlarms(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        runCatching { alarmManager()?.canScheduleExactAlarms() == true }.getOrDefault(false)
    } else {
        true
    }

    private fun enqueueOnce(task: BackgroundTaskRecord): Boolean {
        val builder = OneTimeWorkRequestBuilder<PluginBackgroundWorker>()
            .setConstraints(constraintsOf(task))
            .setInputData(inputDataOf(task))
            .addTag(tag)
        val triggerAt = task.triggerAtMs
        if (triggerAt != null) {
            builder.setInitialDelay(
                (triggerAt - System.currentTimeMillis()).coerceAtLeast(0L),
                TimeUnit.MILLISECONDS,
            )
        }
        WorkManager.getInstance(context)
            .enqueueUniqueWork(uniqueName(task.id), ExistingWorkPolicy.REPLACE, builder.build())
        return true
    }

    private fun enqueuePeriodic(task: BackgroundTaskRecord): Boolean {
        val interval = (task.intervalMs ?: MIN_PERIODIC_INTERVAL_MS).coerceAtLeast(MIN_PERIODIC_INTERVAL_MS)
        val request = PeriodicWorkRequestBuilder<PluginBackgroundWorker>(interval, TimeUnit.MILLISECONDS)
            .setConstraints(constraintsOf(task))
            .setInputData(inputDataOf(task))
            .addTag(tag)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(uniqueName(task.id), ExistingPeriodicWorkPolicy.UPDATE, request)
        return true
    }

    private fun scheduleAlarm(task: BackgroundTaskRecord): Boolean {
        val triggerAt = task.triggerAtMs ?: return false
        val manager = alarmManager() ?: return false
        val pendingIntent = alarmPendingIntent(context, pluginId, task.id)
        val exact = canScheduleExactAlarms()
        val scheduled = if (exact) {
            runCatching {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }.isSuccess || runCatching {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }.isSuccess
        } else {
            runCatching {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }.isSuccess
        }
        if (scheduled) scheduledAlarms += task.id
        return scheduled
    }

    private fun cancelAlarm(taskId: String) {
        scheduledAlarms -= taskId
        runCatching {
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                alarmRequestCode(pluginId, taskId),
                alarmIntent(context, pluginId, taskId),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            ) ?: return
            alarmManager()?.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun constraintsOf(task: BackgroundTaskRecord): Constraints = Constraints.Builder()
        .setRequiredNetworkType(
            if (task.requiresNetwork) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED,
        )
        .setRequiresCharging(task.requiresCharging)
        .build()

    private fun inputDataOf(task: BackgroundTaskRecord) = workDataOf(
        PluginBackgroundWorker.KEY_PLUGIN_ID to pluginId,
        PluginBackgroundWorker.KEY_TASK_ID to task.id,
        PluginBackgroundWorker.KEY_KIND to task.kind.wire,
    )

    private fun uniqueName(taskId: String): String = "plugin-background:$pluginId:$taskId"

    private val tag: String
        get() = "plugin-background:$pluginId"

    private fun alarmManager(): AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
}

internal fun alarmIntent(context: Context, pluginId: String, taskId: String): Intent =
    Intent(context, PluginSystemEventReceiver::class.java)
        .setAction(PluginSystemEventReceiver.ACTION_EXACT_ALARM)
        .putExtra(PluginSystemEventReceiver.EXTRA_PLUGIN_ID, pluginId)
        .putExtra(PluginSystemEventReceiver.EXTRA_TASK_ID, taskId)

internal fun alarmPendingIntent(context: Context, pluginId: String, taskId: String): PendingIntent =
    PendingIntent.getBroadcast(
        context,
        alarmRequestCode(pluginId, taskId),
        alarmIntent(context, pluginId, taskId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

internal fun alarmRequestCode(pluginId: String, taskId: String): Int =
    ("$pluginId:$taskId").hashCode()
