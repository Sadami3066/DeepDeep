package io.github.whywood.deepdeep.vscreen

import kotlinx.coroutines.delay

internal enum class TransportFailureKind(val code: String) {
    EOF("eof"),
    HEARTBEAT("heartbeat"),
    FRAME_INVALID("frame-invalid"),
    IO("io"),
}

internal enum class VirtualScreenFailureCategory {
    TRANSPORT,
    BACKEND,
    START,
}

internal data class VirtualScreenFailureInfo(
    val category: VirtualScreenFailureCategory,
    val message: String,
    val recoverable: Boolean,
    val reason: String? = null,
)

internal class SessionLifecycle {
    @Volatile
    var desiredOnline: Boolean = false
        private set

    fun onStarted() {
        desiredOnline = true
    }

    fun onStopped() {
        desiredOnline = false
    }

    fun shouldRecover(): Boolean = desiredOnline
}

internal enum class StartPlan {
    START,
    REUSE,
    REBUILD,
    BUSY,
}

internal fun planStart(online: Boolean, starting: Boolean, healthOk: Boolean?): StartPlan = when {
    starting -> StartPlan.BUSY
    online && healthOk == true -> StartPlan.REUSE
    online -> StartPlan.REBUILD
    else -> StartPlan.START
}

internal enum class RecoveryOutcome {
    RECOVERED,
    RETRY,
    ABORT,
}

internal class ReconnectScheduler(private val delaysMs: List<Long> = DEFAULT_DELAYS_MS) {
    val maxAttempts: Int
        get() = delaysMs.size

    suspend fun run(attempt: suspend (Int) -> RecoveryOutcome): Boolean {
        for ((index, delayMs) in delaysMs.withIndex()) {
            delay(delayMs)
            when (attempt(index + 1)) {
                RecoveryOutcome.RECOVERED -> return true
                RecoveryOutcome.ABORT -> return false
                RecoveryOutcome.RETRY -> Unit
            }
        }
        return false
    }

    companion object {
        val DEFAULT_DELAYS_MS = listOf(1_000L, 3_000L, 10_000L)
    }
}
