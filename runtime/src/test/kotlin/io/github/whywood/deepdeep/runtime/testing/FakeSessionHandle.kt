package io.github.whywood.deepdeep.runtime.testing

import dev.pi.agent.AgentMessage
import dev.pi.ai.Model
import dev.pi.ai.ModelCost
import dev.pi.coding.session.QueueBehavior
import dev.pi.coding.session.SessionEvent
import dev.pi.mcp.config.McpServerConfig
import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.ImagePart
import io.github.whywood.deepdeep.runtime.SessionCompaction
import io.github.whywood.deepdeep.runtime.SessionHandle
import io.github.whywood.deepdeep.runtime.SessionUsage
import io.github.whywood.deepdeep.runtime.SessionMcpServerStatus
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred

data class PromptRecord(
    val text: String,
    val images: List<ImagePart>?,
    val behavior: QueueBehavior?,
)

val fakeModel: Model = Model(
    id = "fake-model",
    name = "Fake Model",
    api = "fake",
    provider = "fake",
    baseUrl = "http://127.0.0.1",
    input = emptyList(),
    cost = ModelCost(input = 0.0, output = 0.0, cacheRead = 0.0, cacheWrite = 0.0),
    reasoning = false,
    contextWindow = 128_000L,
    maxTokens = 4_096L,
)

class FakeSessionHandle(
    override val sessionId: String,
    override val sessionFile: String?,
) : SessionHandle {
    @Volatile
    var busy: Boolean = false

    @Volatile
    var streaming: Boolean = false

    override val isBusy: Boolean get() = busy

    override val isStreaming: Boolean get() = streaming

    override var messages: List<AgentMessage> = emptyList()

    override var activeToolNames: List<String> = emptyList()

    override var model: Model = fakeModel

    override var thinkingLevel: String = "off"

    val prompts = CopyOnWriteArrayList<PromptRecord>()

    val closed = CopyOnWriteArrayList<Long>()

    var suspendPrompt: Boolean = false

    val promptGate = CompletableDeferred<Unit>()

    var onPrompt: (suspend (String) -> Unit)? = null

    var sessionNameValue: String? = null

    @Volatile
    private var listener: (suspend (SessionEvent) -> Unit)? = null

    override suspend fun prompt(text: String, images: List<ImagePart>?, behavior: QueueBehavior?) {
        prompts += PromptRecord(text, images, behavior)
        onPrompt?.invoke(text)
        if (suspendPrompt) promptGate.await()
    }

    override fun abort() = Unit

    override suspend fun close() {
        closed += System.nanoTime()
    }

    override fun subscribe(listener: suspend (SessionEvent) -> Unit): () -> Unit {
        this.listener = listener
        return { this.listener = null }
    }

    suspend fun emit(event: SessionEvent) {
        listener?.invoke(event)
    }

    override fun clearQueues() = Unit

    override fun removeQueuedInput(text: String, behavior: QueueBehavior): Boolean = false

    override fun canRetryLastAssistant(): Boolean = false

    override suspend fun retryLastAssistant() = Unit

    override fun lastAssistantText(): String? = null

    override suspend fun setModel(model: Model, thinkingLevel: String?) {
        this.model = model
        if (thinkingLevel != null) this.thinkingLevel = thinkingLevel
    }

    override suspend fun setThinkingLevel(level: String) {
        thinkingLevel = level
    }

    override suspend fun compact(): SessionCompaction? = null

    override fun history(): List<AssistantMessage> = emptyList()

    override fun usage(): SessionUsage? = null

    override fun sessionName(): String? = sessionNameValue

    val customMessages = CopyOnWriteArrayList<CustomMessageRecord>()

    override suspend fun appendCustomMessage(customType: String, payloadJson: String, text: String, display: Boolean) {
        customMessages += CustomMessageRecord(customType, payloadJson, text, display)
    }

    override suspend fun appendTitle(title: String) {
        sessionNameValue = title
    }

    override suspend fun reloadMcp(servers: Map<String, McpServerConfig>) = Unit

    override suspend fun reconnectMcp(server: String) = Unit

    override fun mcpServers(): List<SessionMcpServerStatus> = emptyList()

    override fun deferredMcpToolCount(): Int = 0
}

data class CustomMessageRecord(
    val customType: String,
    val payloadJson: String,
    val text: String,
    val display: Boolean,
)
