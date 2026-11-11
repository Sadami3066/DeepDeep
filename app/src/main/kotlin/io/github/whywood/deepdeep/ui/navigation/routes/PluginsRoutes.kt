package io.github.whywood.deepdeep.ui.navigation.routes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.core.AppDependencies
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.plugin.manager.PluginManagerFacade
import io.github.whywood.deepdeep.ui.navigation.AppDestination
import io.github.whywood.deepdeep.ui.navigation.AppNavigator
import io.github.whywood.deepdeep.ui.plugins.PluginPageHost
import io.github.whywood.deepdeep.ui.plugins.PluginsScreen

@Composable
internal fun PluginsRoute(
    dependencies: AppDependencies,
    navigator: AppNavigator,
    onBack: () -> Unit,
    pendingImportUri: String? = null,
    onPendingImportHandled: () -> Unit = {},
) {
    val hostState by dependencies.hostAccess.state.collectAsStateWithLifecycle()
    PluginsScreen(
        manager = dependencies.pluginManager,
        marketRepository = dependencies.marketRepository,
        hostAccess = hostState.effective,
        onBack = onBack,
        pendingImportUri = pendingImportUri,
        onPendingImportHandled = onPendingImportHandled,
        onOpenPluginPage = { pluginId, pageId ->
            navigator.push(AppDestination.PluginPage(pluginId, pageId))
        },
    )
}

@Composable
internal fun PluginPageRoute(
    destination: AppDestination.PluginPage,
    manager: PluginManagerFacade,
    onBack: () -> Unit,
) {
    val colors = deepDeepColors()
    val uiState by manager.state.collectAsStateWithLifecycle()
    val title = remember(uiState, destination) {
        destination.title.ifBlank {
            manager.pagesOf(destination.pluginId)
                .firstOrNull { it.id == destination.pageId }
                ?.title
                ?.let(manager::resolveText)
                .orEmpty()
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("plugin_page_screen"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIconButton(
                icon = DdIcons.ArrowLeft,
                onClick = onBack,
                contentDescription = stringResource(R.string.plugins_back),
                tint = colors.ink,
                iconSize = 21.dp,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = title.ifBlank { stringResource(R.string.plugins_page_title) },
                modifier = Modifier.weight(1f),
                style = DdType.heading,
                color = colors.ink,
                maxLines = 1,
            )
        }
        PluginPageHost(
            pluginId = destination.pluginId,
            pageId = destination.pageId,
            fallback = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.plugins_page_unavailable),
                        modifier = Modifier.padding(horizontal = 32.dp),
                        style = DdType.bodySmall,
                        color = colors.inkTertiary,
                    )
                }
            },
        )
    }
}
