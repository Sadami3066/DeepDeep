package io.github.whywood.deepdeep.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdGroupCard
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdPageScaffold
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.privilege.PrivilegeLevel

@Composable
fun AgentSettingsScreen(
    effective: PrivilegeLevel,
    screenOps: Boolean,
    onOpenPermission: () -> Unit,
    onOpenMcp: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val privilegeActive = effective == PrivilegeLevel.ADB || effective == PrivilegeLevel.ROOT
    val active = screenOps && privilegeActive

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
            SectionLabel(R.string.agent_settings_section_capability)
            Spacer(Modifier.height(8.dp))
            DdGroupCard {
                DdListRow(
                    icon = DdIcons.HandClick,
                    title = stringResource(R.string.settings_screen_ops),
                    subtitle = stringResource(R.string.settings_screen_ops_summary),
                    subtitleMaxLines = 2,
                    value = stringResource(
                        if (active) R.string.agent_settings_screen_ops_active
                        else R.string.agent_settings_screen_ops_inactive,
                    ),
                    onClick = onOpenPermission,
                    showChevron = true,
                    modifier = Modifier.testTag("agent_screen_ops"),
                )
            }
            if (!active) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        if (privilegeActive) R.string.agent_settings_screen_ops_hint_switch
                        else R.string.agent_settings_screen_ops_hint_privilege,
                    ),
                    style = DdType.bodySmall,
                    color = colors.warning,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
            SectionLabel(R.string.agent_settings_section_tools)
            Spacer(Modifier.height(8.dp))
            DdGroupCard {
                DdListRow(
                    icon = DdIcons.Plug,
                    title = stringResource(R.string.agent_settings_mcp),
                    subtitle = stringResource(R.string.agent_settings_mcp_summary),
                    subtitleMaxLines = 2,
                    onClick = onOpenMcp,
                    showChevron = true,
                    modifier = Modifier.testTag("agent_mcp"),
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.agent_settings_footer),
                style = DdType.bodySmall,
                color = colors.inkTertiary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SectionLabel(titleRes: Int) {
    val colors = deepDeepColors()
    Text(
        text = stringResource(titleRes),
        style = DdType.bodySmall,
        color = colors.inkTertiary,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}
