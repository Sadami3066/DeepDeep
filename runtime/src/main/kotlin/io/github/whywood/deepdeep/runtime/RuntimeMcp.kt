package io.github.whywood.deepdeep.runtime

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class RuntimeMcp(
    private val mutex: Mutex,
    private val handles: ConcurrentHashMap<String, RuntimeHandle>,
) {
    private val mcpMutex = Mutex()

    suspend fun reload() = mcpMutex.withLock {
        withContext(Dispatchers.IO) {
            val targets = mutex.withLock { handles.values.toList() }
            for (handle in targets) {
                val loaded = runCatching { handle.engine.loadMcpServers() }
                if (loaded.isFailure) {
                    logError(TAG, "mcp reload config failed session=${handle.session.sessionId}", loaded.exceptionOrNull())
                    continue
                }
                runCatching { handle.session.reloadMcp(loaded.getOrThrow()) }
                    .onFailure { logError(TAG, "mcp reload failed session=${handle.session.sessionId}", it) }
                logState(handle, "reload")
            }
        }
    }

    suspend fun reconnect(server: String) = mcpMutex.withLock {
        withContext(Dispatchers.IO) {
            val targets = mutex.withLock { handles.values.toList() }
            for (handle in targets) {
                runCatching { handle.session.reconnectMcp(server) }
                    .onFailure { logError(TAG, "mcp reconnect failed session=${handle.session.sessionId} server=$server", it) }
                logState(handle, "reconnect")
            }
        }
    }

    fun logState(handle: RuntimeHandle, phase: String) {
        runCatching {
            val servers = handle.session.mcpServers().joinToString { status ->
                "${status.name}=${status.state}/${status.toolCount}/${status.lastError.orEmpty()}"
            }
            val deferred = handle.session.deferredMcpToolCount()
            val loaded = handle.session.activeToolNames.count { it.startsWith("mcp__") }
            logInfo(TAG, "mcp $phase session=${handle.session.sessionId} servers=[$servers] deferred=$deferred loaded=$loaded")
        }.onFailure { logError(TAG, "mcp $phase state unavailable", it) }
    }
}
