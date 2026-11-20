package io.github.whywood.superpi.remote

import io.github.whywood.superpi.sdk.agent.AgentMessage
import io.github.whywood.superpi.sdk.agent.asAgentMessage
import io.github.whywood.superpi.sdk.ai.AssistantMessage
import io.github.whywood.superpi.sdk.ai.PlainTextContent
import io.github.whywood.superpi.sdk.ai.ToolResultMessage
import io.github.whywood.superpi.sdk.ai.UserMessage
import io.github.whywood.superpi.sdk.coding.paths.AgentPaths
import io.github.whywood.superpi.sdk.coding.session.CustomEntry
import io.github.whywood.superpi.sdk.coding.session.SessionEntry
import io.github.whywood.superpi.sdk.coding.session.SessionManager
import io.github.whywood.superpi.sdk.coding.session.SessionMessageEntry
import io.github.whywood.superpi.sdk.coding.session.SessionStats
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okio.FileSystem
import okio.Path.Companion.toPath

class RemoteMirrorSession internal constructor(
    private val manager: SessionManager,
) {
    val sessionId: String get() = manager.sessionId

    val sessionFile: String? get() = manager.sessionFile?.toString()

    var peerSessionId: String? = null
        private set

    init {
        val mapping = manager.getEntries().asReversed()
            .filterIsInstance<CustomEntry>()
            .firstOrNull { it.customType == CUSTOM_TYPE }
        val data = mapping?.data as? JsonObject
        peerSessionId = (data?.get("peerSessionId") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    }

    suspend fun appendUserMessage(text: String) {
        manager.appendMessage(UserMessage(content = PlainTextContent(text), timestamp = System.currentTimeMillis()))
    }

    suspend fun appendAssistantMessage(message: AssistantMessage) {
        manager.appendMessage(message)
    }

    suspend fun appendToolResult(message: ToolResultMessage) {
        manager.appendMessage(message)
    }

    suspend fun appendRemoteMapping(deviceId: String, botId: String, peerSessionId: String) {
        manager.appendCustomEntry(
            CUSTOM_TYPE,
            buildJsonObject {
                put("deviceId", deviceId)
                put("botId", botId)
                put("peerSessionId", peerSessionId)
            },
        )
        this.peerSessionId = peerSessionId
    }

    suspend fun appendName(name: String) {
        manager.appendSessionInfo(name)
    }

    suspend fun appendCustomMessage(customType: String, text: String, payloadJson: String?, display: Boolean) {
        val details = payloadJson?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() }
        manager.appendCustomMessageEntry(customType, PlainTextContent(text), display, details)
    }

    suspend fun appendCustomEntry(customType: String, dataJson: String) {
        val data = runCatching { Json.parseToJsonElement(dataJson) }.getOrNull()
        manager.appendCustomEntry(customType, data)
    }

    fun messages(): List<AgentMessage> = manager.getBranch()
        .filterIsInstance<SessionMessageEntry>()
        .map { entry -> entry.message.asAgentMessage() }

    fun entries(): List<SessionEntry> = manager.getBranch()

    fun name(): String? = manager.getSessionName()

    fun stats(): SessionStats = manager.getSessionStats()

    suspend fun close() {
        manager.close()
    }

    companion object {
        internal const val CUSTOM_TYPE = "superpi.remote"

        suspend fun create(cwd: File, agentDir: File): RemoteMirrorSession {
            val paths = AgentPaths(cwd.absolutePath.toPath(), agentDir.absolutePath.toPath())
            return RemoteMirrorSession(SessionManager.create(paths, FileSystem.SYSTEM))
        }

        suspend fun open(file: File): RemoteMirrorSession =
            RemoteMirrorSession(SessionManager.open(file.absolutePath.toPath(), FileSystem.SYSTEM))
    }
}
