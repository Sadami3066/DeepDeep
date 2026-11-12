package io.github.whywood.deepdeep.plugin.host.events

import io.github.whywood.deepdeep.plugin.api.MessageDeltaEvent
import io.github.whywood.deepdeep.plugin.api.PluginEvent
import io.github.whywood.deepdeep.plugin.api.ToolExecutionUpdateEvent
import io.github.whywood.deepdeep.plugin.api.ToolPhase
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

internal class SessionEventPump(
    private val scope: CoroutineScope,
    private val dispatch: suspend (sessionId: String, event: PluginEvent) -> Unit,
) {
    private val sessions = ConcurrentHashMap<String, SessionPump>()

    private val closedSessions: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun updateSnapshot(sessionId: String, snapshot: HookSnapshot) {
        if (sessionId in closedSessions) return
        val state = state(sessionId)
        state.hookVersion = snapshot.version
        state.hooksEmpty = snapshot.entries.isEmpty()
    }

    fun submit(sessionId: String, event: PluginEvent) {
        if (sessionId in closedSessions) return
        val state = state(sessionId)
        if (state.hooksEmpty) return
        if (event.isDeltaEvent()) {
            state.deltas.trySend(event)
        } else {
            state.structural.trySend(event)
        }
        state.wakeup.trySend(Unit)
        state.ensureStarted()
    }

    fun close(sessionId: String) {
        closedSessions += sessionId
        sessions.remove(sessionId)?.close()
    }

    suspend fun closeAndDrain(sessionId: String) {
        closedSessions += sessionId
        val state = sessions.remove(sessionId) ?: return
        state.close()
        state.job()?.join()
    }

    private fun state(sessionId: String): SessionPump = sessions.computeIfAbsent(sessionId) { id ->
        SessionPump(id)
    }

    private inner class SessionPump(private val sessionId: String) {
        val structural: Channel<PluginEvent> =
            Channel(capacity = STRUCTURAL_CAPACITY, onBufferOverflow = BufferOverflow.DROP_OLDEST)

        val deltas: Channel<PluginEvent> =
            Channel(capacity = DELTA_CAPACITY, onBufferOverflow = BufferOverflow.DROP_OLDEST)

        val wakeup: Channel<Unit> = Channel(Channel.CONFLATED)

        @Volatile
        var hookVersion: Long = 0L

        @Volatile
        var hooksEmpty: Boolean = false

        @Volatile
        private var closed: Boolean = false

        private var job: Job? = null

        fun job(): Job? = job

        fun ensureStarted() {
            synchronized(this) {
                if (job != null || closed) return
                job = scope.launch { drain() }
            }
        }

        fun close() {
            closed = true
            wakeup.trySend(Unit)
        }

        private suspend fun drain() {
            while (true) {
                drainPending()
                if (closed) {
                    drainPending()
                    break
                }
                wakeup.receive()
            }
            structural.close()
            deltas.close()
        }

        private suspend fun drainPending() {
            while (true) {
                val event = structural.tryReceive().getOrNull()
                    ?: deltas.tryReceive().getOrNull()
                    ?: return
                try {
                    dispatch(sessionId, event)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                }
            }
        }
    }

    private companion object {
        const val STRUCTURAL_CAPACITY = 128

        const val DELTA_CAPACITY = 512
    }
}

internal fun PluginEvent.isDeltaEvent(): Boolean = when (this) {
    is MessageDeltaEvent -> true
    is ToolExecutionUpdateEvent -> phase == ToolPhase.PARTIAL
    else -> false
}
