package io.github.whywood.deepdeep.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.SessionNamingMode
import io.github.whywood.deepdeep.design.DdDialog
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdMenu
import io.github.whywood.deepdeep.design.DdMenuItem
import io.github.whywood.deepdeep.design.DdPageScaffold
import io.github.whywood.deepdeep.design.DdSectionLabel
import io.github.whywood.deepdeep.design.DdSwitch
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.privilege.PrivilegeLevel
import io.github.whywood.deepdeep.runtime.ModelOption

@Composable
fun AgentSettingsScreen(
    privilege: PrivilegeLevel,
    screenOps: Boolean,
    onScreenOpsChange: (Boolean) -> Unit,
    onOpenPermission: () -> Unit,
    metadataMode: SessionNamingMode,
    metadataModelLabel: String?,
    metadataModels: List<ModelOption>,
    onMetadataModeChange: (SessionNamingMode) -> Unit,
    onMetadataModelChange: (ModelOption) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    var modeMenu by remember { mutableStateOf(false) }
    var modelPicker by remember { mutableStateOf(false) }
    DdPageScaffold(
        topBar = {
            DdIconButton(
                icon = DdIcons.ArrowLeft,
                onClick = onBack,
                contentDescription = stringResource(R.string.settings_back),
                tint = colors.ink,
                iconSize = 21.dp,
            )
        },
        modifier = modifier.testTag("agent_settings"),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.agent_settings_title),
                style = DdType.pageTitle,
                color = colors.ink,
            )
            Spacer(Modifier.height(20.dp))
            DdSectionLabel(stringResource(R.string.agent_section_capabilities))
            Spacer(Modifier.height(8.dp))
            SettingsGroup {
                DdListRow(
                    icon = DdIcons.HandClick,
                    title = stringResource(R.string.settings_screen_ops),
                    subtitle = stringResource(R.string.settings_screen_ops_summary),
                    subtitleMaxLines = 2,
                    onClick = { onScreenOpsChange(!screenOps) },
                    modifier = Modifier.testTag("agent_screen_ops"),
                    trailing = {
                        DdSwitch(
                            checked = screenOps,
                            onCheckedChange = onScreenOpsChange,
                            stateDescription = stringResource(
                                if (screenOps) R.string.settings_switch_on else R.string.settings_switch_off,
                            ),
                        )
                    },
                )
            }
            if (screenOps && privilege != PrivilegeLevel.ADB && privilege != PrivilegeLevel.ROOT) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.agent_screen_ops_inactive),
                    style = DdType.bodySmall,
                    color = colors.warning,
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .clickable(onClick = onOpenPermission)
                        .testTag("agent_screen_ops_inactive"),
                )
            }
            Spacer(Modifier.height(28.dp))
            DdSectionLabel(stringResource(R.string.agent_section_metadata))
            Spacer(Modifier.height(8.dp))
            SettingsGroup {
                Box {
                    DdListRow(
                        icon = DdIcons.Sparkles,
                        title = stringResource(R.string.agent_metadata_title),
                        subtitle = stringResource(R.string.agent_metadata_summary),
                        subtitleMaxLines = 2,
                        value = stringResource(
                            when (metadataMode) {
                                SessionNamingMode.AUTO -> R.string.agent_metadata_auto
                                SessionNamingMode.MANUAL -> R.string.agent_metadata_manual
                            },
                        ),
                        onClick = { modeMenu = true },
                        modifier = Modifier.testTag("agent_metadata_mode"),
                    )
                    DdMenu(
                        expanded = modeMenu,
                        onDismissRequest = { modeMenu = false },
                        menuWidth = 200.dp,
                    ) {
                        DdMenuItem(
                            text = stringResource(R.string.agent_metadata_auto),
                            onClick = {
                                modeMenu = false
                                onMetadataModeChange(SessionNamingMode.AUTO)
                            },
                            selected = metadataMode == SessionNamingMode.AUTO,
                            testTag = "agent_metadata_auto",
                        )
                        DdMenuItem(
                            text = stringResource(R.string.agent_metadata_manual),
                            onClick = {
                                modeMenu = false
                                onMetadataModeChange(SessionNamingMode.MANUAL)
                            },
                            selected = metadataMode == SessionNamingMode.MANUAL,
                            testTag = "agent_metadata_manual",
                        )
                    }
                }
                if (metadataMode == SessionNamingMode.MANUAL) {
                    DdListRow(
                        icon = DdIcons.Brain,
                        title = stringResource(R.string.agent_metadata_model),
                        value = metadataModelLabel ?: stringResource(R.string.agent_metadata_model_pick),
                        onClick = { modelPicker = true },
                        modifier = Modifier.testTag("agent_metadata_model"),
                    )
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
    if (modelPicker) {
        DdDialog(
            onDismissRequest = { modelPicker = false },
            title = stringResource(R.string.agent_metadata_model),
            content = {
                if (metadataModels.isEmpty()) {
                    Text(
                        text = stringResource(R.string.agent_metadata_model_empty),
                        style = DdType.body,
                        color = colors.inkSecondary,
                    )
                } else {
                    Column(
                        Modifier
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        metadataModels.forEach { model ->
                            DdListRow(
                                title = model.displayName,
                                subtitle = model.providerId,
                                onClick = {
                                    modelPicker = false
                                    onMetadataModelChange(model)
                                },
                                modifier = Modifier.testTag("agent_metadata_model_${model.providerId}_${model.id}"),
                            )
                        }
                    }
                }
            },
            actions = {
                DdTextAction(
                    text = stringResource(R.string.agent_metadata_cancel),
                    onClick = { modelPicker = false },
                    color = colors.inkSecondary,
                )
            },
        )
    }

}
