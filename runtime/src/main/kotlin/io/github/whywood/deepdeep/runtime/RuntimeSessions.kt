package io.github.whywood.deepdeep.runtime

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class RuntimeSessions(
    private val mutex: Mutex,
    private val handles: ConcurrentHashMap<String, RuntimeHandle>,
    private val catalog: SessionDirectory,
    private val visibleCwds: suspend () -> List<String>,
    private val workspaceFor: suspend (String) -> RuntimeWorkspace,
    private val emitListChanged: suspend () -> Unit,
    private val emitSessionRemoved: suspend (String) -> Unit,
    private val detachForget: suspend (String) -> Unit,
    private val sessionCleaner: ((String) -> Unit)?,
    private val sessionArchived: ((String) -> Unit)?,
) {
    suspend fun all(): List<ConversationSummary> =
        catalog.listAll(visibleCwds()).filterNot { it.archived }

    suspend fun workspaceCount(workspaceId: String): Int =
        catalog.list(workspaceFor(workspaceId).cwd).size

    suspend fun title(sessionId: String): String? = mutex.withLock {
        handles[sessionId]?.session?.sessionName()
            ?: catalog.find(visibleCwds(), sessionId)?.title
    }

    suspend fun list(workspaceId: String): List<ConversationSummary> =
        catalog.list(workspaceFor(workspaceId).cwd).filterNot { it.archived }

    suspend fun archived(): List<ConversationSummary> =
        catalog.listAll(visibleCwds()).filter { it.archived }

    suspend fun rename(id: String, title: String) {
        catalog.rename(id, title)
        emitListChanged()
    }

    suspend fun setPinned(id: String, pinned: Boolean) {
        catalog.setPinned(id, pinned)
        emitListChanged()
    }

    suspend fun setArchived(id: String, archived: Boolean) {
        catalog.setArchived(id, archived)
        if (archived) sessionArchived?.invoke(id)
        emitSessionRemoved(id)
        emitListChanged()
    }

    suspend fun delete(id: String) {
        val summary = catalog.find(visibleCwds(), id)
        detachForget(id)
        sessionCleaner?.invoke(id)
        if (summary != null) {
            catalog.delete(summary.filePath)
            catalog.removeMeta(id)
        }
        emitSessionRemoved(id)
        emitListChanged()
    }
}
