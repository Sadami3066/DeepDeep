package io.github.whywood.deepdeep.ui.navigation

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.plugin.manager.PluginComposerActionUi
import io.github.whywood.deepdeep.ui.plugins.LocalPluginManager
import io.github.whywood.deepdeep.ui.plugins.PluginIcon
import io.github.whywood.deepdeep.ui.plugins.pluginComposerActions
import io.github.whywood.deepdeep.ui.plugins.rememberPluginTextResolver

@Composable
internal fun PluginComposerActionRow(modifier: Modifier = Modifier) {
    val manager = LocalPluginManager.current ?: return
    val actions = pluginComposerActions()
    if (actions.isEmpty()) return
    val textResolver = rememberPluginTextResolver()
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEach { entry ->
            PluginComposerActionChip(
                entry = entry,
                label = textResolver.resolve(entry.action.label),
                onClick = { manager.runAction(entry.pluginId, entry.action.id) },
            )
        }
    }
}

@Composable
private fun PluginComposerActionChip(
    entry: PluginComposerActionUi,
    label: String,
    onClick: () -> Unit,
) {
    val colors = deepDeepColors()
    DdPressable(
        onClick = onClick,
        modifier = Modifier.height(32.dp).testTag("plugin_composer_action"),
        shape = RoundedCornerShape(10.dp),
        contentDescription = label,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PluginIcon(
                icon = entry.action.icon,
                assetsDir = null,
                tint = colors.inkSecondary,
                size = 15.dp,
            )
            Text(text = label, style = DdType.label, color = colors.ink, maxLines = 1)
        }
    }
}
