package io.github.whywood.deepdeep.plugin.host

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

internal class PluginScope(
    parentContext: CoroutineContext = EmptyCoroutineContext,
    name: String? = null,
    private val onError: (Throwable) -> Unit = {},
) {
    private val lock = Any()
    private val closeActions = mutableListOf<() -> Unit>()
    private val job = SupervisorJob(parentContext[Job])
    private var closed = false

    val scope: CoroutineScope = CoroutineScope(parentContext + job + CoroutineName(name ?: DEFAULT_NAME))

    fun isClosed(): Boolean = synchronized(lock) { closed }

    fun onClose(action: () -> Unit): Boolean = synchronized(lock) {
        if (closed) {
            false
        } else {
            closeActions += action
            true
        }
    }

    fun close() {
        val pending = synchronized(lock) {
            if (closed) return
            closed = true
            closeActions.reversed().also { closeActions.clear() }
        }
        pending.forEach { action -> runCatching(action).onFailure(onError) }
        job.cancel()
    }

    private companion object {
        const val DEFAULT_NAME = "plugin"
    }
}
