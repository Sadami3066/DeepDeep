package io.github.whywood.deepdeep.screen

import java.util.WeakHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val screenLocks = WeakHashMap<ScreenController, Mutex>()

internal suspend fun <T> ScreenController.withScreenExecution(block: suspend () -> T): T {
    val lock = synchronized(screenLocks) { screenLocks.getOrPut(this) { Mutex() } }
    return lock.withLock { block() }
}
