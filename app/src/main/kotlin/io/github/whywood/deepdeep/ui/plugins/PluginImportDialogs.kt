package io.github.whywood.deepdeep.ui.plugins

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import io.github.whywood.deepdeep.design.DdSpacing

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
                        modifier = Modifier.padding(horizontal = DdSpacing.Xs, vertical = DdSpacing.Sm),
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
                        DdHairline()
                        DdListRow(
                            title = stringResource(R.string.plugins_import_provides),
                            value = providedSummary(inspection),
                        )
                        if (inspection.capabilities.isNotEmpty()) {
                            DdHairline()
                            DdListRow(
                                title = stringResource(R.string.plugins_import_capabilities),
                                value = capabilitySummary(inspection.capabilities),
                            )
                        }
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
                                        modifier = Modifier.padding(start = DdSpacing.Sm),
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
                            modifier = Modifier.padding(horizontal = DdSpacing.Xs),
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
