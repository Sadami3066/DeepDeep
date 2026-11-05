package io.github.whywood.deepdeep.plugin.api

import kotlinx.serialization.json.JsonObject

sealed interface SlotState

data object NoSlotState : SlotState

data class SetupSlotState(
    val title: LocalizedText,
    val message: LocalizedText? = null,
    val url: String? = null,
    val imagePath: String? = null,
    val progress: Int? = null,
    val cancelled: Boolean = false,
) : SlotState

data class DetailSlotState(
    val pluginId: String,
    val status: PluginStatus,
    val rows: List<PluginDetailRow> = emptyList(),
) : SlotState

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

data class ComposerActionState(
    val actionId: String,
    val input: String = "",
    val sessionId: String? = null,
) : SlotState

data class PluginPageState(
    val pluginId: String,
    val pageId: String,
    val sessionId: String? = null,
) : SlotState
