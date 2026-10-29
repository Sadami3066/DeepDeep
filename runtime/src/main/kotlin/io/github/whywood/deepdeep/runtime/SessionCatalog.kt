package io.github.whywood.deepdeep.runtime

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath

class SessionCatalog(
    private val agentDir: File,
    private val metaStore: SessionMetaStore,
) {
    private val fs = FileSystem.SYSTEM
    private val index = SessionIndex(agentDir)

    suspend fun list(cwd: String): List<ConversationSummary> = withContext(Dispatchers.IO) {
        val metas = metaStore.read()
        index.list(cwd)
            .map { session -> session.toSummary(metas[session.sessionId]) }
            .sortedWith(SUMMARY_ORDER)
    }

    suspend fun listAll(cwds: List<String>): List<ConversationSummary> = withContext(Dispatchers.IO) {
        val metas = metaStore.read()
        index.listAll(cwds)
            .map { session -> session.toSummary(metas[session.sessionId]) }
            .sortedWith(SUMMARY_ORDER)
    }

    suspend fun find(cwds: List<String>, sessionId: String): ConversationSummary? =
        listAll(cwds).firstOrNull { it.id == sessionId }

    suspend fun delete(filePath: String) = withContext(Dispatchers.IO) {
        runCatching { fs.delete(filePath.toPath()) }
        Unit
    }

    suspend fun rename(id: String, title: String) = metaStore.update(id) {
        it.copy(titleOverride = title.trim().takeIf { value -> value.isNotEmpty() })
    }

    suspend fun setPinned(id: String, pinned: Boolean) = metaStore.update(id) { it.copy(pinned = pinned) }

    suspend fun setArchived(id: String, archived: Boolean) = metaStore.update(id) { it.copy(archived = archived) }

    suspend fun removeMeta(id: String) = metaStore.remove(id)

    private fun IndexedSession.toSummary(meta: SessionMeta?): ConversationSummary = ConversationSummary(
        id = sessionId,
        title = meta?.titleOverride?.takeIf { it.isNotBlank() } ?: name?.takeIf { it.isNotBlank() } ?: "新对话",
        updatedAt = updatedAt,
        filePath = filePath,
        cwd = cwd,
        pinned = meta?.pinned ?: false,
        archived = meta?.archived ?: false,
    )

    private companion object {
        val SUMMARY_ORDER = compareByDescending<ConversationSummary> { it.pinned }
            .thenByDescending { it.updatedAt }
    }
}
