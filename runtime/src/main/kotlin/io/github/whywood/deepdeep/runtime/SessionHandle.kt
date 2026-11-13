package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.sdk.agent.AgentMessage
import io.github.whywood.deepdeep.sdk.ai.Model
import io.github.whywood.deepdeep.sdk.ai.Provider
import io.github.whywood.deepdeep.sdk.coding.session.QueueBehavior
import io.github.whywood.deepdeep.sdk.coding.session.SessionEvent
import io.github.whywood.deepdeep.sdk.mcp.config.McpServerConfig

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
    fun registerProvider(provider: Provider)
    suspend fun compact(): SessionCompaction?
    fun history(): List<AssistantMessage>
    fun usage(): SessionUsage?
    fun sessionName(): String?
    suspend fun appendTitle(title: String)

    suspend fun appendCustomMessage(customType: String, payloadJson: String, text: String, display: Boolean = true)
    suspend fun appendCustomEntry(customType: String, dataJson: String)
    suspend fun capabilitySnapshot(): CapabilitySnapshot
    fun capabilityBaseline(): CapabilitySnapshot?
    suspend fun reloadMcp(servers: Map<String, McpServerConfig>)
    suspend fun reconnectMcp(server: String)
    fun mcpServers(): List<SessionMcpServerStatus>
    fun deferredMcpToolCount(): Int
}
