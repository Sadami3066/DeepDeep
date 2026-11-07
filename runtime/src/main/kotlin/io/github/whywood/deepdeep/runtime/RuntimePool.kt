package io.github.whywood.deepdeep.runtime

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class RuntimePool(
    private val mutex: Mutex,
    private val handles: ConcurrentHashMap<String, RuntimeHandle>,
    private val commands: SessionCommandPlane,
    private val catalog: SessionDirectory,
    private val visibleCwds: suspend () -> List<String>,
    private val engineFactory: suspend (File) -> SessionEngine,
    private val modelSettings: ModelSettings,
    private val keepHandleAlive: (String) -> Boolean,
    private val translator: RuntimeEventTranslator,
    private val mcp: RuntimeMcp,
    private val publishBusy: () -> Unit,
    private val publishActiveRun: (RuntimeHandle) -> Unit,
    private val clearActiveRun: (String) -> Unit,
    private val emitListChanged: suspend () -> Unit,
    private val onSessionClosed: (String, SessionCloseReason) -> Unit,
) {
    private val closingSessions = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private val closingFiles = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private var lastOpenedSessionId: String? = null

    suspend fun newSession(cwd: String): OpenedSession {
        val opened = mutex.withLock {
            val handle = attach(cwd, null)
            handle.touch()
            lastOpenedSessionId = handle.session.sessionId
            emitListChanged()
            OpenedSession(handle.session.sessionId, handle.session.sessionFile, cwd)
        }
        sweepIdleHandles()
        return opened
    }

    suspend fun openSession(filePath: String, cwd: String): OpenedSession {
        val opened = mutex.withLock {
            closingFiles[filePath]?.await()
            handles.values.firstOrNull { it.session.sessionFile == filePath }?.let { existing ->
                existing.touch()
                lastOpenedSessionId = existing.session.sessionId
                OpenedSession(existing.session.sessionId, filePath, existing.cwd)
            } ?: run {
                val handle = attach(cwd, filePath)
                handle.touch()
                lastOpenedSessionId = handle.session.sessionId
                OpenedSession(handle.session.sessionId, handle.session.sessionFile, cwd)
            }
        }
        sweepIdleHandles()
        return opened
    }

    suspend fun close(sessionId: String) {
        mutex.withLock { detach(sessionId, reason = SessionCloseReason.Closed) }
    }

    suspend fun detachForget(sessionId: String) {
        mutex.withLock { detach(sessionId, forget = true, reason = SessionCloseReason.Deleted) }
    }

    suspend fun reload(sessionId: String): String {
        val reopenedId = mutex.withLock {
            val handle = handles[sessionId] ?: error("Agent session is not ready")
            check(!handle.state.busy) { "Agent session is busy" }
            val file = handle.session.sessionFile ?: error("Agent session has no file")
            val cwd = handle.cwd
            val selected = lastOpenedSessionId == sessionId
            val lastActiveAt = handle.lastActiveAt
            detach(sessionId, reason = SessionCloseReason.Reload)
            val reopened = attach(cwd, file)
            reopened.lastActiveAt = lastActiveAt
            if (selected) lastOpenedSessionId = reopened.session.sessionId
            emitListChanged()
            reopened.session.sessionId
        }
        sweepIdleHandles()
        return reopenedId
    }

    suspend fun handleFor(sessionId: String): RuntimeHandle {
        mutex.withLock { handles[sessionId] }?.let { handle ->
            handle.touch()
            return handle
        }
        closingSessions[sessionId]?.await()
        val summary = HandleBudget.reconnectTarget(catalog.listAll(visibleCwds()), sessionId)
            ?: error("Agent session is not ready")
        val handle = mutex.withLock {
            closingSessions[sessionId]?.await()
            handles[sessionId] ?: attach(summary.cwd, summary.filePath)
        }
        sweepIdleHandles()
        return handle
    }

    suspend fun reconfigureSession(sessionId: String) {
        while (true) {
            val token = commands.claimReconfigure(sessionId)
            if (token == 0L) return
            var again = false
            var reattached = false
            try {
                mutex.withLock {
                    if (!commands.ownsReconfigure(sessionId, token)) return@withLock
                    val handle = handles[sessionId] ?: return@withLock
                    if (!isPersistedSession(handle)) return@withLock
                    val cwd = handle.cwd
                    val file = handle.session.sessionFile
                    val selected = lastOpenedSessionId == sessionId
                    val lastActiveAt = handle.lastActiveAt
                    detach(sessionId, keepReconfigure = true, reason = SessionCloseReason.Reconfigure)
                    val reopened = attach(cwd, file)
                    reopened.lastActiveAt = lastActiveAt
                    if (selected) lastOpenedSessionId = reopened.session.sessionId
                    reattached = true
                }
            } finally {
                again = commands.releaseReconfigure(sessionId, token)
            }
            if (reattached) sweepIdleHandles()
            if (!again) return
        }
    }

    suspend fun reconfigureAll() = withContext(Dispatchers.IO) {
        val sessionIds = mutex.withLock { handles.keys.toList() }
        for (sessionId in sessionIds) {
            runCatching { reconfigureSession(sessionId) }
                .onFailure { logError(TAG, "reconfigure failed session=$sessionId", it) }
        }
        emitListChanged()
    }

    internal suspend fun sweepIdleHandles() {
        val victims = mutex.withLock { evictionVictimsLocked() }
        for (sessionId in victims) {
            val gate = CompletableDeferred<Unit>()
            val handle = mutex.withLock {
                val current = handles[sessionId] ?: return@withLock null
                if (!evictableLocked(sessionId, current)) return@withLock null
                val removed = detachLocked(sessionId) ?: return@withLock null
                closingSessions[sessionId] = gate
                removed.session.sessionFile?.let { closingFiles[it] = gate }
                removed
            } ?: continue
            val file = handle.session.sessionFile
            try {
                closeHandle(handle, SessionCloseReason.Idle)
            } finally {
                closingSessions.remove(sessionId)
                if (file != null) closingFiles.remove(file)
                gate.complete(Unit)
                publishBusy()
                clearActiveRun(sessionId)
            }
        }
    }

    private suspend fun attach(cwd: String, file: String?): RuntimeHandle {
        val existing = handles.values.firstOrNull { it.cwd == cwd && it.session.sessionFile == file }
        if (existing != null && file != null) return existing
        val engine = engineFactory(File(cwd))
        val config = modelSettings.read()
        val session = engine.create(file, config)
        val handle = RuntimeHandle(
            engine = engine,
            session = session,
            cwd = cwd,
            state = commands.attach(session.sessionId),
        )
        handles[session.sessionId] = handle
        handle.unsubscribe = session.subscribe { event -> translator.onSessionEvent(handle, event) }
        mcp.logState(handle, "attach")
        return handle
    }

    private suspend fun detach(
        sessionId: String,
        forget: Boolean = false,
        keepReconfigure: Boolean = false,
        reason: SessionCloseReason = SessionCloseReason.Closed,
    ): RuntimeHandle? {
        val handle = detachLocked(sessionId, forget, keepReconfigure) ?: return null
        closeHandle(handle, reason)
        publishBusy()
        clearActiveRun(sessionId)
        return handle
    }

    private fun detachLocked(
        sessionId: String,
        forget: Boolean = false,
        keepReconfigure: Boolean = false,
    ): RuntimeHandle? {
        if (lastOpenedSessionId == sessionId) lastOpenedSessionId = null
        val handle = handles.remove(sessionId)
        commands.detach(sessionId, keepReconfigure)
        if (forget) commands.forget(sessionId)
        return handle
    }

    private suspend fun closeHandle(handle: RuntimeHandle, reason: SessionCloseReason) {
        handle.unsubscribe?.invoke()
        handle.unsubscribe = null
        val sessionId = handle.session.sessionId
        handle.session.close()
        runCatching { onSessionClosed(sessionId, reason) }
    }

    private fun isPersistedSession(handle: RuntimeHandle): Boolean {
        val path = handle.session.sessionFile ?: return false
        val file = File(path)
        return file.isFile && file.length() > 0L
    }

    private fun evictionVictimsLocked(): List<String> {
        val now = System.currentTimeMillis()
        val entries = handles.map { (sessionId, handle) -> budgetEntryLocked(sessionId, handle, now) }
        return HandleBudget.decideEvictions(entries, now)
    }

    private fun evictableLocked(sessionId: String, handle: RuntimeHandle): Boolean {
        val entry = budgetEntryLocked(sessionId, handle, System.currentTimeMillis())
        if (entry.busy || entry.reconfiguring || entry.keepAlive || entry.selected) return false
        return System.currentTimeMillis() - handle.lastActiveAt > HandleBudget.IDLE_TTL_MS
    }

    private fun budgetEntryLocked(sessionId: String, handle: RuntimeHandle, now: Long): HandleBudget.Entry {
        val snapshot = commands.snapshot(sessionId)
        return HandleBudget.Entry(
            sessionId = sessionId,
            lastActiveAt = handle.lastActiveAt,
            busy = snapshot?.busy == true || snapshot?.runId != null,
            reconfiguring = snapshot?.reconfiguring == true,
            keepAlive = keepAliveSafely(sessionId),
            selected = sessionId == lastOpenedSessionId,
        )
    }

    private fun keepAliveSafely(sessionId: String): Boolean =
        try {
            keepHandleAlive(sessionId)
        } catch (_: Exception) {
            true
        }
}
