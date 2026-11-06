package io.github.whywood.deepdeep.runtime

import dev.pi.agent.AgentMessage
import dev.pi.ai.Model
import dev.pi.coding.session.QueueBehavior
import dev.pi.coding.session.SessionEvent
import dev.pi.mcp.config.McpServerConfig

data class SessionCompaction(val tokensBefore: Long, val estimatedTokensAfter: Long?)

data class SessionMcpServerStatus(
    val name: String,
    val state: String,
    val toolCount: Int,
    val lastError: String?,
)

interface SessionHandle {
    val sessionId: String
    val sessionFile: String?
    val isBusy: Boolean
    val isStreaming: Boolean
    val messages: List<AgentMessage>
    val activeToolNames: List<String>
    val model: Model
    val thinkingLevel: String

    suspend fun prompt(text: String, images: List<ImagePart>? = null, behavior: QueueBehavior? = null)
    fun abort()
    suspend fun close()
    fun subscribe(listener: suspend (SessionEvent) -> Unit): () -> Unit
    fun clearQueues()

    fun removeQueuedInput(text: String, behavior: QueueBehavior): Boolean
    fun canRetryLastAssistant(): Boolean
    suspend fun retryLastAssistant()
    fun lastAssistantText(): String?
    suspend fun setModel(model: Model, thinkingLevel: String?)
    suspend fun setThinkingLevel(level: String)
    suspend fun compact(): SessionCompaction?
    fun history(): List<AssistantMessage>
    fun usage(): SessionUsage?
    fun sessionName(): String?
    suspend fun appendTitle(title: String)

    suspend fun appendCustomMessage(customType: String, payloadJson: String, text: String, display: Boolean = true)
    suspend fun reloadMcp(servers: Map<String, McpServerConfig>)
    suspend fun reconnectMcp(server: String)
    fun mcpServers(): List<SessionMcpServerStatus>
    fun deferredMcpToolCount(): Int
}
