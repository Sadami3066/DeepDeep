package io.github.whywood.deepdeep.vscreen

import kotlinx.coroutines.delay

internal class IdlePolicy(private val timeoutMs: Long = IDLE_TIMEOUT_MS) {
    private var lastActivityAtMs = NO_ACTIVITY

    @Synchronized
    fun onConnected(nowMs: Long) {
        lastActivityAtMs = nowMs
    }

    @Synchronized
    fun onActivity(nowMs: Long) {
        lastActivityAtMs = nowMs
    }

    @Synchronized
    fun isIdle(nowMs: Long): Boolean =
        lastActivityAtMs != NO_ACTIVITY && nowMs - lastActivityAtMs >= timeoutMs

    @Synchronized
    fun reset() {
        lastActivityAtMs = NO_ACTIVITY
    }

    companion object {
        const val IDLE_TIMEOUT_MS = 5 * 60_000L
        const val CHECK_INTERVAL_MS = 30_000L
        private const val NO_ACTIVITY = -1L
    }
}

internal suspend fun watchIdle(
    policy: IdlePolicy,
    nowMs: () -> Long,
    isOnline: () -> Boolean,
    onIdle: suspend () -> Unit,
    checkIntervalMs: Long = IdlePolicy.CHECK_INTERVAL_MS,
) {
    while (true) {
        delay(checkIntervalMs)
        if (!isOnline()) return
        if (!policy.isIdle(nowMs())) continue
        onIdle()
        return
    }
}
