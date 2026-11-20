package io.github.whywood.superpi.data.relay

import io.github.whywood.superpi.runtime.AgentRuntime
import io.github.whywood.superpi.runtime.RuntimeEvent
import io.github.whywood.superpi.runtime.SessionRuntimeEvent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class RemoteAgentServer(
    private val host: AgentRuntime,
    private val bots: suspend () -> List<RelayBot>,
    private val send: (JsonObject) -> Boolean,
    scope: CoroutineScope,
) {
    private val feeds = ConcurrentHashMap<String, Feed>()

    init {
        scope.launch {
            host.events.collect { event -> onRuntimeEvent(event) }
        }
    }

    fun reset() {
        feeds.clear()
    }

    suspend fun handle(frame: RelayServerFrame.Route) {
        try {
            when (frame.method) {
                "bot.prompt" -> prompt(frame)
                "bot.steer" -> steer(frame)
                "bot.follow_up" -> followUp(frame)
                "bot.abort" -> abort(frame)
                "bot.state" -> state(frame)
                "bot.resume" -> resume(frame)
                else -> reject(frame, "bad_request", "unknown method ${frame.method}")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            respondError(frame.id, "internal", error.message ?: "the bot failed")
        }
    }

    private suspend fun prompt(frame: RelayServerFrame.Route) {
        val botId = frame.params.string("botId")
        if (botId.isBlank()) {
            reject(frame, "bot_not_found", "botId is required")
            return
        }
        val text = frame.params.string("text")
        if (text.isBlank()) {
            reject(frame, "bad_request", "text is required")
            return
        }
        val existing = frame.params.string("sessionId").ifBlank { null }
        val sessionId = existing ?: run {
            if (bots().none { bot -> bot.id == botId }) {
                reject(frame, "bot_not_found", "bot $botId is not available")
                return
            }
            host.newSession(botId).sessionId
        }
        feeds[sessionId] = Feed(frame.id, AtomicLong(0))
        respondOk(frame.id, buildJsonObject { put("sessionId", sessionId) })
        host.send(sessionId, text)
    }

    private suspend fun steer(frame: RelayServerFrame.Route) {
        val sessionId = frame.params.string("sessionId")
        val text = frame.params.string("text")
        if (sessionId.isBlank() || text.isBlank()) {
            reject(frame, "bad_request", "sessionId and text are required")
            return
        }
        feeds.putIfAbsent(sessionId, Feed(frame.id, AtomicLong(0)))
        respondOk(frame.id, buildJsonObject { put("sessionId", sessionId) })
        host.steer(sessionId, text)
    }

    private suspend fun followUp(frame: RelayServerFrame.Route) {
        val sessionId = frame.params.string("sessionId")
        val text = frame.params.string("text")
        if (sessionId.isBlank() || text.isBlank()) {
            reject(frame, "bad_request", "sessionId and text are required")
            return
        }
        feeds.putIfAbsent(sessionId, Feed(frame.id, AtomicLong(0)))
        respondOk(frame.id, buildJsonObject { put("sessionId", sessionId) })
        host.followUp(sessionId, text)
    }

    private fun abort(frame: RelayServerFrame.Route) {
        val sessionId = frame.params.string("sessionId")
        if (sessionId.isBlank()) {
            reject(frame, "bad_request", "sessionId is required")
            return
        }
        val stopped = host.stop(sessionId)
        respondOk(
            frame.id,
            buildJsonObject {
                put("sessionId", sessionId)
                put("stopped", stopped.size)
            },
        )
    }

    private fun state(frame: RelayServerFrame.Route) {
        val sessionId = frame.params.string("sessionId")
        if (sessionId.isBlank()) {
            reject(frame, "bad_request", "sessionId is required")
            return
        }
        respondOk(
            frame.id,
            buildJsonObject {
                put("sessionId", sessionId)
                put("busy", host.busySessionIds.value.contains(sessionId))
            },
        )
    }

    private fun resume(frame: RelayServerFrame.Route) {
        val sessionId = frame.params.string("sessionId")
        if (sessionId.isBlank()) {
            reject(frame, "bad_request", "sessionId is required")
            return
        }
        feeds.putIfAbsent(sessionId, Feed(frame.id, AtomicLong(0)))
        respondOk(frame.id, buildJsonObject { put("sessionId", sessionId) })
    }

    private fun onRuntimeEvent(session: SessionRuntimeEvent) {
        val feed = feeds[session.sessionId] ?: return
        val events = RelayEventMapper.map(session.event)
        if (events.isEmpty()) return
        for (event in events) {
            val sent = send(RelayFrames.routeEvent(feed.routeId, feed.seq.getAndIncrement(), event))
            if (!sent) break
        }
        if (session.event is RuntimeEvent.RunFinished || session.event is RuntimeEvent.RunFailed) {
            feeds.remove(session.sessionId, feed)
        }
    }

    private fun respondOk(id: String, result: JsonObject) {
        send(RelayFrames.routeOk(id, result))
    }

    private fun respondError(id: String, code: String, message: String) {
        send(RelayFrames.routeError(id, code, message))
    }

    private fun reject(frame: RelayServerFrame.Route, code: String, message: String) {
        respondError(frame.id, code, message)
    }

    private fun JsonObject.string(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

    private data class Feed(
        val routeId: String,
        val seq: AtomicLong,
    )
}
