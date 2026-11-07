package io.github.whywood.deepdeep.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.SessionNamingMode
import io.github.whywood.deepdeep.design.DdDialog
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdMenu
import io.github.whywood.deepdeep.design.DdMenuItem
import io.github.whywood.deepdeep.design.DdPageScaffold
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdSectionLabel
import io.github.whywood.deepdeep.design.DdSwitch
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.LocalDdRowHorizontalPadding
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.ModelOption

@Composable
fun AgentSettingsScreen(
    screenOps: Boolean,
    onScreenOpsChange: (Boolean) -> Unit,
    onOpenWebSearch: () -> Unit,
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
                AgentRow(
                    title = stringResource(R.string.settings_screen_ops),
                    subtitle = stringResource(R.string.settings_screen_ops_summary),
                    onClick = { onScreenOpsChange(!screenOps) },
                    testTag = "agent_screen_ops",
                    trailing = {
                        DdSwitch(
                            checked = screenOps,
                            onCheckedChange = onScreenOpsChange,
                            activeColor = colors.warning,
                            stateDescription = stringResource(
                                if (screenOps) R.string.settings_switch_on else R.string.settings_switch_off,
                            ),
                        )
                    },
                )
                DdHairline(Modifier.padding(horizontal = 16.dp))
                AgentRow(
                    title = stringResource(R.string.agent_web_search),
                    subtitle = stringResource(R.string.agent_web_search_summary),
                    value = stringResource(R.string.agent_web_search_value),
                    onClick = onOpenWebSearch,
                    testTag = "agent_web_search",
                )
            }
            Spacer(Modifier.height(28.dp))
            DdSectionLabel(stringResource(R.string.agent_section_metadata))
            Spacer(Modifier.height(8.dp))
            SettingsGroup {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    AgentRow(
                        title = stringResource(R.string.agent_metadata_title),
                        subtitle = stringResource(R.string.agent_metadata_summary),
                        value = stringResource(
                            when (metadataMode) {
                                SessionNamingMode.AUTO -> R.string.agent_metadata_auto
                                SessionNamingMode.MANUAL -> R.string.agent_metadata_manual
                            },
                        ),
                        onClick = { modeMenu = true },
                        testTag = "agent_metadata_mode",
                    )
                    DdMenu(
                        expanded = modeMenu,
                        onDismissRequest = { modeMenu = false },
                        menuWidth = maxWidth,
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
                    DdHairline(Modifier.padding(horizontal = 16.dp))
                    AgentRow(
                        title = stringResource(R.string.agent_metadata_model),
                        value = metadataModelLabel ?: stringResource(R.string.agent_metadata_model_pick),
                        onClick = { modelPicker = true },
                        testTag = "agent_metadata_model",
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

@Composable
private fun AgentRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    testTag: String? = null,
) {
    val colors = deepDeepColors()
    DdPressable(
        onClick = onClick ?: {},
        modifier = modifier.fillMaxWidth().then(
            if (testTag == null) Modifier else Modifier.testTag(testTag),
        ),
        shape = RoundedCornerShape(20.dp),
        enabled = onClick != null,
        contentDescription = title,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = LocalDdRowHorizontalPadding.current, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = DdType.body.copy(fontSize = 17.sp, lineHeight = 24.sp),
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = DdType.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                        color = colors.inkTertiary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            } else if (value != null) {
                Spacer(Modifier.width(12.dp))
                Text(
                    text = value,
                    style = DdType.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                    color = colors.inkTertiary,
                    maxLines = 1,
                )
            }
        }
    }
}
