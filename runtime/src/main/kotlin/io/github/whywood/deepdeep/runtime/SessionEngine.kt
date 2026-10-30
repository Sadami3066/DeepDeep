package io.github.whywood.deepdeep.runtime

import dev.pi.mcp.config.McpServerConfig

interface SessionEngine {
    suspend fun create(file: String?, config: ModelConfig): SessionHandle
    suspend fun loadMcpServers(): Map<String, McpServerConfig>
    fun titleFor(text: String): String
}

interface SessionDirectory {
    suspend fun list(cwd: String): List<ConversationSummary>
    suspend fun listAll(cwds: List<String>): List<ConversationSummary>
    suspend fun find(cwds: List<String>, sessionId: String): ConversationSummary?
    suspend fun delete(filePath: String)
    suspend fun rename(id: String, title: String)
    suspend fun setPinned(id: String, pinned: Boolean)
    suspend fun setArchived(id: String, archived: Boolean)
    suspend fun removeMeta(id: String)
}

interface WorkspaceDirectory {
    suspend fun read(): List<RuntimeWorkspace>
    suspend fun add(workspace: RuntimeWorkspace)
    suspend fun remove(id: String)
}
