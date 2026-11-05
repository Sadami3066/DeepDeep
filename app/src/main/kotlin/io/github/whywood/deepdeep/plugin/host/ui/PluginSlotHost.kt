package io.github.whywood.deepdeep.plugin.host.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.whywood.deepdeep.plugin.api.SlotContent
import io.github.whywood.deepdeep.plugin.api.SlotState
import io.github.whywood.deepdeep.plugin.host.PluginFailureGuard

@Composable
internal fun PluginSlotHost(
    entries: List<SlotRenderEntry>,
    state: SlotState,
    modifier: Modifier = Modifier,
    failureGuard: PluginFailureGuard? = null,
    onAction: (String) -> Unit = {},
    fallback: @Composable () -> Unit,
) {
    val entry = entries.firstOrNull { candidate ->
        when (candidate.content) {
            is SlotContent.Declarative -> candidate.uiTreeValid
            is SlotContent.Compose -> candidate.composeCompatible
        }
    } ?: run {
        fallback()
        return
    }

    val guardKey = "${entry.pluginId}@${entry.manifestVersion}:${entry.slotId}"
    val crashed = remember(entry.pluginId, entry.slotId) {
        failureGuard?.detect(guardKey) != null
    }
    if (crashed) {
        fallback()
        return
    }

    remember(entry.pluginId, entry.slotId) {
        failureGuard?.mark(guardKey, "slot ${entry.slotId}")
        true
    }
    LaunchedEffect(entry.pluginId, entry.slotId) {
        failureGuard?.clear(guardKey)
    }

    key(entry.pluginId, entry.slotId) {
        when (val content = entry.content) {
            is SlotContent.Declarative -> DeclarativeUiRenderer(
                node = content.tree,
                assetsDir = entry.assetsDir,
                modifier = modifier,
                onAction = onAction,
            )

            is SlotContent.Compose -> content.render(state)
        }
    }
}

@Composable
internal fun PluginSlotHost(
    slotId: String,
    registry: SlotRegistry,
    state: SlotState,
    modifier: Modifier = Modifier,
    failureGuard: PluginFailureGuard? = null,
    onAction: (String) -> Unit = {},
    fallback: @Composable () -> Unit,
) {
    PluginSlotHost(
        entries = registry.bindings(slotId),
        state = state,
        modifier = modifier,
        failureGuard = failureGuard,
        onAction = onAction,
        fallback = fallback,
    )
}
