package io.github.whywood.superpi.ui.remoteagent

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.whywood.superpi.data.relay.RelayDevice
import io.github.whywood.superpi.data.relay.RelayLive
import io.github.whywood.superpi.data.relay.RelayLiveState
import io.github.whywood.superpi.data.relay.RemoteAgentEvent
import io.github.whywood.superpi.data.relay.RemoteAgentProtocol
import io.github.whywood.superpi.data.relay.RemoteCallCodes
import io.github.whywood.superpi.data.relay.RemoteCallError
import io.github.whywood.superpi.data.relay.RemoteEvent
import io.github.whywood.superpi.data.relay.RemoteMirrorLine
import io.github.whywood.superpi.data.relay.RemoteMirrorProjection
import io.github.whywood.superpi.data.relay.RemoteMirrorStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal class RemoteChatStateHolder(
    private val relayLive: RelayLive,
    private val mirror: RemoteMirrorStore,
    private val deviceId: String,
    private val botId: String,
    private val scope: CoroutineScope,
    private val mirrorScope: CoroutineScope,
) {
    var deviceName by mutableStateOf(deviceId)
        private set
    var botName by mutableStateOf(botId)
        private set
    var deviceOnline by mutableStateOf(false)
        private set
    var relayOnline by mutableStateOf(relayLive.state.value == RelayLiveState.Online)
        private set
    var blocks by mutableStateOf<List<RemoteChatBlock>>(emptyList())
        private set
    var running by mutableStateOf(false)
        private set
    var settled by mutableStateOf(true)
        private set
    var canAbort by mutableStateOf(false)
        private set
    var sendFailure by mutableStateOf<RemoteChatFailure?>(null)
        private set

    private var sessionId: String? = null
    private var activeRouteId: String? = null
    private var callInFlight = false
    private var firstEventWatchdog: Job? = null
    private var failedText: String? = null
    private var nextKey = 0L
    private var mirrorSession: String? = null
    private val pendingMirrorLines = mutableListOf<RemoteMirrorLine>()
    private val mirrorQueue = Channel<Pair<String, RemoteMirrorLine>>(Channel.UNLIMITED)

    init {
        mirrorScope.launch(Dispatchers.IO) {
            for ((session, line) in mirrorQueue) {
                mirror.append(deviceId, botId, session, line)
            }
        }
        relayLive.devices.value.firstOrNull { it.deviceId == deviceId }?.let(::applyDevice)
        scope.launch {
            relayLive.state.collect(::onRelayState)
        }
        scope.launch {
            relayLive.devices.collect { devices ->
                val device = devices.firstOrNull { it.deviceId == deviceId }
                if (device == null) {
                    deviceOnline = false
                } else {
                    applyDevice(device)
                }
            }
        }
        scope.launch {
            relayLive.remoteEvents.collect(::onRemoteEvent)
        }
    }

    fun close() {
        mirrorQueue.close()
    }

    fun send(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || running || !settled) return false
        if (!relayOnline) {
            sendFailure = RemoteChatFailure.NotConnected
            return false
        }
        sendFailure = null
        failedText = null
        blocks = blocks + RemoteChatBlock.User(nextKey(), trimmed)
        running = true
        settled = false
        sessionId?.let { session ->
            queueMirror(session, RemoteMirrorProjection.user(trimmed, System.currentTimeMillis()))
        }
        scope.launch { deliver(trimmed) }
        return true
    }

    fun retry(): Boolean {
        val text = failedText ?: return false
        if (running || !settled) return false
        if (!relayOnline) {
            sendFailure = RemoteChatFailure.NotConnected
            return false
        }
        sendFailure = null
        failedText = null
        running = true
        settled = false
        scope.launch { deliver(text) }
        return true
    }

    fun abort() {
        if (!running) return
        val current = sessionId ?: return
        scope.launch {
            val result = relayLive.call(deviceId, "bot.abort", buildJsonObject { put("sessionId", current) })
            if (result == null) {
                val error = relayLive.lastCallError.value
                if (error?.code != RemoteCallCodes.DISCONNECTED) {
                    appendFailure(error?.let(RemoteChatFailure::from) ?: RemoteChatFailure.Disconnected)
                }
            }
        }
    }

    private suspend fun deliver(text: String) {
        callInFlight = true
        try {
            val params = buildJsonObject {
                put("botId", botId)
                sessionId?.let { put("sessionId", it) }
                put("text", text)
            }
            val result = relayLive.call(deviceId, "bot.prompt", params) { routeId -> activeRouteId = routeId }
            if (result == null) {
                failSend(text, relayLive.lastCallError.value)
                return
            }
            sendFailure = null
            canAbort = true
            val replySession = result["sessionId"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            if (sessionId == null && replySession != null) {
                sessionId = replySession
                beginMirror(replySession, text)
            }
            firstEventWatchdog?.cancel()
            firstEventWatchdog = scope.launch {
                delay(FIRST_EVENT_TIMEOUT_MILLIS)
                interrupt(RemoteChatFailure.Timeout)
            }
        } finally {
            callInFlight = false
        }
    }

    private fun failSend(text: String, error: RemoteCallError?) {
        running = false
        settled = true
        canAbort = false
        activeRouteId = null
        failedText = text
        sendFailure = error?.let(RemoteChatFailure::from) ?: RemoteChatFailure.Disconnected
    }

    private fun onRelayState(state: RelayLiveState) {
        relayOnline = state == RelayLiveState.Online
        if (relayOnline) return
        deviceOnline = false
        if (!callInFlight) interrupt(RemoteChatFailure.Disconnected)
    }

    private fun applyDevice(device: RelayDevice) {
        deviceName = device.name
        deviceOnline = device.online
        device.bots.firstOrNull { it.id == botId }?.let { botName = it.name }
        if (!device.online && !callInFlight) interrupt(RemoteChatFailure.DeviceOffline)
    }

    private fun interrupt(failure: RemoteChatFailure) {
        if (!running) return
        firstEventWatchdog?.cancel()
        firstEventWatchdog = null
        running = false
        settled = true
        canAbort = false
        activeRouteId = null
        appendFailure(failure)
    }

    private fun onRemoteEvent(event: RemoteEvent) {
        if (event.routeId != activeRouteId) return
        firstEventWatchdog?.cancel()
        firstEventWatchdog = null
        val decoded = RemoteAgentProtocol.decode(event.event) ?: return
        when (decoded) {
            RemoteAgentEvent.AgentStart -> {
                running = true
                settled = false
            }

            is RemoteAgentEvent.TextDelta -> appendAssistantText(decoded.delta)
            is RemoteAgentEvent.ReasoningDelta -> appendAssistantReasoning(decoded.delta)
            is RemoteAgentEvent.ToolCall -> upsertToolCall(decoded)
            is RemoteAgentEvent.ToolResult -> finishToolCall(decoded)
            is RemoteAgentEvent.Failure -> {
                appendFailure(RemoteChatFailure.Remote(decoded.message))
                running = false
                settled = true
                canAbort = false
            }

            RemoteAgentEvent.AgentEnd -> {
                running = false
                canAbort = false
            }

            RemoteAgentEvent.AgentSettled -> {
                running = false
                settled = true
                canAbort = false
                activeRouteId = null
            }
        }
        mirrorEvent(decoded)
    }

    private fun appendAssistantText(delta: String) {
        val list = blocks.toMutableList()
        val last = list.lastOrNull()
        if (last is RemoteChatBlock.Assistant) {
            list[list.lastIndex] = last.copy(text = last.text + delta)
        } else {
            list += RemoteChatBlock.Assistant(nextKey(), delta, "")
        }
        blocks = list
    }

    private fun appendAssistantReasoning(delta: String) {
        val list = blocks.toMutableList()
        val last = list.lastOrNull()
        if (last is RemoteChatBlock.Assistant) {
            list[list.lastIndex] = last.copy(reasoning = last.reasoning + delta)
        } else {
            list += RemoteChatBlock.Assistant(nextKey(), "", delta)
        }
        blocks = list
    }

    private fun upsertToolCall(call: RemoteAgentEvent.ToolCall) {
        val list = blocks.toMutableList()
        val index = list.indexOfLast { block -> block is RemoteChatBlock.Tool && block.callId == call.callId }
        if (index >= 0) {
            val existing = list[index] as RemoteChatBlock.Tool
            list[index] = existing.copy(
                name = call.name.ifEmpty { existing.name },
                arguments = call.arguments.ifEmpty { existing.arguments },
            )
        } else {
            list += RemoteChatBlock.Tool(nextKey(), call.callId, call.name, call.arguments, null, false)
        }
        blocks = list
    }

    private fun finishToolCall(result: RemoteAgentEvent.ToolResult) {
        val list = blocks.toMutableList()
        val index = list.indexOfLast { block -> block is RemoteChatBlock.Tool && block.callId == result.callId }
        if (index >= 0) {
            val existing = list[index] as RemoteChatBlock.Tool
            list[index] = existing.copy(result = result.result, isError = result.isError)
        } else {
            list += RemoteChatBlock.Tool(nextKey(), result.callId, "", "", result.result, result.isError)
        }
        blocks = list
    }

    private fun appendFailure(failure: RemoteChatFailure) {
        blocks = blocks + RemoteChatBlock.Failure(nextKey(), failure)
    }

    private fun beginMirror(session: String, userText: String) {
        mirrorSession = session
        queueMirror(session, RemoteMirrorProjection.user(userText, System.currentTimeMillis()))
        val pending = pendingMirrorLines.toList()
        pendingMirrorLines.clear()
        pending.forEach { line -> queueMirror(session, line) }
    }

    private fun mirrorEvent(event: RemoteAgentEvent) {
        val line = RemoteMirrorProjection.project(event, System.currentTimeMillis()) ?: return
        val session = mirrorSession
        if (session == null) {
            pendingMirrorLines += line
        } else {
            queueMirror(session, line)
        }
    }

    private fun queueMirror(session: String, line: RemoteMirrorLine) {
        mirrorQueue.trySend(session to line)
    }

    private fun nextKey(): Long = nextKey++

    private companion object {
        private const val FIRST_EVENT_TIMEOUT_MILLIS = 30_000L
    }
}
