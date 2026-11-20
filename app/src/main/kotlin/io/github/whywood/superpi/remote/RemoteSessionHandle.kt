package io.github.whywood.superpi.remote

import io.github.whywood.superpi.data.relay.RemoteEvent
import io.github.whywood.superpi.runtime.AssistantMessage as HistoryAssistantMessage
import io.github.whywood.superpi.runtime.CapabilitySnapshot
import io.github.whywood.superpi.runtime.HistoryMapper
import io.github.whywood.superpi.runtime.ImagePart
import io.github.whywood.superpi.runtime.SessionCompaction
import io.github.whywood.superpi.runtime.SessionHandle
import io.github.whywood.superpi.runtime.SessionMcpServerStatus
import io.github.whywood.superpi.runtime.SessionUsage
import io.github.whywood.superpi.sdk.agent.AgentEvent
import io.github.whywood.superpi.sdk.agent.AgentMessage
import io.github.whywood.superpi.sdk.agent.StandardAgentMessage
import io.github.whywood.superpi.sdk.agent.asAgentMessage
import io.github.whywood.superpi.sdk.agent.asLlmMessage
import io.github.whywood.superpi.sdk.ai.AssistantMessage as PiAssistantMessage
import io.github.whywood.superpi.sdk.ai.Model
import io.github.whywood.superpi.sdk.ai.ModelCost
import io.github.whywood.superpi.sdk.ai.ModelInputModality
import io.github.whywood.superpi.sdk.ai.PlainTextContent
import io.github.whywood.superpi.sdk.ai.Provider
import io.github.whywood.superpi.sdk.ai.StopReason
import io.github.whywood.superpi.sdk.ai.TextContent
import io.github.whywood.superpi.sdk.ai.UserMessage
import io.github.whywood.superpi.sdk.coding.session.QueueBehavior
import io.github.whywood.superpi.sdk.coding.session.SessionEvent
import io.github.whywood.superpi.sdk.mcp.config.McpServerConfig
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

