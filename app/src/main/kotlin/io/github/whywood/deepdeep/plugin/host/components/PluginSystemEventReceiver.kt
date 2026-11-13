package io.github.whywood.deepdeep.plugin.host.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.whywood.deepdeep.DeepDeepApplication
import io.github.whywood.deepdeep.plugin.api.PluginBackgroundKind
import io.github.whywood.deepdeep.plugin.host.external.ExternalPluginRecordStore
import io.github.whywood.deepdeep.plugin.host.extensions.device.PluginBackgroundDispatcher
import io.github.whywood.deepdeep.plugin.host.extensions.device.PluginBackgroundScheduler
import io.github.whywood.deepdeep.plugin.host.extensions.device.PluginBackgroundStore
import io.github.whywood.deepdeep.plugin.host.extensions.device.backgroundStoreFile
import io.github.whywood.deepdeep.plugin.host.extensions.device.isTaskDue
import io.github.whywood.deepdeep.plugin.host.extensions.device.nextRunAtMs
import io.github.whywood.deepdeep.plugin.host.extensions.device.rescheduleAfterRun
import io.github.whywood.deepdeep.plugin.host.extensions.device.sanitizedPluginId
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class PluginSystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in SUPPORTED_ACTIONS) return
        val appContext = context.applicationContext ?: context
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(HANDLE_TIMEOUT_MS) {
                    when (action) {
                        ACTION_EXACT_ALARM -> handleExactAlarm(appContext, intent)
                        else -> handleSystemEvent(appContext, boot = action == Intent.ACTION_BOOT_COMPLETED)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Log.w(TAG, "plugin system event failed: $action", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleSystemEvent(context: Context, boot: Boolean) {
        val pluginsRoot = File(context.filesDir, "plugins")
        val enabled = runCatching {
            ExternalPluginRecordStore(File(pluginsRoot, "plugins.json")).load()
        }.getOrDefault(emptyMap()).mapValues { entry -> entry.value.enabled }
        if (boot) ensureProcess(context)
        val directories = pluginsRoot.listFiles()?.filter { it.isDirectory }.orEmpty()
        for (directory in directories) {
            val pluginId = sanitizedPluginId(directory.name) ?: continue
            if (enabled[pluginId] == false) continue
            if (!backgroundStoreFile(directory).isFile) continue
            val store = PluginBackgroundStore(backgroundStoreFile(directory))
            val scheduler = PluginBackgroundScheduler(context, pluginId)
            val tasks = store.load()
            for (task in tasks) {
                when (task.kind) {
                    PluginBackgroundKind.BOOT -> if (boot) {
                        deliver(pluginId, task.id, PluginBackgroundKind.BOOT)
                        store.markRun(task, System.currentTimeMillis())
                    }

                    PluginBackgroundKind.PERIODIC -> {
                        if (isTaskDue(task, System.currentTimeMillis())) {
                            deliver(pluginId, task.id, PluginBackgroundKind.PERIODIC)
                            store.markRun(task, System.currentTimeMillis())
                        }
                        store.record(task.id)?.let(scheduler::enqueue)
                    }

                    PluginBackgroundKind.ONCE, PluginBackgroundKind.EXACT -> {
                        val next = nextRunAtMs(task)
                        if (next != null && next <= System.currentTimeMillis()) {
                            deliver(pluginId, task.id, task.kind)
                            val updated = rescheduleAfterRun(task, System.currentTimeMillis())
                            if (updated != null) {
                                store.upsert(updated)
                                scheduler.enqueue(updated)
                            } else {
                                store.remove(task.id)
                            }
                        } else {
                            scheduler.enqueue(task)
                        }
                    }
                }
            }
        }
    }

    private suspend fun handleExactAlarm(context: Context, intent: Intent) {
        val pluginId = intent.getStringExtra(EXTRA_PLUGIN_ID)?.let(::sanitizedPluginId) ?: return
        val taskId = intent.getStringExtra(EXTRA_TASK_ID)?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val directory = File(File(context.filesDir, "plugins"), pluginId)
        val store = PluginBackgroundStore(backgroundStoreFile(directory))
        val task = store.record(taskId) ?: return
        if (task.kind != PluginBackgroundKind.EXACT) return
        deliver(pluginId, taskId, PluginBackgroundKind.EXACT)
        val firedAt = System.currentTimeMillis()
        val updated = rescheduleAfterRun(task, firedAt)
        if (updated != null) {
            store.upsert(updated)
            PluginBackgroundScheduler(context, pluginId).enqueue(updated)
        } else {
            store.remove(taskId)
        }
    }

    private suspend fun deliver(pluginId: String, taskId: String, kind: PluginBackgroundKind) {
        PluginBackgroundDispatcher.deliver(pluginId, taskId, kind, DELIVERY_WAIT_MS)
    }

    private suspend fun ensureProcess(context: Context) {
        if (PluginBackgroundDispatcher.installed()) return
        val application = context.applicationContext as? DeepDeepApplication ?: return
        runCatching { application.graph.processRuntime.startAsync() }
    }

    companion object {
        const val ACTION_EXACT_ALARM = "io.github.whywood.deepdeep.plugin.action.EXACT_ALARM"
        const val EXTRA_PLUGIN_ID = "pluginId"
        const val EXTRA_TASK_ID = "taskId"
        private const val TAG = "PluginSystemEvent"
        private const val HANDLE_TIMEOUT_MS = 9_000L
        private const val DELIVERY_WAIT_MS = 1_000L
        private val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            ACTION_EXACT_ALARM,
        )
    }
}
