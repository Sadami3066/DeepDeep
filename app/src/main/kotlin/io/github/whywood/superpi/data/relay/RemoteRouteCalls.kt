package io.github.whywood.superpi.data.relay

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.JsonObject

data class RemoteEvent(
    val routeId: String,
    val seq: Long,
    val event: JsonObject,
)

data class RemoteCallError(
    val code: String,
    val message: String,
)

internal sealed interface RemoteCallOutcome {
    data class Ok(val result: JsonObject?) : RemoteCallOutcome

    data class Failed(val error: RemoteCallError) : RemoteCallOutcome
}

internal object RemoteCallCodes {
    const val NOT_CONNECTED = "not_connected"
    const val DISCONNECTED = "disconnected"
    const val TIMEOUT = "timeout"
}

internal class RemoteCallTracker {
    private val pending = ConcurrentHashMap<String, CompletableDeferred<RemoteCallOutcome>>()

    fun open(routeId: String): CompletableDeferred<RemoteCallOutcome> {
        val deferred = CompletableDeferred<RemoteCallOutcome>()
        pending[routeId] = deferred
        return deferred
    }

    fun settle(routeId: String, outcome: RemoteCallOutcome): Boolean {
        val deferred = pending.remove(routeId) ?: return false
        return deferred.complete(outcome)
    }

    fun failAll(error: RemoteCallError) {
        pending.keys.toList().forEach { routeId ->
            settle(routeId, RemoteCallOutcome.Failed(error))
        }
    }

    fun pendingCount(): Int = pending.size
}
