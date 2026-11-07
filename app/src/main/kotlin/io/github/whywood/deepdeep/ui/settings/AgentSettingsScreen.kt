package io.github.whywood.deepdeep.ui.settings

import androidx.compose.foundation.clickable
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
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdPageScaffold
import io.github.whywood.deepdeep.design.DdSwitch
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.privilege.PrivilegeLevel

@Composable
fun AgentSettingsScreen(
    privilege: PrivilegeLevel,
    screenOps: Boolean,
    onScreenOpsChange: (Boolean) -> Unit,
    onOpenPermission: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
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
            Spacer(Modifier.height(40.dp))
        }
    }
}
