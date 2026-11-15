package io.github.whywood.deepdeep.ui.common.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.whywood.deepdeep.plugin.api.PluginToolResult
import io.github.whywood.deepdeep.plugin.api.SlotIds
import io.github.whywood.deepdeep.plugin.api.ToolResultSource
import io.github.whywood.deepdeep.plugin.api.ToolResultState
import io.github.whywood.deepdeep.plugin.host.ui.HostCustomMessageSlotState
import io.github.whywood.deepdeep.plugin.host.ui.HostToolCardSlotState
import io.github.whywood.deepdeep.plugin.host.ui.PluginSlotHost
import io.github.whywood.deepdeep.plugin.host.ui.ToolRendererEntry
import io.github.whywood.deepdeep.plugin.manager.PluginManagerFacade
import io.github.whywood.deepdeep.runtime.ToolResultDetails
import io.github.whywood.deepdeep.runtime.ToolTrace
import kotlinx.serialization.json.JsonElement
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
    result: PluginToolResult? = null,
    trace: ToolTrace? = null,
    allowRenderers: Boolean = true,
    fallback: @Composable () -> Unit,
) {
    val manager = LocalPluginManager.current ?: run { fallback(); return }
    val uiState by manager.state.collectAsStateWithLifecycle()
    val entries = remember(uiState, toolName) { manager.slotEntries(SlotIds.toolCard(toolName)) }
    if (entries.any { it.composeCompatible }) {
        PluginSlotHost(
            entries = entries,
            state = HostToolCardSlotState(callId = callId, toolName = toolName, args = args, result = result),
            fallback = fallback,
        )
        return
    }
    val renderer = if (allowRenderers && trace != null) {
        remember(uiState, trace) { trace.toolRenderer(manager) }
    } else {
        null
    }
    if (renderer == null || trace == null) {
        fallback()
        return
    }
    val identity = remember(uiState, trace) { trace.toolResultIdentity(manager::isPluginTool) }
    val state = remember(trace, result) {
        HostToolResultState(
            callId = callId,
            toolName = identity.tool,
            source = identity.source,
            server = identity.server,
            arguments = trace.toolResultArguments(),
            result = result,
            structuredContent = ToolResultDetails.structuredContent(trace.resultDetails),
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
    val identity = toolResultIdentity(manager::isPluginTool)
    return manager.toolRenderer(identity.source, identity.server, identity.tool)
}

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
internal fun rememberPluginCardTraceKeys(traces: List<ToolTrace>): Set<String> {
    val manager = LocalPluginManager.current ?: return emptySet()
    if (traces.isEmpty()) return emptySet()
    val uiState by manager.state.collectAsStateWithLifecycle()
    return remember(uiState, traces) {
        traces.filterTo(mutableSetOf()) { trace ->
            trace.name in HostCardToolNames && return@filterTo false
            if (manager.slotEntries(SlotIds.toolCard(trace.name)).isNotEmpty()) return@filterTo true
            val identity = trace.toolResultIdentity(manager::isPluginTool)
            manager.toolRenderer(identity.source, identity.server, identity.tool) != null
        }.mapTo(mutableSetOf()) { it.cardKey() }
    }
}

internal val HostCardToolNames = setOf("present", "miniapp_load", "miniapp_register", "ask_user_question")

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

