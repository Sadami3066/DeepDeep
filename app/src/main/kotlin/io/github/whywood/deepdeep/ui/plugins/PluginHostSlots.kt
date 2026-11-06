package io.github.whywood.deepdeep.ui.plugins

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.whywood.deepdeep.plugin.api.PluginPageState
import io.github.whywood.deepdeep.plugin.api.SlotIds
import io.github.whywood.deepdeep.plugin.host.ui.PluginSlotHost
import io.github.whywood.deepdeep.plugin.manager.PluginComposerActionUi
import io.github.whywood.deepdeep.plugin.manager.PluginManagerFacade
import io.github.whywood.deepdeep.ui.common.chat.LocalPluginManager

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
internal fun pluginComposerActions(manager: PluginManagerFacade): List<PluginComposerActionUi> {
    val uiState by manager.state.collectAsStateWithLifecycle()
    return remember(uiState) { manager.composerActions() }
}
