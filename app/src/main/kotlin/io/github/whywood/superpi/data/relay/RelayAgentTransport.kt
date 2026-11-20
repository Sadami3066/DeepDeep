package io.github.whywood.superpi.data.relay

import io.github.whywood.superpi.remote.RemoteAgentTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.JsonObject

class RelayAgentTransport(
    private val live: RelayLive,
    scope: CoroutineScope,
) : RemoteAgentTransport {
    override val remoteEvents: SharedFlow<RemoteEvent> = live.remoteEvents

    override val onlineDevices: StateFlow<Set<String>> = live.remoteBots
        .map { bots -> bots.filter { it.online }.map { it.deviceId }.toSet() }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    override fun lastCallError(): RemoteCallError? = live.lastCallError.value

    override suspend fun call(
        to: String,
        method: String,
        params: JsonObject,
        timeoutMillis: Long,
        onRouteId: (String) -> Unit,
    ): JsonObject? = live.call(to, method, params, timeoutMillis, onRouteId)
}
