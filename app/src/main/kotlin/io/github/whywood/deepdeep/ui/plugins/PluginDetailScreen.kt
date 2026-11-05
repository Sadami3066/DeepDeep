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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdDialog
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdPrimaryButton
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.plugin.api.PluginPage
import io.github.whywood.deepdeep.plugin.api.PluginSource
import io.github.whywood.deepdeep.plugin.api.SlotIds
import io.github.whywood.deepdeep.plugin.manager.PluginAvailabilityUi
import io.github.whywood.deepdeep.plugin.manager.PluginManagerFacade
import io.github.whywood.deepdeep.plugin.manager.PluginPhaseUi
import io.github.whywood.deepdeep.plugin.manager.PluginSnapshot
import io.github.whywood.deepdeep.plugin.manager.PluginStatusUi
import java.io.File

@Composable
internal fun PluginDetailScreen(
    manager: PluginManagerFacade,
    pluginId: String,
    onBack: () -> Unit,
    onOpenPermission: () -> Unit,
    onOpenPluginPage: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    pluginsRoot: File? = null,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resolvedPluginsRoot = pluginsRoot ?: remember(context) { File(context.filesDir, "plugins") }
    val holder = remember(manager) { PluginsStateHolder(manager, scope) }
    val state by holder.state.collectAsStateWithLifecycle()
    var deleteOpen by remember { mutableStateOf(false) }
    val snapshot = state.snapshot(pluginId)

    LaunchedEffect(holder) { holder.load() }
    LifecycleResumeEffect(holder) {
        holder.load()
        onPauseOrDispose { }
    }
    LaunchedEffect(state.loading, snapshot) {
        if (!state.loading && snapshot == null) onBack()
    }

    val current = snapshot ?: return
    val pages = manager.pagesOf(pluginId)
    val hasDetailSlot = manager.slotEntries(SlotIds.detailSection(pluginId)).isNotEmpty()
    PluginDetailContent(
        snapshot = current,
        state = state,
        pluginsRoot = resolvedPluginsRoot,
        pages = pages,
        hasDetailSlot = hasDetailSlot,
        deleteOpen = deleteOpen,
        onDeleteOpenChange = { deleteOpen = it },
        onBack = onBack,
        onOpenPermission = onOpenPermission,
        onOpenPluginPage = onOpenPluginPage,
        onRefresh = holder::refresh,
        onInstall = { holder.install(pluginId) },
        onUpdate = { holder.update(pluginId) },
        onUninstall = { holder.uninstall(pluginId) },
        onRunAction = { actionId -> holder.runAction(pluginId, actionId) },
        onSlotAction = { actionId ->
            if (current.actions.any { it.id == actionId }) holder.runAction(pluginId, actionId)
        },
        onSetting = { key, value -> holder.setSetting(pluginId, key, value) },
        modifier = modifier,
    )
}

@Composable
private fun PluginDetailContent(
    snapshot: PluginSnapshot,
    state: PluginsUiState,
    pluginsRoot: File?,
    pages: List<PluginPage>,
    hasDetailSlot: Boolean,
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
    onSlotAction: (String) -> Unit,
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

            if (hasDetailSlot) {
                Spacer(Modifier.height(18.dp))
                PluginDetailSectionSlot(
                    pluginId = snapshot.id,
                    status = snapshot.status ?: PluginStatusUi.NotReady(phaseLabel),
                    rows = snapshot.detailRows,
                    onAction = onSlotAction,
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

            snapshot.lastError?.let { error ->
                Spacer(Modifier.height(14.dp))
                Text(
                    text = error,
                    modifier = Modifier.padding(horizontal = 28.dp).testTag("plugin_detail_last_error"),
                    style = DdType.bodySmall,
                    color = colors.danger,
                )
            }

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
                when {
                    !snapshot.installable -> Unit

                    !snapshot.installed -> {
                        DdPrimaryButton(
                            text = stringResource(R.string.plugins_action_download),
                            onClick = onInstall,
                            modifier = Modifier.fillMaxWidth().testTag("plugin_detail_download"),
                            enabled = availability is PluginAvailabilityUi.Available && !busy,
                        )
                    }

                    else -> {
                        if (snapshot.updateAvailable) {
                            DdPrimaryButton(
                                text = stringResource(R.string.plugins_action_update),
                                onClick = onUpdate,
                                modifier = Modifier.fillMaxWidth().testTag("plugin_detail_update"),
                                enabled = !busy,
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                        PluginGroup {
                            DdListRow(
                                title = stringResource(R.string.plugins_action_reinstall),
                                onClick = { if (!busy) onUpdate() },
                                enabled = !busy,
                                modifier = Modifier.testTag("plugin_detail_reinstall"),
                            )
                            DdHairline()
                            DdListRow(
                                title = stringResource(R.string.plugins_action_delete),
                                onClick = { onDeleteOpenChange(true) },
                                enabled = !busy,
                                modifier = Modifier.testTag("plugin_detail_delete"),
                            )
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
