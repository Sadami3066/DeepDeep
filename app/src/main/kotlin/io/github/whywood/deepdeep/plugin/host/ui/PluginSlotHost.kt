package io.github.whywood.deepdeep.plugin.host.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import io.github.whywood.deepdeep.plugin.api.SlotState
import io.github.whywood.deepdeep.plugin.host.PluginFailureGuard

internal fun slotGuardKey(pluginId: String, manifestVersion: String, slotId: String): String =
    "$pluginId@$manifestVersion:$slotId"

@Composable
internal fun PluginSlotHost(
    entries: List<SlotRenderEntry>,
    state: SlotState,
    failureGuard: PluginFailureGuard? = null,
    fallback: @Composable () -> Unit,
) {
    val entry = entries.firstOrNull { it.composeCompatible } ?: run {
        fallback()
        return
    }

    val guardKey = slotGuardKey(entry.pluginId, entry.manifestVersion, entry.slotId)
    val crashed = remember(guardKey) { failureGuard?.detect(guardKey) != null }
    if (crashed) {
        fallback()
        return
    }

    if (failureGuard != null) {
        DisposableEffect(guardKey) {
            failureGuard.mark(guardKey, "slot ${entry.slotId}")
            onDispose { failureGuard.clear(guardKey) }
        }
    }

    key(entry.pluginId, entry.slotId) {
        entry.content.render(state)
    }
}
