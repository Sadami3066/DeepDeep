package io.github.whywood.deepdeep.plugin.host.extensions.device

import io.github.whywood.deepdeep.plugin.api.PluginBackgroundKind
import kotlinx.coroutines.delay

internal object PluginBackgroundDispatcher {
    @Volatile
    var dispatch: (suspend (pluginId: String, taskId: String, kind: PluginBackgroundKind) -> Unit)? = null

    fun install(handler: suspend (String, String, PluginBackgroundKind) -> Unit) {
        dispatch = handler
    }

    fun clear() {
        dispatch = null
    }

    fun installed(): Boolean = dispatch != null

    suspend fun deliver(
        pluginId: String,
        taskId: String,
        kind: PluginBackgroundKind,
        waitMillis: Long = DEFAULT_WAIT_MILLIS,
    ): Boolean {
        val plugin = sanitizedPluginId(pluginId) ?: return false
        val task = taskId.trim().takeIf { it.isNotEmpty() } ?: return false
        val deadline = System.currentTimeMillis() + waitMillis.coerceIn(0, MAX_WAIT_MILLIS)
        while (true) {
            val handler = dispatch
            if (handler != null) {
                return runCatching { handler(plugin, task, kind) }.isSuccess
            }
            if (System.currentTimeMillis() >= deadline) return false
            delay(WAIT_STEP_MILLIS)
        }
    }

    private const val DEFAULT_WAIT_MILLIS = 5_000L
    private const val MAX_WAIT_MILLIS = 15_000L
    private const val WAIT_STEP_MILLIS = 100L
}
