package io.github.whywood.superpi.ui.common.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.whywood.superpi.plugin.api.CustomMessageSlotState
import io.github.whywood.superpi.plugin.api.PluginToolResult
import io.github.whywood.superpi.plugin.api.ToolCardSlotState
import io.github.whywood.superpi.plugin.api.SlotIds
import io.github.whywood.superpi.plugin.api.ToolResultSource
import io.github.whywood.superpi.plugin.api.ToolResultState
import io.github.whywood.superpi.plugin.host.ui.ToolInvocation
import io.github.whywood.superpi.plugin.host.ui.ToolResultIdentity
import io.github.whywood.superpi.plugin.host.ui.toolInvocation
import io.github.whywood.superpi.plugin.host.ui.toolResultArguments
import io.github.whywood.superpi.plugin.host.ui.toolResultIdentity
import io.github.whywood.superpi.plugin.host.ui.structuredContent
import io.github.whywood.superpi.plugin.host.ui.PluginSlotHost
import io.github.whywood.superpi.plugin.host.ui.ToolRendererEntry
import io.github.whywood.superpi.plugin.manager.PluginManagerFacade
import io.github.whywood.superpi.runtime.ToolResultDetails
import io.github.whywood.superpi.runtime.ToolTrace
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

internal val LocalPluginManager = staticCompositionLocalOf<PluginManagerFacade?> { null }

@Composable
internal fun ProvidePluginManager(manager: PluginManagerFacade, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPluginManager provides manager, content = content)
}

@Composable
private fun PluginAnswerCardSlot(
    slotId: String,
    state: io.github.whywood.superpi.plugin.api.SlotState,
    fallback: @Composable () -> Unit,
): Boolean {
    val manager = LocalPluginManager.current ?: return false
    val uiState by manager.state.collectAsStateWithLifecycle()
    val entries = remember(uiState, slotId) { manager.slotEntries(slotId) }
    if (entries.none { it.composeCompatible }) return false
    PluginSlotHost(entries = entries, state = state, fallback = fallback)
    return true
}

@Composable
internal fun PluginToolCardSlot(
    toolName: String,
    callId: String,
    args: JsonObject,
    result: PluginToolResult? = null,
    trace: ToolTrace? = null,
    allowRenderers: Boolean = true,
    fallback: @Composable () -> Unit,
) {
    if (
        PluginAnswerCardSlot(
            slotId = SlotIds.toolCard(toolName),
            state = HostAnswerCardSlotState(
                callId = callId,
                toolName = toolName,
                args = args,
                result = result,
                messageId = "",
                customType = "",
                payload = JsonObject(emptyMap()),
            ),
            fallback = fallback,
        )
    ) {
        return
    }
    val manager = LocalPluginManager.current ?: run { fallback(); return }
    val uiState by manager.state.collectAsStateWithLifecycle()
    val renderer = if (allowRenderers && trace != null) {
        remember(uiState, trace) { trace.toolRenderer(manager) }
    } else {
        null
    }
    if (renderer == null || trace == null) {
        fallback()
        return
    }
    val invocation = remember(uiState, trace) { trace.toolInvocation(manager::isPluginTool) }
    val state = remember(trace, result, invocation) {
        HostToolResultState(
            callId = callId,
            toolName = invocation.identity.tool,
            source = invocation.identity.source,
            server = invocation.identity.server,
            arguments = invocation.arguments,
            result = result,
            structuredContent = invocation.structuredContent,
        )
    }
    PluginToolResultRendererHost(renderer, state, fallback)
}

@Composable
private fun PluginToolResultRendererHost(
    entry: ToolRendererEntry,
    state: ToolResultState,
    fallback: @Composable () -> Unit,
) {
    if (!entry.composeCompatible) {
        fallback()
        return
    }
    androidx.compose.runtime.key(entry.pluginId, entry.renderer.id) {
        entry.renderer.content(state)
    }
}

private fun ToolTrace.toolRenderer(manager: PluginManagerFacade): ToolRendererEntry? {
    val identity = toolInvocation(manager::isPluginTool).identity
    return manager.toolRenderer(identity.source, identity.server, identity.tool)
}

internal data class HostAnswerCardSlotState(
    override val callId: String,
    override val toolName: String,
    override val args: JsonObject,
    override val result: PluginToolResult?,
    override val messageId: String,
    override val customType: String,
    override val payload: JsonObject,
) : ToolCardSlotState, CustomMessageSlotState

internal data class HostToolResultState(
    override val callId: String,
    override val toolName: String,
    override val source: ToolResultSource,
    override val server: String?,
    override val arguments: JsonObject,
    override val result: PluginToolResult?,
    override val structuredContent: JsonElement?,
) : ToolResultState


@Composable
internal fun PluginCustomMessageSlot(
    messageId: String,
    customType: String,
    payload: JsonObject,
    fallback: @Composable () -> Unit,
) {
    val rendered = PluginAnswerCardSlot(
        slotId = SlotIds.chatMessage(customType),
        state = HostAnswerCardSlotState(
            callId = "",
            toolName = "",
            args = JsonObject(emptyMap()),
            result = null,
            messageId = messageId,
            customType = customType,
            payload = payload,
        ),
        fallback = fallback,
    )
    if (!rendered) fallback()
}

