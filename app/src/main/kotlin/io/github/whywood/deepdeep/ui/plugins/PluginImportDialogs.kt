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
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdSwitch
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.plugin.manager.ExternalInspectionUi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.whywood.deepdeep.design.DdTextField
import io.github.whywood.deepdeep.plugin.host.update.PluginRepositorySettings
import io.github.whywood.deepdeep.plugin.manager.PluginUpdateUi

@Composable
internal fun PluginImportDialog(
    inspection: ExternalInspectionUi,
    trustPublisher: Boolean,
    busy: Boolean,
    error: String?,
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
                            title = stringResource(R.string.plugins_import_version),
                            value = inspection.version,
                        )
                        DdHairline()
                        DdListRow(
                            title = stringResource(R.string.plugins_import_size),
                            value = formatPluginSize(context, inspection.size),
                        )
                        DdHairline()
                        DdListRow(
                            title = stringResource(R.string.plugins_import_sha256),
                            value = inspection.sha256.take(16),
                        )
                        DdHairline()
                        DdListRow(
                            title = stringResource(R.string.plugins_import_publisher),
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
                    error?.let { message ->
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = message,
                            style = DdType.bodySmall,
                            color = colors.danger,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
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
    busyId: String?,
    stagedIds: Set<String>,
    error: String?,
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
                                when {
                                    update.pluginId in stagedIds -> Text(
                                        text = stringResource(R.string.plugins_update_staged),
                                        style = DdType.bodySmall,
                                        color = colors.inkTertiary,
                                    )

                                    busyId == update.pluginId -> DdSpinner(size = 16.dp, strokeWidth = 2.dp)

                                    else -> DdTextAction(
                                        text = stringResource(R.string.plugins_updates_install),
                                        onClick = { onUpdate(update.pluginId) },
                                        color = colors.accent,
                                    )
                                }
                            }
                        }
                    }
                    error?.let { message ->
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = message,
                            style = DdType.bodySmall,
                            color = colors.danger,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
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
internal fun PluginRepositoryDialog(
    settings: PluginRepositorySettings,
    onSave: (PluginRepositorySettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = deepDeepColors()
    var url by remember(settings) { mutableStateOf(settings.indexUrl.orEmpty()) }
    var autoCheck by remember(settings) { mutableStateOf(settings.autoCheck) }
    DdDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.plugins_source_title),
        content = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.plugins_source_hint),
                    style = DdType.bodySmall,
                    color = colors.inkSecondary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
                DdTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = stringResource(R.string.plugins_source_url),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    testTag = "plugins_source_url",
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.plugins_source_auto_check),
                        modifier = Modifier.weight(1f),
                        style = DdType.body,
                        color = colors.ink,
                    )
                    DdSwitch(checked = autoCheck, onCheckedChange = { autoCheck = it })
                }
            }
        },
        actions = {
            DdTextAction(
                text = stringResource(R.string.plugins_close),
                onClick = onDismiss,
                color = colors.inkSecondary,
            )
            DdTextAction(
                text = stringResource(R.string.plugins_source_save),
                onClick = {
                    onSave(
                        PluginRepositorySettings(
                            indexUrl = url.trim().takeIf { it.isNotEmpty() },
                            autoCheck = autoCheck,
                        ),
                    )
                },
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
    if (provides.pages.isNotEmpty()) {
        if (isNotEmpty()) append(" · ")
        append(provides.pages.joinToString(", "))
    }
    if (isEmpty()) append("-")
}
