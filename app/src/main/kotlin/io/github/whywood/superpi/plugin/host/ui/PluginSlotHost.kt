package io.github.whywood.superpi.plugin.host.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import io.github.whywood.superpi.plugin.api.SlotState

@Composable
internal fun PluginSlotHost(
    entries: List<SlotRenderEntry>,
    state: SlotState,
    fallback: @Composable () -> Unit,
) {
    val entry = entries.firstOrNull { it.composeCompatible } ?: run {
        fallback()
        return
    }

    key(entry.pluginId, entry.slotId) {
        entry.content.render(state)
    }
}
