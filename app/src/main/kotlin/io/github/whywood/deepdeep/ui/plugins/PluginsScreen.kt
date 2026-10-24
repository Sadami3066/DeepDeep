package io.github.whywood.deepdeep.ui.plugins

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdSwitch
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.LocalDdRowHorizontalPadding
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.plugin.PluginAvailability
import io.github.whywood.deepdeep.plugin.PluginManager
import io.github.whywood.deepdeep.plugin.PluginOperation
import io.github.whywood.deepdeep.plugin.PluginSnapshot
import io.github.whywood.deepdeep.ui.settings.SettingsGroup

@Composable
fun PluginsScreen(
    manager: PluginManager,
    onBack: () -> Unit,
    onOpenPlugin: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val state by manager.state.collectAsState()
    val interactive = !state.busy && state.interaction == null

    LaunchedEffect(Unit) { manager.refresh() }
    LifecycleResumeEffect(Unit) {
        manager.refreshIfStale(minIntervalMillis = 0)
        onPauseOrDispose { }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("plugins_screen"),
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
                text = stringResource(R.string.plugins_title),
                modifier = Modifier.weight(1f),
                style = DdType.heading,
                color = colors.ink,
            )
            DdIconButton(
                icon = DdIcons.Refresh,
                onClick = { manager.refresh() },
                contentDescription = stringResource(R.string.plugins_refresh),
                modifier = Modifier.testTag("plugins_refresh"),
                tint = colors.ink,
                iconSize = 21.dp,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            SettingsGroup {
                state.plugins.forEachIndexed { index, snapshot ->
                    if (index > 0) DdHairline()
                    PluginRow(
                        snapshot = snapshot,
                        operation = state.operation?.takeIf { it.pluginId == snapshot.plugin.id },
                        interactive = interactive,
                        onOpen = { onOpenPlugin(snapshot.plugin.id) },
                        onDownload = { manager.install(snapshot.plugin.id) },
                        onToggle = { enabled -> manager.setEnabled(snapshot.plugin.id, enabled) },
                    )
                }
            }
            state.error?.let { error ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = error,
                    modifier = Modifier.padding(horizontal = 4.dp).testTag("plugins_error"),
                    style = DdType.bodySmall,
                    color = colors.danger,
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PluginRow(
    snapshot: PluginSnapshot,
    operation: PluginOperation?,
    interactive: Boolean,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val colors = deepDeepColors()
    val plugin = snapshot.plugin
    val name = stringResource(plugin.nameRes)
    val stage = operation?.let {
        it.stage
            ?: it.progress?.let { percent -> stringResource(R.string.plugins_downloading_percent, percent) }
            ?: stringResource(R.string.plugins_stage_preparing)
    }
    val enabled = interactive && operation == null
    DdPressable(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().testTag("plugin_row_${plugin.id}"),
        shape = RoundedCornerShape(20.dp),
        enabled = snapshot.installed,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(
                    horizontal = LocalDdRowHorizontalPadding.current,
                    vertical = 12.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (plugin.tintIcon) {
                DdIcon(icon = plugin.icon, tint = colors.ink, size = 24.dp)
            } else {
                Image(
                    painter = painterResource(plugin.icon.res),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    contentScale = ContentScale.Fit,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = DdType.body,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = stage ?: stringResource(plugin.summaryRes),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                if (snapshot.installed) {
                    DdSwitch(
                        checked = snapshot.enabled,
                        onCheckedChange = onToggle,
                        enabled = enabled,
                        stateDescription = stringResource(
                            if (snapshot.enabled) R.string.plugins_enable else R.string.plugins_disable,
                        ),
                        modifier = Modifier
                            .testTag("plugin_switch_${plugin.id}")
                            .semantics { contentDescription = name },
                    )
                } else {
                    DdTextAction(
                        text = stringResource(R.string.plugins_action_download),
                        onClick = onDownload,
                        enabled = enabled && snapshot.availability is PluginAvailability.Available,
                        modifier = Modifier.testTag("plugin_download_${plugin.id}"),
                    )
                }
            }
        }
    }
}
