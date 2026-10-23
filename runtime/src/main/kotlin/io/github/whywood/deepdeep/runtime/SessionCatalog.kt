package io.github.whywood.deepdeep.runtime

import dev.pi.coding.paths.AgentPaths
import dev.pi.coding.session.SessionInfo
import dev.pi.coding.session.SessionManager
import java.io.File
import java.time.OffsetDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath

class SessionCatalog(
    private val agentDir: File,
    private val metaStore: SessionMetaStore,
) {
    private val fs = FileSystem.SYSTEM

    suspend fun list(cwd: String): List<ConversationSummary> = withContext(Dispatchers.IO) {
        val metas = metaStore.read()
        val paths = AgentPaths(cwd.toPath(), agentDir.absolutePath.toPath())
        SessionManager.list(paths, fs)
            .map { info -> info.toSummary(metas[info.sessionId]) }
            .sortedWith(compareByDescending<ConversationSummary> { it.pinned }.thenByDescending { it.updatedAt })
    }

    suspend fun listAll(cwds: List<String>): List<ConversationSummary> = withContext(Dispatchers.IO) {
        val metas = metaStore.read()
        val seen = mutableSetOf<String>()
        val result = mutableListOf<ConversationSummary>()
        for (cwd in cwds) {
            val paths = AgentPaths(cwd.toPath(), agentDir.absolutePath.toPath())
            for (info in SessionManager.list(paths, fs)) {
                if (!seen.add(info.sessionId)) continue
                result.add(info.toSummary(metas[info.sessionId]))
            }
        }
        result.sortedWith(compareByDescending<ConversationSummary> { it.pinned }.thenByDescending { it.updatedAt })
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

    private fun SessionInfo.toSummary(meta: SessionMeta?): ConversationSummary = ConversationSummary(
        id = sessionId,
        title = meta?.titleOverride?.takeIf { it.isNotBlank() } ?: name?.takeIf { it.isNotBlank() } ?: "新对话",
        updatedAt = parseTimestamp(timestamp),
        filePath = sessionFile.toString(),
        cwd = cwd,
        pinned = meta?.pinned ?: false,
        archived = meta?.archived ?: false,
    )

    private fun parseTimestamp(value: String): Long = runCatching {
        OffsetDateTime.parse(value).toInstant().toEpochMilli()
    }.getOrDefault(0L)
}
