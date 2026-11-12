package io.github.whywood.deepdeep.plugin.host.services

import io.github.whywood.deepdeep.plugin.api.ChatDelivery
import io.github.whywood.deepdeep.plugin.api.ChatMessageOptions
import io.github.whywood.deepdeep.plugin.api.ChatSendKind
import io.github.whywood.deepdeep.plugin.api.ChatSendResult
import io.github.whywood.deepdeep.plugin.api.ChatService
import io.github.whywood.deepdeep.runtime.PluginTurnResult
import io.github.whywood.deepdeep.runtime.SessionCommands
import kotlinx.serialization.json.JsonObject

internal class ChatPluginService(
    private val post: suspend (String, String, JsonObject, String, Boolean) -> Boolean,
    private val commands: SessionCommands,
) : ChatService {
    override suspend fun postCustomMessage(
        sessionId: String,
        customType: String,
        payload: JsonObject,
        text: String,
        display: Boolean,
    ): Boolean = post(sessionId, customType, payload, text, display)

    override suspend fun sendMessage(
        sessionId: String,
        text: String,
        options: ChatMessageOptions,
    ): ChatSendResult {
        options.display?.let { display ->
            post(sessionId, display.customType, display.payload, display.text, true)
        }
        if (!options.triggerTurn) {
            return HostChatSendResult(ChatSendKind.DISPLAYED, sessionId, runId = null, delivery = null)
        }
        return when (val result = commands.deliverPluginTurn(sessionId, text, options.deliverAs == ChatDelivery.FOLLOW_UP)) {
            is PluginTurnResult.Started -> HostChatSendResult(ChatSendKind.STARTED, sessionId, result.runId, null)
            is PluginTurnResult.Queued -> HostChatSendResult(ChatSendKind.QUEUED, sessionId, null, options.deliverAs)
        }
    }

    private class HostChatSendResult(
        override val kind: ChatSendKind,
        override val sessionId: String,
        override val runId: String?,
        override val delivery: ChatDelivery?,
    ) : ChatSendResult
}
