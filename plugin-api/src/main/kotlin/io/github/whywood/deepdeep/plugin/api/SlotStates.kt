package io.github.whywood.deepdeep.plugin.api

import kotlinx.serialization.json.JsonObject

sealed interface SlotState

data object NoSlotState : SlotState

data class ToolCardSlotState(
    val callId: String,
    val toolName: String,
    val args: JsonObject = JsonObject(emptyMap()),
    val result: PluginToolResult? = null,
) : SlotState

data class CustomMessageSlotState(
    val messageId: String,
    val customType: String,
    val payload: JsonObject = JsonObject(emptyMap()),
) : SlotState
