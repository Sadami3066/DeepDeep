package io.github.whywood.deepdeep.plugin.host.extensions.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.whywood.deepdeep.plugin.api.ComponentService
import io.github.whywood.deepdeep.plugin.api.PluginActivityOptions
import io.github.whywood.deepdeep.plugin.api.PluginReceiverHandle
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

internal class ComponentsPluginService(private val deps: DeviceServiceDeps) : ComponentService {
    private val handles = ConcurrentHashMap<String, PluginReceiverHandleImpl>()

    override fun startActivity(options: PluginActivityOptions): Boolean = runCatching {
        deps.launchPluginActivity(deps.pluginId, options.pageId, options.arguments, options.newTask)
    }.getOrDefault(false)

    override fun registerReceiver(filter: IntentFilter): PluginReceiverHandle {
        val handle = PluginReceiverHandleImpl(deps.appContext, deps.scope, filter, handles::remove)
        handles[handle.id] = handle
        handle.register()
        return handle
    }
}

private class PluginReceiverHandleImpl(
    private val context: Context,
    private val scope: CoroutineScope,
    private val filter: IntentFilter,
    private val onClosed: (String) -> Unit,
) : BroadcastReceiver(), PluginReceiverHandle {
    override val id: String = UUID.randomUUID().toString()

    private val events = MutableSharedFlow<Intent>(
        extraBufferCapacity = EVENT_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val closed = AtomicBoolean(false)
    private var registered = false
    private var scopeCleanup: kotlinx.coroutines.DisposableHandle? = null

    fun register() {
        if (closed.get()) return
        val result = runCatching {
            ContextCompat.registerReceiver(context, this, filter, ContextCompat.RECEIVER_EXPORTED)
        }
        registered = result.isSuccess
        if (!registered) {
            Log.w(TAG, "plugin receiver registration failed", result.exceptionOrNull())
        }
        scopeCleanup = scope.coroutineContext[Job]?.invokeOnCompletion { close() }
    }

    override fun onReceive(receiverContext: Context?, intent: Intent?) {
        if (intent == null) return
        if (closed.get()) return
        events.tryEmit(intent)
    }

    override fun events(): Flow<Intent> = events

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        scopeCleanup?.dispose()
        scopeCleanup = null
        if (registered) {
            registered = false
            runCatching { context.unregisterReceiver(this) }
        }
        onClosed(id)
    }

    private companion object {
        const val EVENT_BUFFER = 64
        const val TAG = "PluginComponents"
    }
}
