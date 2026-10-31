package io.github.whywood.deepdeep.vscreen

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

internal class HeartbeatPolicy(
    private val intervalMs: Long = INTERVAL_MS,
    private val timeoutMs: Long = TIMEOUT_MS,
) {
    private var lastPongAtMs = 0L
    private var nextPingAtMs = 0L

    @Synchronized
    fun onConnected(nowMs: Long) {
        lastPongAtMs = nowMs
        nextPingAtMs = nowMs + intervalMs
    }

    @Synchronized
    fun onPong(nowMs: Long) {
        lastPongAtMs = nowMs
    }

    @Synchronized
    fun isTransportLost(nowMs: Long): Boolean = nowMs - lastPongAtMs >= timeoutMs

    @Synchronized
    fun isPingDue(nowMs: Long): Boolean = nowMs >= nextPingAtMs

    @Synchronized
    fun onPingSent(nowMs: Long) {
        nextPingAtMs = nowMs + intervalMs
    }

    @Synchronized
    fun reset() {
        lastPongAtMs = 0L
        nextPingAtMs = 0L
    }

    companion object {
        const val INTERVAL_MS = 5_000L
        const val TIMEOUT_MS = 15_000L
        const val CHECK_INTERVAL_MS = 1_000L
        const val HEALTH_PING_TIMEOUT_MS = 2_000L
    }
}

internal class PongGate {
    private var waiter: CompletableDeferred<Unit>? = null

    @Synchronized
    fun arm() {
        waiter = CompletableDeferred()
    }

    @Synchronized
    fun onPong(): Boolean {
        val current = waiter ?: return false
        waiter = null
        current.complete(Unit)
        return true
    }

    @Synchronized
    fun reset() {
        waiter = null
    }

    suspend fun await(timeoutMs: Long): Boolean {
        val current = synchronized(this) { waiter } ?: return false
        val received = withTimeoutOrNull(timeoutMs) {
            current.await()
            true
        } ?: false
        if (!received) synchronized(this) { if (waiter === current) waiter = null }
        return received
    }
}
