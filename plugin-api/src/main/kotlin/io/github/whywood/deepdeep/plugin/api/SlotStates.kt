package io.github.whywood.deepdeep.plugin.api

import kotlinx.serialization.json.JsonObject

public interface SlotState

public data object NoSlotState : SlotState

public interface ToolCardSlotState : SlotState {
    public val callId: String
    public val toolName: String
    public val args: JsonObject
    public val result: PluginToolResult?
}

public interface CustomMessageSlotState : SlotState {
    public val messageId: String
    public val customType: String
    public val payload: JsonObject
}
