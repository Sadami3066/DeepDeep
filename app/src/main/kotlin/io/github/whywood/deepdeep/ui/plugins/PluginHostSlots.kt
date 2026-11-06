package io.github.whywood.deepdeep.ui.plugins

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.whywood.deepdeep.plugin.manager.PluginComposerActionUi
import io.github.whywood.deepdeep.plugin.manager.PluginManagerFacade
import io.github.whywood.deepdeep.ui.common.chat.LocalPluginManager

@Composable
internal fun PluginPageHost(
    pluginId: String,
    pageId: String,
    fallback: @Composable () -> Unit,
) {
    val manager = LocalPluginManager.current ?: run {
        fallback()
        return
    }
    val uiState by manager.state.collectAsStateWithLifecycle()
    val page = remember(uiState, pluginId, pageId) {
        manager.pagesOf(pluginId).firstOrNull { it.id == pageId }
    }
    if (page == null) {
        if (!uiState.loading && uiState.snapshot(pluginId) != null) fallback()
        return
    }
    key(pluginId, pageId) {
        page.content()
    }
}

@Composable
internal fun pluginComposerActions(manager: PluginManagerFacade): List<PluginComposerActionUi> {
    val uiState by manager.state.collectAsStateWithLifecycle()
    return remember(uiState) { manager.composerActions() }
}
