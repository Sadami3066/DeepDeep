package io.github.whywood.deepdeep.ui.settings

import androidx.annotation.StringRes
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.UiPreferences
import io.github.whywood.deepdeep.data.UiToggle
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdSwitch
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors

@Composable
fun ChatSettingsScreen(
    preferences: UiPreferences,
    onBack: () -> Unit,
    onToggle: (UiToggle, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIconButton(
                icon = DdIcons.ArrowLeft,
                onClick = onBack,
                contentDescription = stringResource(R.string.settings_back),
                tint = colors.ink,
                iconSize = 21.dp,
            )
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_chat),
                style = DdType.pageTitle,
                color = colors.ink,
            )
            Spacer(Modifier.height(20.dp))
            SettingsGroup {
                SwitchRow(
                    icon = DdIcons.ChevronDown,
                    title = R.string.settings_auto_collapse_process,
                    checked = preferences.autoCollapseProcess,
                    onCheckedChange = { onToggle(UiToggle.AUTO_COLLAPSE_PROCESS, it) },
                )
                DdHairline()
                SwitchRow(
                    icon = DdIcons.Refresh,
                    title = R.string.settings_auto_retry,
                    checked = preferences.autoRetry,
                    onCheckedChange = { onToggle(UiToggle.AUTO_RETRY, it) },
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SwitchRow(
    icon: DdIcon,
    @StringRes title: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val stateDescription = stringResource(
        if (checked) R.string.settings_switch_on else R.string.settings_switch_off,
    )
    DdListRow(
        icon = icon,
        title = stringResource(title),
        onClick = { onCheckedChange(!checked) },
        trailing = {
            DdSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                stateDescription = stateDescription,
            )
        },
    )
}