class RemoteSessionHandle internal constructor(
    private val target: RemoteSessionTarget,
    private val transport: RemoteAgentTransport,
    private val mirror: RemoteMirrorSession,
    injectedScope: CoroutineScope? = null,
) : SessionHandle {
    private val listeners = CopyOnWriteArrayList<suspend (SessionEvent) -> Unit>()
    private val ownsScope = injectedScope == null
    private val scope: CoroutineScope = injectedScope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val remoteModel: Model = buildRemoteModel(target)

    @Volatile
    private var activeTurn: RemoteTurn? = null

    @Volatile
    private var closed: Boolean = false

    private var peerSessionId: String? = mirror.peerSessionId

    override val sessionId: String get() = mirror.sessionId

    override val sessionFile: String? get() = mirror.sessionFile

    override val isBusy: Boolean get() = activeTurn != null

    override val isStreaming: Boolean get() = activeTurn?.streaming == true

    override val messages: List<AgentMessage> get() = mirror.messages()

    override val activeToolNames: List<String>
        get() = activeTurn?.state?.runningToolNames().orEmpty()

    override val model: Model get() = remoteModel

    override val thinkingLevel: String get() = THINKING_LEVEL

    override suspend fun prompt(text: String, images: List<ImagePart>?, behavior: QueueBehavior?) {
        if (closed) return
        when (behavior) {
            QueueBehavior.Steer -> queueInput(BOT_STEER, text)
            QueueBehavior.FollowUp -> queueInput(BOT_FOLLOW_UP, text)
            null -> runPrompt(text)
        }
    }

    override fun abort() {
        val turn = activeTurn
        turn?.aborted = true
        peerSessionId?.let { sessionId -> abortRemote(sessionId) }
        turn?.settled?.complete(Unit)
    }

    override suspend fun close() {
        closed = true
        activeTurn?.settled?.complete(Unit)
        activeTurn = null
        listeners.clear()
        mirror.close()
        if (ownsScope) scope.cancel()
    }

    override fun subscribe(listener: suspend (SessionEvent) -> Unit): () -> Unit {
        listeners += listener
        return { listeners.remove(listener) }
    }

    override fun clearQueues() {
    }

    override fun removeQueuedInput(text: String, behavior: QueueBehavior): Boolean = false

    override fun canRetryLastAssistant(): Boolean = false

    override suspend fun retryLastAssistant() {
    }

    override fun lastAssistantText(): String? {
        val last = mirror.messages().asReversed().firstOrNull { it.role == ROLE_ASSISTANT } ?: return null
        val message = last.asLlmMessage() as? PiAssistantMessage ?: return null
        return message.content.filterIsInstance<TextContent>().joinToString("") { it.text }.ifBlank { null }
    }

    override suspend fun setModel(model: Model, thinkingLevel: String?) {
    }

    override suspend fun setThinkingLevel(level: String) {
    }

    override fun registerProvider(provider: Provider) {
    }

    override suspend fun compact(): SessionCompaction? = null

    override fun history(): List<HistoryAssistantMessage> = HistoryMapper.mapEntries(mirror.entries())

    override fun usage(): SessionUsage? = null

    override fun sessionName(): String? = mirror.name()

    override suspend fun appendTitle(title: String) {
        mirror.appendName(title)
    }

    override suspend fun appendCustomMessage(
        customType: String,
        payloadJson: String,
        text: String,
        display: Boolean,
    ) {
        mirror.appendCustomMessage(customType, text, payloadJson, display)
    }

    override suspend fun appendCustomEntry(customType: String, dataJson: String) {
        mirror.appendCustomEntry(customType, dataJson)
    }

    override suspend fun capabilitySnapshot(): CapabilitySnapshot = CapabilitySnapshot()

    override fun capabilityBaseline(): CapabilitySnapshot? = CapabilitySnapshot()

    override suspend fun reloadMcp(servers: Map<String, McpServerConfig>) {
    }

    override suspend fun reconnectMcp(server: String) {
    }

    override fun mcpServers(): List<SessionMcpServerStatus> = emptyList()

    override fun deferredMcpToolCount(): Int = 0

    private suspend fun runPrompt(text: String) {
        if (!target.isOnline()) {
            emit(SessionEvent.SessionError(offlineMessage()))
            return
        }
        val turn = RemoteTurn(target.botName)
        activeTurn = turn
        try {
            mirror.appendUserMessage(text)
            coroutineScope {
                val collector: Job = launch(start = CoroutineStart.UNDISPATCHED) {
                    transport.remoteEvents.collect { event -> onRemoteEvent(turn, event) }
                }
                try {
                    val sessionId = peerSessionId
                    val result = transport.call(
                        to = target.deviceId,
                        method = BOT_PROMPT,
                        params = promptParams(text, sessionId),
                        onRouteId = { route -> turn.routeId = route },
                    )
                    if (result == null) {
                        turn.failure = NO_ANSWER_MESSAGE
                    } else {
                        rememberPeerSession(result)
                        if (peerSessionId == null) turn.failure = NO_SESSION_MESSAGE
                    }
                    if (turn.failure == null && turn.aborted) {
                        peerSessionId?.let { sessionId -> abortRemote(sessionId) }
                    } else if (turn.failure == null) {
                        val settled = withTimeoutOrNull(SETTLE_TIMEOUT_MILLIS) { turn.settled.await() }
                        if (settled == null) turn.failure = SETTLE_TIMEOUT_MESSAGE
                    }
                    finishTurn(turn)
                } finally {
                    collector.cancel()
                }
            }
        } finally {
            activeTurn = null
        }
    }

    private suspend fun queueInput(method: String, text: String) {
        if (!target.isOnline()) {
            emit(SessionEvent.SessionError(offlineMessage()))
            return
        }
        val sessionId = peerSessionId
        if (sessionId == null) {
            emit(SessionEvent.SessionError(NO_SESSION_MESSAGE))
            return
        }
        val result = transport.call(
            to = target.deviceId,
            method = method,
            params = buildJsonObject {
                put("botId", target.botId)
                put("sessionId", sessionId)
                put("text", text)
            },
        )
        if (result == null) {
            emit(SessionEvent.SessionError(NO_ANSWER_MESSAGE))
            return
        }
        val turn = activeTurn
        if (turn != null) {
            turn.queuedTexts.add(text)
            emitAgent(AgentEvent.MessageStart(userAgentMessage(text)))
        } else {
            mirror.appendUserMessage(text)
        }
    }

    private fun userAgentMessage(text: String): AgentMessage =
        StandardAgentMessage(UserMessage(content = PlainTextContent(text), timestamp = System.currentTimeMillis()))

    private suspend fun onRemoteEvent(turn: RemoteTurn, event: RemoteEvent) {
        if (event.routeId != turn.routeId) return
        if (!turn.accept(event.seq)) return
        when (val decoded = RemoteEventMapping.decode(event.event)) {
            null -> Unit
            is RemoteBotEvent.AgentStart -> emitAgent(AgentEvent.AgentStart)
            is RemoteBotEvent.Failed -> {
                turn.failure = decoded.message
                turn.settled.complete(Unit)
            }

            is RemoteBotEvent.Settled -> turn.settled.complete(Unit)
            is RemoteBotEvent.TextDelta, is RemoteBotEvent.ReasoningDelta -> {
                turn.streaming = true
                emitMapped(turn, decoded)
            }

            is RemoteBotEvent.ToolCall -> {
                turn.streaming = true
                emitMapped(turn, decoded)
            }

            else -> emitMapped(turn, decoded)
        }
    }

    private suspend fun emitMapped(turn: RemoteTurn, event: RemoteBotEvent) {
        for (agentEvent in turn.state.consume(event)) emitAgent(agentEvent)
    }

    private suspend fun emitAgent(event: AgentEvent) {
        emit(SessionEvent.AgentEventReceived(event))
    }

    private suspend fun finishTurn(turn: RemoteTurn) {
        val failure = turn.failure
        val stopReason = when {
            failure != null -> StopReason.Error
            turn.aborted || turn.state.endStatus == STATUS_STOPPED -> StopReason.Aborted
            turn.state.endStatus == STATUS_FAILED -> StopReason.Error
            else -> StopReason.Stop
        }
        val errorMessage = failure
            ?: if (stopReason == StopReason.Error) FAILED_MESSAGE else null
        val messages = persistTurn(turn, stopReason, errorMessage)
        if (messages.isNotEmpty()) emitAgent(AgentEvent.AgentEnd(messages))
        if (failure != null) emit(SessionEvent.SessionError(failure))
    }

    private suspend fun persistTurn(
        turn: RemoteTurn,
        stopReason: StopReason,
        errorMessage: String?,
    ): List<AgentMessage> {
        val state = turn.state
        for (queued in turn.queuedTexts) mirror.appendUserMessage(queued)
        val results = state.toolResultMessages()
        val assistant = state.assistantMessage(stopReason, errorMessage)
        val written = assistant.content.isNotEmpty() || results.isNotEmpty() || stopReason == StopReason.Aborted
        if (!written) return emptyList()
        mirror.appendAssistantMessage(assistant)
        results.forEach { result -> mirror.appendToolResult(result) }
        return buildList {
            add(assistant.asAgentMessage())
            results.forEach { result -> add(result.asAgentMessage()) }
        }
    }

    private suspend fun rememberPeerSession(result: JsonObject) {
        val returned = (result["sessionId"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: return
        if (returned == peerSessionId) return
        peerSessionId = returned
        mirror.appendRemoteMapping(target.deviceId, target.botId, returned)
    }

    private fun abortRemote(sessionId: String) {
        if (closed) return
        scope.launch {
            runCatching {
                transport.call(target.deviceId, BOT_ABORT, buildJsonObject {
                    put("botId", target.botId)
                    put("sessionId", sessionId)
                })
            }
        }
    }

    private suspend fun emit(event: SessionEvent) {
        for (listener in listeners) {
            try {
                listener(event)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
            }
        }
    }

    private fun promptParams(text: String, sessionId: String?): JsonObject = buildJsonObject {
        put("botId", target.botId)
        if (sessionId != null) put("sessionId", sessionId)
        put("text", text)
    }

    private fun offlineMessage(): String =
        "Remote bot ${target.botName} on ${target.deviceName} is offline."

    private class RemoteTurn(modelName: String) {
        val state = RemoteTurnState(modelName)
        val settled = CompletableDeferred<Unit>()
        val queuedTexts = CopyOnWriteArrayList<String>()

        @Volatile
        var routeId: String? = null

        @Volatile
        var aborted: Boolean = false

        @Volatile
        var failure: String? = null

        @Volatile
        var streaming: Boolean = false

        private var acceptedSeq: Long = -1L

        @Synchronized
        fun accept(seq: Long): Boolean {
            if (seq <= acceptedSeq) return false
            acceptedSeq = seq
            return true
        }
    }

    private companion object {
        private const val BOT_PROMPT = "bot.prompt"
        private const val BOT_STEER = "bot.steer"
        private const val BOT_FOLLOW_UP = "bot.follow_up"
        private const val BOT_ABORT = "bot.abort"
        private const val STATUS_STOPPED = "stopped"
        private const val STATUS_FAILED = "failed"
        private const val ROLE_ASSISTANT = "assistant"
        private const val THINKING_LEVEL = "off"
        private const val SETTLE_TIMEOUT_MILLIS = 10 * 60 * 1000L
        private const val NO_ANSWER_MESSAGE = "The remote device did not answer."
        private const val NO_SESSION_MESSAGE = "The remote bot did not start a session."
        private const val SETTLE_TIMEOUT_MESSAGE = "The remote bot did not finish the turn."
        private const val FAILED_MESSAGE = "The bot on the remote device failed."
    }
}

private fun buildRemoteModel(target: RemoteSessionTarget): Model = Model(
    id = "remote",
    name = target.botName,
    api = "",
    provider = RemoteTurnState.REMOTE_PROVIDER,
    baseUrl = "",
    input = listOf(ModelInputModality.Text),
    cost = ModelCost(input = 0.0, output = 0.0, cacheRead = 0.0, cacheWrite = 0.0),
    reasoning = false,
    contextWindow = 0L,
    maxTokens = 0L,
)
