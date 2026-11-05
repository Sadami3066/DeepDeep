package io.github.whywood.deepdeep.plugin.host.services

import io.github.whywood.deepdeep.plugin.api.ChatService
import kotlinx.serialization.json.JsonObject

internal class ChatPluginService(
    private val post: suspend (String, String, JsonObject, String, Boolean) -> Boolean,
) : ChatService {
    override suspend fun postCustomMessage(
        sessionId: String,
        customType: String,
        payload: JsonObject,
        text: String,
        display: Boolean,
    ): Boolean = post(sessionId, customType, payload, text, display)
}
