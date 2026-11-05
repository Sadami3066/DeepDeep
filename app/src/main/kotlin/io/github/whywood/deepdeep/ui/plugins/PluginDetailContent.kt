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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import io.github.whywood.deepdeep.plugin.api.PluginPage
import io.github.whywood.deepdeep.plugin.api.PluginSource
import io.github.whywood.deepdeep.plugin.manager.PluginAvailabilityUi
import io.github.whywood.deepdeep.plugin.manager.PluginPhaseUi
import io.github.whywood.deepdeep.plugin.manager.PluginSnapshot
import io.github.whywood.deepdeep.plugin.manager.PluginStatusUi
import io.github.whywood.deepdeep.plugin.manager.PluginsUiState
import java.io.File

@Composable
internal fun PluginDetailContent(
    snapshot: PluginSnapshot,
    state: PluginsUiState,
    pluginsRoot: File?,
    pages: List<PluginPage>,
    slotEntries: List<io.github.whywood.deepdeep.plugin.host.ui.SlotRenderEntry>,
    detailSections: List<PluginDetailSectionEntries>,
    failureGuard: io.github.whywood.deepdeep.plugin.host.PluginFailureGuard,
    showConnect: Boolean,
    onConnect: () -> Unit,
    recoveryBusy: Boolean,
    recoveryError: String?,
    onRetryActivation: () -> Unit,
    onRollback: () -> Unit,
    deleteOpen: Boolean,
    onDeleteOpenChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onOpenPermission: () -> Unit,
    onOpenPluginPage: (String) -> Unit,
    onRefresh: () -> Unit,
    onInstall: () -> Unit,
    onUpdate: () -> Unit,
    onUninstall: () -> Unit,
    onRunAction: (String) -> Unit,
    onSetting: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val context = LocalContext.current
    val resolver = rememberPluginTextResolver()
    val operation = state.operation?.takeIf { it.pluginId == snapshot.id }
    val busy = state.busy || state.interaction != null
    val availability = snapshot.availability
    val phaseLabel = stringResource(pluginPhaseLabelRes(snapshot.phase))
    val statusLabel = when {
        operation != null -> operation.stage
            ?: operation.progress?.let { stringResource(R.string.plugins_downloading_percent, it) }
            ?: stringResource(R.string.plugins_stage_preparing)

        snapshot.status != null -> snapshot.status.label
        snapshot.installed -> phaseLabel
        else -> stringResource(R.string.plugins_status_not_installed)
    }
    val sizeValue = when {
        snapshot.installedSizeBytes != null -> formatPluginSize(context, snapshot.installedSizeBytes)
        snapshot.sizeLabel != null -> snapshot.sizeLabel
        else -> stringResource(R.string.plugins_size_builtin)
    }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("plugin_detail_${snapshot.id}"),
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
            PluginIcon(
                icon = snapshot.icon,
                assetsDir = pluginAssetsDir(pluginsRoot, snapshot),
                tint = colors.ink,
                size = 22.dp,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = snapshot.name,
                modifier = Modifier.weight(1f),
                style = DdType.heading,
                color = colors.ink,
                maxLines = 1,
            )
            if (operation != null) {
                DdSpinner(size = 18.dp, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            DdIconButton(
                icon = DdIcons.Refresh,
                onClick = onRefresh,
                contentDescription = stringResource(R.string.plugins_refresh),
                modifier = Modifier.testTag("plugin_detail_refresh"),
                tint = colors.ink,
                iconSize = 21.dp,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(Modifier.padding(horizontal = 24.dp)) {
                Text(
                    text = snapshot.summary,
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                )
                snapshot.description?.let { description ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = description,
                        style = DdType.bodySmall,
                        color = colors.inkTertiary,
                    )
                }
                Spacer(Modifier.height(18.dp))
                PluginGroup {
                    DdListRow(title = stringResource(R.string.plugins_detail_status), value = statusLabel)
                    DdHairline()
                    DdListRow(title = stringResource(R.string.plugins_detail_phase), value = phaseLabel)
                    DdHairline()
                    DdListRow(title = stringResource(R.string.plugins_detail_version), value = snapshot.version)
                    if (snapshot.installedVersion != null && snapshot.installedVersion != snapshot.version) {
                        DdHairline()
                        DdListRow(
                            title = stringResource(R.string.plugins_detail_installed_version),
                            value = snapshot.installedVersion,
                        )
                    }
                    DdHairline()
                    DdListRow(title = stringResource(R.string.plugins_detail_size), value = sizeValue)
                    DdHairline()
                    DdListRow(
                        title = stringResource(R.string.plugins_detail_source),
                        value = stringResource(
                            if (snapshot.source == PluginSource.EXTERNAL) {
                                R.string.plugins_source_external
                            } else {
                                R.string.plugins_source_builtin
                            },
                        ),
                    )
                    if (snapshot.requiresPrivilege) {
                        DdHairline()
                        DdListRow(title = stringResource(R.string.plugins_detail_requires_privilege))
                    }
                    snapshot.publisher?.let { publisher ->
                        if (publisher.keyId.isNotBlank()) {
                            DdHairline()
                            DdListRow(
                                title = stringResource(R.string.plugins_detail_publisher),
                                value = publisher.name.ifBlank { publisher.keyId.take(12) },
                                subtitle = publisher.keyId,
                            )
                        }
                    }
                    if (snapshot.skills.isNotEmpty()) {
                        DdHairline()
                        DdListRow(
                            title = stringResource(R.string.plugins_detail_skills),
                            value = snapshot.skills.joinToString("、"),
                        )
                    }
                    if (snapshot.phase == PluginPhaseUi.PENDING_RESTART) {
                        DdHairline()
                        DdListRow(
                            title = stringResource(R.string.plugins_phase_pending_restart),
                            subtitle = stringResource(R.string.plugins_pending_restart_notice),
                        )
                    }
                }
            }

            if (snapshot.detailRows.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Column(Modifier.padding(horizontal = 24.dp)) {
                    PluginGroup {
                        snapshot.detailRows.forEachIndexed { index, row ->
                            if (index > 0) DdHairline()
                            DdListRow(
                                title = resolver.resolve(row.label),
                                value = row.value,
                            )
                        }
                    }
                }
            }

            val availabilityReason = (availability as? PluginAvailabilityUi.Unavailable)?.reason
            if (availabilityReason != null) {
                Spacer(Modifier.height(14.dp))
                Column(Modifier.padding(horizontal = 24.dp)) {
                    Text(
                        text = availabilityReason,
                        modifier = Modifier.padding(horizontal = 4.dp),
                        style = DdType.bodySmall,
                        color = colors.inkTertiary,
                    )
                    if (snapshot.requiresPrivilege) {
                        Spacer(Modifier.height(8.dp))
                        PluginGroup {
                            DdListRow(
                                title = stringResource(R.string.plugin_screen_open_permission),
                                onClick = onOpenPermission,
                                showChevron = true,
                                modifier = Modifier.testTag("plugin_permission_guide"),
                            )
                        }
                    }
                }
            }

            if (showConnect) {
                Spacer(Modifier.height(18.dp))
                Column(Modifier.padding(horizontal = 24.dp)) {
                    PluginGroup {
                        DdListRow(
                            title = stringResource(R.string.plugins_connect),
                            onClick = { if (!busy) onConnect() },
                            enabled = !busy,
                            showChevron = true,
                            modifier = Modifier.testTag("plugin_connect"),
                        )
                    }
                }
            }

            if (snapshot.settings.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                PluginSettingsSection(
                    settings = snapshot.settings,
                    values = snapshot.settingsValues,
                    enabled = !busy && snapshot.enabled,
                    resolver = resolver,
                    onSetting = onSetting,
                )
            }

            detailSections.forEachIndexed { index, section ->
                Spacer(Modifier.height(if (index == 0) 18.dp else 12.dp))
                PluginDetailSectionSlot(
                    entries = section.entries,
                    status = snapshot.status ?: PluginStatusUi.NotReady(phaseLabel),
                    rows = snapshot.detailRows,
                    failureGuard = failureGuard,
                ) { }
            }

            if (pages.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Column(Modifier.padding(horizontal = 24.dp)) {
                    PluginGroup {
                        DdListRow(
                            title = stringResource(R.string.plugins_open_page),
                            onClick = { onOpenPluginPage(pages.first().id) },
                            showChevron = true,
                            modifier = Modifier.testTag("plugin_open_page"),
                        )
                    }
                }
            }

            PluginSlotIssuesSection(
                pluginId = snapshot.id,
                entries = slotEntries,
                failureGuard = failureGuard,
            )

            val showRecovery = showsRecoverySection(snapshot.phase, snapshot.lastError)
            if (showRecovery) {
                Spacer(Modifier.height(18.dp))
                PluginRecoverySection(
                    lastError = snapshot.lastError.orEmpty(),
                    previousVersion = rollbackTargetVersion(snapshot.source, snapshot.previousVersion),
                    canRetryActivation = supportsActivationRecovery(snapshot.source),
                    busy = recoveryBusy,
                    error = recoveryError,
                    onRetryActivation = onRetryActivation,
                    onRollback = onRollback,
                )
            } else {
                snapshot.lastError?.let { error ->
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = error,
                        modifier = Modifier.padding(horizontal = 28.dp).testTag("plugin_detail_last_error"),
                        style = DdType.bodySmall,
                        color = colors.danger,
                    )
                }
            }

            PluginDetailFooter(
                snapshot = snapshot,
                state = state,
                busy = busy,
                availability = availability,
                onInstall = onInstall,
                onUpdate = onUpdate,
                onUninstall = onUninstall,
                onRunAction = onRunAction,
                onRefresh = onRefresh,
                onDeleteOpenChange = onDeleteOpenChange,
            )
        }
    }

    if (deleteOpen) {
        DdDialog(
            onDismissRequest = { onDeleteOpenChange(false) },
            title = stringResource(R.string.plugins_delete_title),
            content = {
                Text(
                    text = stringResource(R.string.plugins_delete_message, snapshot.name),
                    style = DdType.bodySmall,
                    color = colors.inkSecondary,
                )
            },
            actions = {
                DdTextAction(
                    text = stringResource(R.string.plugins_cancel),
                    onClick = { onDeleteOpenChange(false) },
                    color = colors.inkSecondary,
                )
                DdTextAction(
                    text = stringResource(R.string.plugins_delete_confirm),
                    onClick = {
                        onDeleteOpenChange(false)
                        onUninstall()
                    },
                    color = colors.danger,
                )
            },
        )
    }
}

