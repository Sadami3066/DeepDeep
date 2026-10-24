package io.github.whywood.deepdeep.ui.plugins

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdDialog
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
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
    var deleteTarget by remember { mutableStateOf<PluginSnapshot?>(null) }

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
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.plugins_intro),
                style = DdType.bodySmall,
                color = colors.inkTertiary,
            )
            Spacer(Modifier.height(10.dp))
            DdTextAction(
                text = stringResource(R.string.plugins_refresh),
                onClick = { manager.refresh() },
                modifier = Modifier.testTag("plugins_refresh"),
            )
            Spacer(Modifier.height(14.dp))
            SettingsGroup {
                state.plugins.forEachIndexed { index, snapshot ->
                    if (index > 0) DdHairline()
                    PluginRow(
                        snapshot = snapshot,
                        operation = state.operation?.takeIf { it.pluginId == snapshot.plugin.id },
                        busy = state.busy || state.interaction != null,
                        onOpen = { onOpenPlugin(snapshot.plugin.id) },
                        onDownload = { manager.install(snapshot.plugin.id) },
                        onDelete = { deleteTarget = snapshot },
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

    deleteTarget?.let { snapshot ->
        DdDialog(
            onDismissRequest = { deleteTarget = null },
            title = stringResource(R.string.plugins_delete_title),
            content = {
                Text(
                    text = stringResource(
                        R.string.plugins_delete_message,
                        stringResource(snapshot.plugin.nameRes),
                    ),
                    style = DdType.bodySmall,
                    color = colors.inkSecondary,
                )
            },
            actions = {
                DdTextAction(
                    text = stringResource(R.string.plugins_cancel),
                    onClick = { deleteTarget = null },
                    color = colors.inkSecondary,
                )
                DdTextAction(
                    text = stringResource(R.string.plugins_delete_confirm),
                    onClick = {
                        deleteTarget = null
                        manager.uninstall(snapshot.plugin.id)
                    },
                    color = colors.danger,
                )
            },
        )
    }
}

@Composable
private fun PluginRow(
    snapshot: PluginSnapshot,
    operation: PluginOperation?,
    busy: Boolean,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = deepDeepColors()
    val plugin = snapshot.plugin
    val name = stringResource(plugin.nameRes)
    val summary = stringResource(plugin.summaryRes)
    val status = when {
        operation != null -> operation.stage
            ?: operation.progress?.let { stringResource(R.string.plugins_downloading_percent, it) }
            ?: stringResource(R.string.plugins_stage_preparing)

        !snapshot.installed -> when (val availability = snapshot.availability) {
            is PluginAvailability.Unavailable -> availability.reason
            PluginAvailability.Available -> stringResource(R.string.plugins_status_not_installed)
        }

        else -> snapshot.status?.label ?: stringResource(R.string.plugins_status_installed)
    }
    DdListRow(
        title = name,
        subtitle = stringResource(R.string.plugins_row_subtitle, summary, status),
        subtitleMaxLines = 2,
        onClick = if (snapshot.installed) onOpen else null,
        showChevron = snapshot.installed,
        modifier = Modifier.testTag("plugin_row_${plugin.id}"),
        trailing = {
            when {
                operation != null -> DdSpinner(size = 16.dp, strokeWidth = 2.dp)
                !snapshot.installed -> DdTextAction(
                    text = stringResource(R.string.plugins_action_download),
                    onClick = onDownload,
                    enabled = !busy && snapshot.availability is PluginAvailability.Available,
                    modifier = Modifier.testTag("plugin_download_${plugin.id}"),
                )

                else -> DdIconButton(
                    icon = DdIcons.Trash,
                    onClick = onDelete,
                    contentDescription = stringResource(R.string.plugins_action_delete),
                    modifier = Modifier.testTag("plugin_delete_${plugin.id}"),
                    size = 36.dp,
                    iconSize = 17.dp,
                    tint = colors.inkTertiary,
                    enabled = !busy,
                )
            }
        },
    )
}
