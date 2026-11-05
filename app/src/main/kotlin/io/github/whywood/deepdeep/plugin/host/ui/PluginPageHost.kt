package io.github.whywood.deepdeep.plugin.host.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.whywood.deepdeep.plugin.api.PluginPageState
import io.github.whywood.deepdeep.plugin.host.PluginFailureGuard

internal object PluginPageSlots {
    const val PREFIX: String = "plugin.page:"

    fun forPage(pageId: String): String = PREFIX + pageId
}

internal class PluginPageResolver(private val source: PluginUiCatalogProvider) {
    fun pages(pluginId: String, includeUnavailable: Boolean = false): List<PluginPageRef> =
        source.current().pagesOf(pluginId, includeUnavailable)

    fun allPages(includeUnavailable: Boolean = false): List<PluginPageRef> =
        source.current().pages.filter { includeUnavailable || it.available }

    fun resolve(pluginId: String, pageId: String): PluginPageRef? = source.current().page(pluginId, pageId)
}

@Composable
internal fun PluginPageHost(
    page: PluginPageRef,
    registry: SlotRegistry,
    state: PluginPageState,
    modifier: Modifier = Modifier,
    failureGuard: PluginFailureGuard? = null,
    onAction: (String) -> Unit = {},
    fallback: @Composable () -> Unit = {},
) {
    PluginSlotHost(
        slotId = page.slotId,
        registry = registry,
        state = state,
        modifier = modifier,
        failureGuard = failureGuard,
        onAction = onAction,
        fallback = fallback,
    )
}

@Composable
internal fun PluginPageHost(
    page: PluginPageRef,
    registry: SlotRegistry,
    sessionId: String?,
    modifier: Modifier = Modifier,
    failureGuard: PluginFailureGuard? = null,
    onAction: (String) -> Unit = {},
    fallback: @Composable () -> Unit = {},
) {
    PluginPageHost(
        page = page,
        registry = registry,
        state = page.state(sessionId),
        modifier = modifier,
        failureGuard = failureGuard,
        onAction = onAction,
        fallback = fallback,
    )
}
