package io.github.whywood.deepdeep.ui.common.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.whywood.deepdeep.plugin.api.SlotIds
import io.github.whywood.deepdeep.plugin.host.ui.HostCustomMessageSlotState
import io.github.whywood.deepdeep.plugin.host.ui.HostToolCardSlotState
import io.github.whywood.deepdeep.plugin.host.ui.PluginSlotHost
import io.github.whywood.deepdeep.plugin.manager.PluginManagerFacade
import kotlinx.serialization.json.JsonObject

internal val LocalPluginManager = staticCompositionLocalOf<PluginManagerFacade?> { null }

@Composable
internal fun ProvidePluginManager(manager: PluginManagerFacade, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPluginManager provides manager, content = content)
}

@Composable
internal fun PluginToolCardSlot(
    toolName: String,
    callId: String,
    args: JsonObject,
    result: io.github.whywood.deepdeep.plugin.api.PluginToolResult? = null,
    fallback: @Composable () -> Unit,
) {
    val manager = LocalPluginManager.current ?: run { fallback(); return }
    val uiState by manager.state.collectAsStateWithLifecycle()
    val entries = remember(uiState, toolName) { manager.slotEntries(SlotIds.toolCard(toolName)) }
    PluginSlotHost(
        entries = entries,
        state = HostToolCardSlotState(callId = callId, toolName = toolName, args = args, result = result),
        fallback = fallback,
    )
}


@Composable
internal fun PluginCustomMessageSlot(
    messageId: String,
    customType: String,
    payload: JsonObject,
    fallback: @Composable () -> Unit,
) {
    val manager = LocalPluginManager.current ?: run { fallback(); return }
    val uiState by manager.state.collectAsStateWithLifecycle()
    val entries = remember(uiState, customType) { manager.slotEntries(SlotIds.chatMessage(customType)) }
    PluginSlotHost(
        entries = entries,
        state = HostCustomMessageSlotState(messageId = messageId, customType = customType, payload = payload),
        fallback = fallback,
    )
}

