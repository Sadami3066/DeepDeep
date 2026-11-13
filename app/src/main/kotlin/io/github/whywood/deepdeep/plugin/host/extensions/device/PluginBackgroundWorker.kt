package io.github.whywood.deepdeep.plugin.host.extensions.device

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.whywood.deepdeep.plugin.api.PluginBackgroundKind
import java.io.File

class PluginBackgroundWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val pluginId = inputData.getString(KEY_PLUGIN_ID)?.let(::sanitizedPluginId)
            ?: return Result.success()
        val taskId = inputData.getString(KEY_TASK_ID)?.trim()?.takeIf { it.isNotEmpty() }
            ?: return Result.success()
        val kind = PluginBackgroundKind.fromWire(inputData.getString(KEY_KIND).orEmpty())
            ?: return Result.success()
        val delivered = PluginBackgroundDispatcher.deliver(pluginId, taskId, kind)
        if (!delivered) {
            return if (runAttemptCount < MAX_DELIVERY_ATTEMPTS) Result.retry() else Result.success()
        }
        val store = PluginBackgroundStore(
            backgroundStoreFile(File(applicationContext.filesDir, "plugins/$pluginId")),
        )
        val task = store.record(taskId) ?: return Result.success()
        when (task.kind) {
            PluginBackgroundKind.ONCE -> store.remove(taskId)
            PluginBackgroundKind.PERIODIC -> store.markRun(task, System.currentTimeMillis())
            else -> Unit
        }
        return Result.success()
    }

    companion object {
        const val KEY_PLUGIN_ID = "pluginId"
        const val KEY_TASK_ID = "taskId"
        const val KEY_KIND = "kind"
        private const val MAX_DELIVERY_ATTEMPTS = 3
    }
}
