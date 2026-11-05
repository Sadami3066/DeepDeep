package io.github.whywood.deepdeep.ui.plugins

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdDialog
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdSwitch
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.plugin.manager.ExternalInspectionUi
import io.github.whywood.deepdeep.plugin.manager.PluginUpdateUi

@Composable
internal fun PluginImportDialog(
    inspection: ExternalInspectionUi,
    trustPublisher: Boolean,
    busy: Boolean,
    onTrustChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = deepDeepColors()
    val context = LocalContext.current
    DdDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.plugins_import_title),
        content = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (inspection.error != null) {
                    Text(
                        text = inspection.error,
                        style = DdType.bodySmall,
                        color = colors.danger,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                    )
                } else {
                    PluginGroup {
                        DdListRow(title = inspection.name)
                        DdHairline()
                        DdListRow(
                            title = stringResource(R.string.plugins_detail_version),
                            value = inspection.version,
                        )
                        DdHairline()
                        DdListRow(
                            title = stringResource(R.string.plugins_detail_size),
                            value = formatPluginSize(context, inspection.size),
                        )
                        DdHairline()
                        DdListRow(
                            title = stringResource(R.string.plugins_import_sha256),
                            value = inspection.sha256.take(16),
                        )
                        DdHairline()
                        DdListRow(
                            title = stringResource(R.string.plugins_detail_publisher),
                            value = inspection.publisherName.ifBlank { inspection.publisherKeyId.take(12) },
                            subtitle = inspection.publisherKeyId,
                        )
                        if (inspection.requiresPrivilege) {
                            DdHairline()
                            DdListRow(title = stringResource(R.string.plugins_import_requires_privilege))
                        }
                        DdHairline()
                        DdListRow(
                            title = stringResource(R.string.plugins_import_provides),
                            value = providedSummary(inspection),
                        )
                    }
                    if (!inspection.trusted) {
                        Spacer(Modifier.height(10.dp))
                        PluginGroup {
                            DdListRow(
                                title = stringResource(R.string.plugins_import_trust_publisher),
                                trailing = {
                                    DdSwitch(
                                        checked = trustPublisher,
                                        onCheckedChange = onTrustChange,
                                        modifier = Modifier.padding(start = 8.dp),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        },
        actions = {
            DdTextAction(
                text = stringResource(R.string.plugins_cancel),
                onClick = onDismiss,
                color = colors.inkSecondary,
            )
            if (inspection.error == null) {
                DdTextAction(
                    text = stringResource(R.string.plugins_import_confirm),
                    onClick = onConfirm,
                    enabled = !busy && (inspection.trusted || trustPublisher),
                )
            }
        },
    )
}

@Composable
internal fun PluginUpdatesDialog(
    updates: List<PluginUpdateUi>,
    onUpdate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = deepDeepColors()
    val context = LocalContext.current
    DdDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.plugins_updates_title),
        content = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (updates.isEmpty()) {
                    Text(
                        text = stringResource(R.string.plugins_updates_empty),
                        style = DdType.bodySmall,
                        color = colors.inkSecondary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                    )
                } else {
                    PluginGroup {
                        updates.forEachIndexed { index, update ->
                            if (index > 0) DdHairline()
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(update.name, style = DdType.body, color = colors.ink)
                                    Text(
                                        text = stringResource(
                                            R.string.plugins_updates_item,
                                            update.installedVersion,
                                            update.availableVersion,
                                        ),
                                        style = DdType.bodySmall,
                                        color = colors.inkTertiary,
                                    )
                                }
                                Text(
                                    text = formatPluginSize(context, update.size),
                                    style = DdType.bodySmall,
                                    color = colors.inkTertiary,
                                )
                                DdTextAction(
                                    text = stringResource(R.string.plugins_updates_install),
                                    onClick = { onUpdate(update.pluginId) },
                                    color = colors.accent,
                                )
                            }
                        }
                    }
                }
            }
        },
        actions = {
            DdTextAction(
                text = stringResource(R.string.plugins_close),
                onClick = onDismiss,
                color = colors.accent,
            )
        },
    )
}

@Composable
internal fun PluginImportResultDialog(
    restartRequired: Boolean,
    onDismiss: () -> Unit,
) {
    val colors = deepDeepColors()
    DdDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.plugins_import_success),
        content = {
            Text(
                text = stringResource(
                    if (restartRequired) R.string.plugins_import_restart else R.string.plugins_import_done,
                ),
                style = DdType.bodySmall,
                color = colors.inkSecondary,
            )
        },
        actions = {
            DdTextAction(
                text = stringResource(R.string.plugins_close),
                onClick = onDismiss,
                color = colors.accent,
            )
        },
    )
}

private fun providedSummary(inspection: ExternalInspectionUi): String = buildString {
    val provides = inspection.provides
    if (provides.tools.isNotEmpty()) append(provides.tools.joinToString(", "))
    if (provides.skills.isNotEmpty()) {
        if (isNotEmpty()) append(" · ")
        append(provides.skills.joinToString(", "))
    }
    if (isEmpty()) append("-")
}
