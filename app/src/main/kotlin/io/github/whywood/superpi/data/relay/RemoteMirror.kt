package io.github.whywood.superpi.data.relay

import java.io.File
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class RemoteMirrorLine(
    val role: String,
    val text: String,
    val tool: String?,
    val at: Long,
)

object RemoteMirrorProjection {
    const val ROLE_USER = "user"
    const val ROLE_ASSISTANT = "assistant"
    const val ROLE_TOOL = "tool"
    const val ROLE_ERROR = "error"

    fun user(text: String, at: Long): RemoteMirrorLine =
        RemoteMirrorLine(role = ROLE_USER, text = text, tool = null, at = at)

    fun project(event: RemoteAgentEvent, at: Long): RemoteMirrorLine? = when (event) {
        is RemoteAgentEvent.TextDelta -> RemoteMirrorLine(ROLE_ASSISTANT, event.delta, null, at)
        is RemoteAgentEvent.ToolCall -> RemoteMirrorLine(ROLE_TOOL, event.arguments, event.name, at)
        is RemoteAgentEvent.ToolResult -> RemoteMirrorLine(ROLE_TOOL, event.result, event.callId, at)
        is RemoteAgentEvent.Failure -> RemoteMirrorLine(ROLE_ERROR, event.message, null, at)
        else -> null
    }

    fun toJsonLine(line: RemoteMirrorLine): String = buildJsonObject {
        put("role", line.role)
        put("text", line.text)
        put("tool", line.tool)
        put("at", line.at)
    }.toString()
}

class RemoteMirrorStore(private val rootDir: File) {
    fun append(deviceId: String, botId: String, sessionId: String, line: RemoteMirrorLine): Boolean = runCatching {
        val directory = File(rootDir, "${mirrorPathSegment(deviceId)}/${mirrorPathSegment(botId)}")
        if (!directory.isDirectory && !directory.mkdirs()) return false
        File(directory, "${mirrorPathSegment(sessionId)}.jsonl")
            .appendText(RemoteMirrorProjection.toJsonLine(line) + "\n")
        true
    }.getOrDefault(false)
}

internal fun mirrorPathSegment(value: String): String = value
    .map { character ->
        if ((character.isLetterOrDigit() && character.code < 128) || character == '-' || character == '_') {
            character
        } else {
            '_'
        }
    }
    .joinToString("")
    .take(MAX_SEGMENT_LENGTH)
    .ifEmpty { "session" }

private const val MAX_SEGMENT_LENGTH = 64
