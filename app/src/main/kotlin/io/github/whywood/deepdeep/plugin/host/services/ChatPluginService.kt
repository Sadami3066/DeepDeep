package io.github.whywood.deepdeep.plugin.host.services

import io.github.whywood.deepdeep.plugin.api.ChatDelivery
import io.github.whywood.deepdeep.plugin.api.ChatMessageOptions
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
        if (!options.triggerTurn) return ChatSendResult.Displayed(sessionId)
        return when (val result = commands.deliverPluginTurn(sessionId, text, options.deliverAs == ChatDelivery.FOLLOW_UP)) {
            is PluginTurnResult.Started -> ChatSendResult.Started(sessionId, result.runId)
            is PluginTurnResult.Queued -> ChatSendResult.Queued(sessionId, options.deliverAs)
        }
    }
}
