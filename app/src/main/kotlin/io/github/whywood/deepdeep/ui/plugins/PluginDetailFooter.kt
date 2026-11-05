package io.github.whywood.deepdeep.ui.plugins

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdPrimaryButton
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.plugin.manager.PluginAvailabilityUi
import io.github.whywood.deepdeep.plugin.manager.PluginSnapshot
import io.github.whywood.deepdeep.plugin.manager.PluginsUiState

@Composable
internal fun PluginDetailFooter(
    snapshot: PluginSnapshot,
    state: PluginsUiState,
    busy: Boolean,
    availability: PluginAvailabilityUi,
    onInstall: () -> Unit,
    onUpdate: () -> Unit,
    onUninstall: () -> Unit,
    onRunAction: (String) -> Unit,
    onRefresh: () -> Unit,
    onDeleteOpenChange: (Boolean) -> Unit,
) {
    val colors = deepDeepColors()
    val resolver = rememberPluginTextResolver()
    Spacer(Modifier.height(24.dp))
    if (snapshot.installed && snapshot.enabled) {
        if (snapshot.actions.isNotEmpty()) {
            Column(Modifier.padding(horizontal = 24.dp)) {
                PluginGroup {
                    snapshot.actions.forEachIndexed { index, action ->
                        if (index > 0) DdHairline()
                        DdListRow(
                            title = resolver.resolve(action.label),
                            onClick = { if (!busy) onRunAction(action.id) },
                            enabled = !busy && action.enabled,
                            modifier = Modifier.testTag("plugin_action_${action.id}"),
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    Column(Modifier.padding(horizontal = 24.dp)) {
        if (snapshot.installable) {
            if (!snapshot.installed) {
                DdPrimaryButton(
                    text = stringResource(R.string.plugins_action_download),
                    onClick = onInstall,
                    modifier = Modifier.fillMaxWidth().testTag("plugin_detail_download"),
                    enabled = availability is PluginAvailabilityUi.Available && !busy,
                )
            } else {
                if (snapshot.updateAvailable) {
                    DdPrimaryButton(
                        text = stringResource(R.string.plugins_action_update),
                        onClick = onUpdate,
                        modifier = Modifier.fillMaxWidth().testTag("plugin_detail_update"),
                        enabled = !busy,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                val reinstall = pluginShowsReinstall(snapshot)
                val delete = pluginShowsDelete(snapshot)
                if (reinstall || delete) {
                    PluginGroup {
                        if (reinstall) {
                            DdListRow(
                                title = stringResource(R.string.plugins_action_reinstall),
                                onClick = { if (!busy) onUpdate() },
                                enabled = !busy,
                                modifier = Modifier.testTag("plugin_detail_reinstall"),
                            )
                        }
                        if (reinstall && delete) DdHairline()
                        if (delete) {
                            DdListRow(
                                title = stringResource(R.string.plugins_action_delete),
                                onClick = { onDeleteOpenChange(true) },
                                enabled = !busy,
                                modifier = Modifier.testTag("plugin_detail_delete"),
                            )
                        }
                    }
                }
            }
        }
        state.error?.let { error ->
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = error,
                    modifier = Modifier.weight(1f).testTag("plugin_detail_error"),
                    style = DdType.bodySmall,
                    color = colors.danger,
                )
                DdTextAction(
                    text = stringResource(R.string.assistant_retry),
                    onClick = onRefresh,
                    modifier = Modifier.testTag("plugin_detail_error_retry"),
                )
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}
