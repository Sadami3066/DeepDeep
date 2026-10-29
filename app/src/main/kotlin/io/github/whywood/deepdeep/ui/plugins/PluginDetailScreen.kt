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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdDialog
import io.github.whywood.deepdeep.design.DdDropdown
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdMenuItem
import io.github.whywood.deepdeep.design.DdPrimaryButton
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdSwitch
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.plugin.PluginAvailability
import io.github.whywood.deepdeep.plugin.OfficialPlugin
import io.github.whywood.deepdeep.plugin.PluginDetailRow
import io.github.whywood.deepdeep.plugin.PluginKind
import io.github.whywood.deepdeep.plugin.PluginManager
import io.github.whywood.deepdeep.plugin.PluginSettingSpec
import io.github.whywood.deepdeep.plugin.PluginSnapshot
import io.github.whywood.deepdeep.plugin.PluginStatus
import io.github.whywood.deepdeep.plugin.micloud.MiCloudFeatureSection
import io.github.whywood.deepdeep.plugin.micloud.MiCloudPlugin
import io.github.whywood.deepdeep.ui.settings.SettingsGroup
import kotlinx.coroutines.delay

@Composable
fun PluginDetailScreen(
    manager: PluginManager,
    pluginId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val state by manager.state.collectAsState()
    val snapshot = state.snapshot(pluginId)
    val operation = state.operation?.takeIf { it.pluginId == pluginId }
    val busy = state.busy || state.interaction != null
    var deleteOpen by remember { mutableStateOf(false) }
    var detailRows by remember(pluginId) { mutableStateOf<List<PluginDetailRow>>(emptyList()) }
    var detailRefresh by remember(pluginId) { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { manager.refreshIfStale() }
    LifecycleResumeEffect(Unit) {
        detailRefresh += 1
        manager.refreshIfStale()
        onPauseOrDispose { }
    }
    LaunchedEffect(pluginId, state.plugins, state.loading) {
        if (!state.loading && snapshot == null) {
            delay(120)
            onBack()
        }
    }
    LaunchedEffect(snapshot?.status, snapshot?.installedVersion, detailRefresh) {
        val current = snapshot ?: return@LaunchedEffect
        val status = current.status ?: return@LaunchedEffect
        detailRows = runCatching { current.plugin.detailRows(status) }.getOrDefault(emptyList())
    }

    if (snapshot == null) {
        return
    }

    val plugin = snapshot.plugin
    val name = stringResource(plugin.nameRes)
    val statusLabel = when {
        operation != null -> operation.stage
            ?: operation.progress?.let { stringResource(R.string.plugins_downloading_percent, it) }
            ?: stringResource(R.string.plugins_stage_preparing)

        !snapshot.installed -> stringResource(R.string.plugins_status_not_installed)
        !snapshot.enabled -> stringResource(R.string.plugins_status_disabled)
        else -> snapshot.status?.label ?: stringResource(R.string.plugins_status_installed)
    }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("plugin_detail_$pluginId"),
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
                text = name,
                modifier = Modifier.weight(1f),
                style = DdType.heading,
                color = colors.ink,
            )
            if (operation != null) {
                DdSpinner(size = 18.dp, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            DdIconButton(
                icon = DdIcons.Refresh,
                onClick = { manager.refresh() },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Text(
                text = stringResource(plugin.summaryRes),
                style = DdType.bodySmall,
                color = colors.inkTertiary,
            )
            Spacer(Modifier.height(18.dp))
            SettingsGroup {
                DdListRow(
                    title = stringResource(R.string.plugins_detail_status),
                    value = statusLabel,
                )
                DdHairline()
                DdListRow(
                    title = stringResource(R.string.plugins_detail_version),
                    value = snapshot.installedVersion ?: plugin.version,
                )
                DdHairline()
                DdListRow(
                    title = stringResource(R.string.plugins_detail_size),
                    value = stringResource(plugin.sizeLabelRes),
                )
                if (detailRows.isNotEmpty()) {
                    detailRows.forEach { row ->
                        DdHairline()
                        DdListRow(
                            title = stringResource(row.labelRes),
                            value = row.value,
                        )
                    }
                }
                val skills = plugin.skills()
                if (skills.isNotEmpty()) {
                    DdHairline()
                    DdListRow(
                        title = stringResource(R.string.plugins_detail_skills),
                        value = stringResource(
                            R.string.plugins_detail_skills_value,
                            skills.joinToString("、") { it.id },
                        ),
                    )
                }
            }

            if (snapshot.installed && plugin.settings().isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text(
                    text = stringResource(R.string.plugins_setting_section),
                    modifier = Modifier.padding(horizontal = 4.dp),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                )
                Spacer(Modifier.height(6.dp))
                SettingsGroup {
                    PluginSettingsRows(
                        plugin = plugin,
                        settings = snapshot.settings,
                        enabled = !busy && snapshot.enabled,
                        onSetting = { key, value -> manager.setSetting(plugin.id, key, value) },
                    )
                }
            }

            val micloud = plugin as? MiCloudPlugin
            if (micloud != null && snapshot.installed && snapshot.enabled) {
                Spacer(Modifier.height(16.dp))
                MiCloudFeatureSection(
                    preferences = micloud.preferences,
                    enabled = snapshot.status is PluginStatus.Ready,
                )
            }

            val availability = snapshot.availability
            if (!snapshot.installed && availability is PluginAvailability.Unavailable) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = availability.reason,
                    modifier = Modifier.padding(horizontal = 4.dp),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                )
            }

            Spacer(Modifier.height(24.dp))
            when {
                plugin.kind == PluginKind.INTERNAL -> Unit

                !snapshot.installed -> {
                    DdPrimaryButton(
                        text = stringResource(R.string.plugins_action_download),
                        onClick = { manager.install(plugin.id) },
                        modifier = Modifier.fillMaxWidth().testTag("plugin_detail_download"),
                        enabled = availability is PluginAvailability.Available && !busy,
                    )
                }

                else -> {
                    if (snapshot.enabled) {
                        val actions = snapshot.status?.let { plugin.actions(it) }.orEmpty()
                        if (actions.isNotEmpty()) {
                            SettingsGroup {
                                actions.forEachIndexed { index, action ->
                                    if (index > 0) DdHairline()
                                    DdListRow(
                                        title = stringResource(action.labelRes),
                                        onClick = {
                                            if (!busy) manager.runAction(plugin.id, action.id)
                                        },
                                        enabled = !busy,
                                        modifier = Modifier.testTag("plugin_action_${action.id}"),
                                    )
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                    if (snapshot.updateAvailable) {
                        DdPrimaryButton(
                            text = stringResource(R.string.plugins_action_update),
                            onClick = { manager.update(plugin.id) },
                            modifier = Modifier.fillMaxWidth().testTag("plugin_detail_update"),
                            enabled = !busy,
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    SettingsGroup {
                        DdListRow(
                            title = stringResource(R.string.plugins_action_reinstall),
                            onClick = { if (!busy) manager.update(plugin.id) },
                            enabled = !busy,
                            modifier = Modifier.testTag("plugin_detail_reinstall"),
                        )
                        DdHairline()
                        DdListRow(
                            title = stringResource(R.string.plugins_action_delete),
                            onClick = { deleteOpen = true },
                            enabled = !busy,
                            modifier = Modifier.testTag("plugin_detail_delete"),
                        )
                    }
                }
            }

            state.error?.let { error ->
                Spacer(Modifier.height(14.dp))
                Text(
                    text = error,
                    modifier = Modifier.padding(horizontal = 4.dp).testTag("plugin_detail_error"),
                    style = DdType.bodySmall,
                    color = colors.danger,
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    if (deleteOpen) {
        DdDialog(
            onDismissRequest = { deleteOpen = false },
            title = stringResource(R.string.plugins_delete_title),
            content = {
                Text(
                    text = stringResource(R.string.plugins_delete_message, name),
                    style = DdType.bodySmall,
                    color = colors.inkSecondary,
                )
            },
            actions = {
                DdTextAction(
                    text = stringResource(R.string.plugins_cancel),
                    onClick = { deleteOpen = false },
                    color = colors.inkSecondary,
                )
                DdTextAction(
                    text = stringResource(R.string.plugins_delete_confirm),
                    onClick = {
                        deleteOpen = false
                        manager.uninstall(plugin.id)
                    },
                    color = colors.danger,
                )
            },
        )
    }
}

@Composable
private fun PluginSettingsRows(
    plugin: OfficialPlugin,
    settings: Map<String, String>,
    enabled: Boolean,
    onSetting: (String, String) -> Unit,
) {
    plugin.settings().forEachIndexed { index, spec ->
        if (index > 0) DdHairline()
        when (spec) {
            is PluginSettingSpec.Toggle -> PluginToggleSettingRow(spec, settings, enabled, onSetting)
            is PluginSettingSpec.Choice -> PluginChoiceSettingRow(spec, settings, enabled, onSetting)
            is PluginSettingSpec.Number -> PluginNumberSettingRow(spec, settings, enabled, onSetting)
        }
    }
}

@Composable
private fun PluginToggleSettingRow(
    spec: PluginSettingSpec.Toggle,
    settings: Map<String, String>,
    enabled: Boolean,
    onSetting: (String, String) -> Unit,
) {
    val checked = settings[spec.key]?.toBooleanStrictOrNull() ?: spec.defaultValue
    DdListRow(
        title = stringResource(spec.titleRes),
        trailing = {
            DdSwitch(
                checked = checked,
                onCheckedChange = { onSetting(spec.key, it.toString()) },
                enabled = enabled,
                modifier = Modifier.testTag("plugin_setting_${spec.key}"),
            )
        },
    )
}

@Composable
private fun PluginChoiceSettingRow(
    spec: PluginSettingSpec.Choice,
    settings: Map<String, String>,
    enabled: Boolean,
    onSetting: (String, String) -> Unit,
) {
    val colors = deepDeepColors()
    var expanded by remember(spec.key) { mutableStateOf(false) }
    val stored = settings[spec.key]
    val current = spec.options.firstOrNull { it.value == stored }?.value ?: spec.default
    val currentLabel = spec.options.firstOrNull { it.value == current }?.labelRes?.let { stringResource(it) } ?: current
    DdDropdown(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        anchor = {
            DdListRow(
                title = stringResource(spec.titleRes),
                value = currentLabel,
                showChevron = true,
                enabled = enabled,
                onClick = if (enabled) {
                    { expanded = true }
                } else {
                    null
                },
                modifier = Modifier.testTag("plugin_setting_${spec.key}"),
            )
        },
    ) {
        spec.options.forEach { option ->
            DdMenuItem(
                text = stringResource(option.labelRes),
                onClick = {
                    expanded = false
                    onSetting(spec.key, option.value)
                },
                selected = option.value == current,
                trailing = if (option.value == current) {
                    {
                        DdIcon(icon = DdIcons.Check, tint = colors.accent, size = 18.dp)
                    }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun PluginNumberSettingRow(
    spec: PluginSettingSpec.Number,
    settings: Map<String, String>,
    enabled: Boolean,
    onSetting: (String, String) -> Unit,
) {
    val colors = deepDeepColors()
    val current = (settings[spec.key]?.toIntOrNull() ?: spec.defaultValue).coerceIn(spec.min, spec.max)
    DdListRow(
        title = stringResource(spec.titleRes),
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DdTextAction(
                    text = "−",
                    onClick = { onSetting(spec.key, (current - 1).toString()) },
                    enabled = enabled && current > spec.min,
                    color = colors.inkSecondary,
                    modifier = Modifier.testTag("plugin_setting_${spec.key}_dec"),
                )
                Text(
                    text = current.toString(),
                    modifier = Modifier.width(36.dp).testTag("plugin_setting_${spec.key}_value"),
                    style = DdType.body,
                    color = colors.ink,
                    textAlign = TextAlign.Center,
                )
                DdTextAction(
                    text = "+",
                    onClick = { onSetting(spec.key, (current + 1).toString()) },
                    enabled = enabled && current < spec.max,
                    color = colors.inkSecondary,
                    modifier = Modifier.testTag("plugin_setting_${spec.key}_inc"),
                )
            }
        },
    )
}
