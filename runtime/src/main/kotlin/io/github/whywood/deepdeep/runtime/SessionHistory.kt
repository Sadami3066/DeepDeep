package io.github.whywood.deepdeep.runtime

import dev.pi.coding.paths.AgentPaths
import dev.pi.coding.session.SessionManager
import java.io.File
import okio.FileSystem
import okio.Path.Companion.toPath

object SessionHistory {
    suspend fun load(filePath: String): List<AssistantMessage> {
        if (filePath.isBlank()) return emptyList()
        return runCatching {
            val manager = SessionManager.open(filePath.toPath(), FileSystem.SYSTEM)
            HistoryMapper.mapEntries(manager.getBranch())
        }.getOrDefault(emptyList())
    }

    fun sessionsDirectory(cwd: String, agentDirectory: File): File? {
        if (cwd.isBlank()) return null
        if (!File(cwd).isAbsolute) return null
        val root = runCatching { agentDirectory.canonicalFile }.getOrNull() ?: return null
        val sessions = runCatching {
            AgentPaths(cwd.toPath(), root.absolutePath.toPath()).sessionsForCwd().toFile()
        }.getOrNull() ?: return null
        val canonical = runCatching { sessions.canonicalFile }.getOrNull() ?: return null
        if (!canonical.toPath().startsWith(root.toPath())) return null
        return canonical
    }
}
