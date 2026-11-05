package io.github.whywood.deepdeep.plugin.host.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.plugin.api.LocalizedText

internal class PluginComposerActionResolver(private val source: PluginUiCatalogProvider) {
    fun actions(includeUnavailable: Boolean = false): List<PluginComposerActionRef> =
        source.current().composerActionsFor(includeUnavailable)

    fun actionsOf(pluginId: String, includeUnavailable: Boolean = false): List<PluginComposerActionRef> =
        source.current().plugin(pluginId)?.composerActions.orEmpty()
            .filter { includeUnavailable || it.available }
}

@Composable
internal fun ComposerActionHost(
    actions: List<PluginComposerActionRef>,
    text: (LocalizedText) -> String,
    onAction: (pluginId: String, actionId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val available = actions.filter { it.available }
    if (available.isEmpty()) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        available.forEach { entry ->
            ComposerActionChip(entry = entry, text = text, onAction = onAction)
        }
    }
}

@Composable
internal fun ComposerActionBar(
    actions: List<PluginComposerActionRef>,
    text: (LocalizedText) -> String,
    onAction: (pluginId: String, actionId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ComposerActionHost(actions = actions, text = text, onAction = onAction, modifier = modifier)
}

@Composable
private fun ComposerActionChip(
    entry: PluginComposerActionRef,
    text: (LocalizedText) -> String,
    onAction: (pluginId: String, actionId: String) -> Unit,
) {
    val colors = deepDeepColors()
    val label = text(entry.action.label)
    DdPressable(
        onClick = { onAction(entry.pluginId, entry.action.id) },
        modifier = Modifier.heightIn(min = 32.dp),
        shape = CircleShape,
        background = colors.wash,
        contentDescription = label,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PluginIconHost(
                icon = entry.action.icon,
                assetsDir = entry.assetsDir,
                tint = colors.inkSecondary,
                size = 16.dp,
            )
            Text(text = label, style = DdType.label, color = colors.ink, maxLines = 1)
        }
    }
}
