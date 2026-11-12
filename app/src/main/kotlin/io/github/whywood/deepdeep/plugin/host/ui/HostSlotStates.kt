package io.github.whywood.deepdeep.plugin.host.ui

import io.github.whywood.deepdeep.plugin.api.CustomMessageSlotState
import io.github.whywood.deepdeep.plugin.api.PluginToolResult
import io.github.whywood.deepdeep.plugin.api.ToolCardSlotState
import kotlinx.serialization.json.JsonObject

internal data class HostToolCardSlotState(
    override val callId: String,
    override val toolName: String,
    override val args: JsonObject,
    override val result: PluginToolResult? = null,
) : ToolCardSlotState

internal data class HostCustomMessageSlotState(
    override val messageId: String,
    override val customType: String,
    override val payload: JsonObject,
) : CustomMessageSlotState
