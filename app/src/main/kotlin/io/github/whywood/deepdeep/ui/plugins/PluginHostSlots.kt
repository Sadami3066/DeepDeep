package io.github.whywood.deepdeep.ui.plugins

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.whywood.deepdeep.plugin.api.CustomMessageSlotState
import io.github.whywood.deepdeep.plugin.api.DetailSlotState
import io.github.whywood.deepdeep.plugin.api.PluginPageState
import io.github.whywood.deepdeep.plugin.api.PluginStatus
import io.github.whywood.deepdeep.plugin.api.SetupSlotState
import io.github.whywood.deepdeep.plugin.api.SlotIds
import io.github.whywood.deepdeep.plugin.api.ToolCardSlotState
import io.github.whywood.deepdeep.plugin.host.ui.PluginSlotHost
import io.github.whywood.deepdeep.plugin.manager.PluginComposerActionUi
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
        state = ToolCardSlotState(callId = callId, toolName = toolName, args = args, result = result),
        failureGuard = manager.slotFailureGuard(),
        fallback = fallback,
    )
}

@Composable
internal fun PluginSetupContentSlot(
    interaction: io.github.whywood.deepdeep.plugin.manager.PluginInteraction,
    failureGuard: io.github.whywood.deepdeep.plugin.host.PluginFailureGuard? = null,
    fallback: @Composable () -> Unit,
) {
    val manager = LocalPluginManager.current ?: run { fallback(); return }
    val uiState by manager.state.collectAsStateWithLifecycle()
    val entries = remember(uiState, interaction.pluginId) {
        manager.slotEntries(SlotIds.setupContent()).filter { it.pluginId == interaction.pluginId }
    }
    PluginSlotHost(
        entries = entries,
        state = SetupSlotState(
            title = io.github.whywood.deepdeep.plugin.api.LocalizedText.Literal(interaction.stageTitle),
            message = interaction.stageMessage?.let { io.github.whywood.deepdeep.plugin.api.LocalizedText.Literal(it) },
            url = interaction.url,
            imagePath = interaction.imagePath,
            progress = interaction.progress,
        ),
        failureGuard = failureGuard ?: manager.slotFailureGuard(),
        fallback = fallback,
    )
}

@Composable
internal fun PluginDetailSectionSlot(
    entries: List<io.github.whywood.deepdeep.plugin.host.ui.SlotRenderEntry>,
    status: io.github.whywood.deepdeep.plugin.manager.PluginStatusUi,
    rows: List<io.github.whywood.deepdeep.plugin.api.PluginDetailRow>,
    failureGuard: io.github.whywood.deepdeep.plugin.host.PluginFailureGuard? = null,
    fallback: @Composable () -> Unit,
) {
    val pluginId = entries.firstOrNull()?.pluginId
    if (pluginId == null) {
        fallback()
        return
    }
    val apiStatus: PluginStatus = when (status) {
        is io.github.whywood.deepdeep.plugin.manager.PluginStatusUi.Ready -> PluginStatus.Ready(status.label, status.account)
        is io.github.whywood.deepdeep.plugin.manager.PluginStatusUi.NotReady -> PluginStatus.NotReady(status.label)
        is io.github.whywood.deepdeep.plugin.manager.PluginStatusUi.Failed -> PluginStatus.Failed(status.label)
    }
    PluginSlotHost(
        entries = entries,
        state = DetailSlotState(pluginId = pluginId, status = apiStatus, rows = rows),
        failureGuard = failureGuard,
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
        state = CustomMessageSlotState(messageId = messageId, customType = customType, payload = payload),
        failureGuard = manager.slotFailureGuard(),
        fallback = fallback,
    )
}

@Composable
internal fun PluginPageSlot(
    pluginId: String,
    pageId: String,
    fallback: @Composable () -> Unit,
) {
    val manager = LocalPluginManager.current ?: run { fallback(); return }
    val uiState by manager.state.collectAsStateWithLifecycle()
    val entries = remember(uiState, pluginId, pageId) {
        manager.slotEntries(SlotIds.page(pageId)).filter { it.pluginId == pluginId }
    }
    PluginSlotHost(
        entries = entries,
        state = PluginPageState(pluginId = pluginId, pageId = pageId),
        failureGuard = manager.slotFailureGuard(),
        fallback = fallback,
    )
}

@Composable
internal fun pluginComposerActions(): List<PluginComposerActionUi> {
    val manager = LocalPluginManager.current ?: return emptyList()
    val uiState by manager.state.collectAsStateWithLifecycle()
    return remember(uiState) { manager.composerActions() }
}
