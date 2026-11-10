package io.github.whywood.deepdeep.runtime.host

import kotlinx.coroutines.flow.StateFlow

enum class HostAccess {
    LOCAL,
    ADB,
    ROOT,
}

enum class HostAccessFallback {
    ADB_UNAVAILABLE,
    ROOT_UNAVAILABLE,
}

data class HostAccessState(
    val selected: HostAccess = HostAccess.LOCAL,
    val effective: HostAccess = HostAccess.LOCAL,
    val adbReady: Boolean = false,
    val rootReady: Boolean = false,
    val fallback: HostAccessFallback? = null,
)

fun resolveHostAccess(
    selected: HostAccess,
    adbReady: Boolean,
    rootReady: Boolean,
): HostAccessState = when (selected) {
    HostAccess.LOCAL -> HostAccessState(selected, HostAccess.LOCAL, adbReady, rootReady)
    HostAccess.ADB -> if (adbReady) {
        HostAccessState(selected, HostAccess.ADB, adbReady = true, rootReady = rootReady)
    } else {
        HostAccessState(selected, HostAccess.LOCAL, fallback = HostAccessFallback.ADB_UNAVAILABLE)
    }
    HostAccess.ROOT -> when {
        rootReady -> HostAccessState(selected, HostAccess.ROOT, adbReady = adbReady, rootReady = true)
        adbReady -> HostAccessState(selected, HostAccess.ADB, adbReady = true, fallback = HostAccessFallback.ROOT_UNAVAILABLE)
        else -> HostAccessState(selected, HostAccess.LOCAL, fallback = HostAccessFallback.ROOT_UNAVAILABLE)
    }
}

interface HostAccessController {
    val state: StateFlow<HostAccessState>

    suspend fun select(access: HostAccess)

    suspend fun refresh()

    suspend fun connectAdb()

    fun startAdbPairing()

    fun openWirelessDebugging(context: android.content.Context)
}
