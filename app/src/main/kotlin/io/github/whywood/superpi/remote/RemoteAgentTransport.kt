package io.github.whywood.superpi.remote

import io.github.whywood.superpi.data.relay.RemoteEvent
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject

interface RemoteAgentTransport {
    val remoteEvents: SharedFlow<RemoteEvent>

    val onlineDevices: StateFlow<Set<String>>

    suspend fun call(
        to: String,
        method: String,
        params: JsonObject,
        timeoutMillis: Long = CALL_TIMEOUT_MILLIS,
        onRouteId: (String) -> Unit = {},
    ): JsonObject?

    companion object {
        const val CALL_TIMEOUT_MILLIS = 15_000L
    }
}
