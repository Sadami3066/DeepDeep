package io.github.whywood.deepdeep.ui.settings

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
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors

@Composable
fun PrivacyScreen(
    onBack: () -> Unit,
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
                text = stringResource(R.string.settings_privacy),
                style = DdType.pageTitle,
                color = colors.ink,
            )
            Spacer(Modifier.height(20.dp))
            SettingsGroup {
                DdListRow(
                    icon = DdIcons.Folder,
                    title = stringResource(R.string.privacy_local_title),
                    subtitle = stringResource(R.string.privacy_local_body),
                    subtitleMaxLines = 3,
                )
                DdHairline()
                DdListRow(
                    icon = DdIcons.Globe,
                    title = stringResource(R.string.privacy_provider_title),
                    subtitle = stringResource(R.string.privacy_provider_body),
                    subtitleMaxLines = 3,
                )
                DdHairline()
                DdListRow(
                    icon = DdIcons.Shield,
                    title = stringResource(R.string.privacy_tracking_title),
                    subtitle = stringResource(R.string.privacy_tracking_body),
                    subtitleMaxLines = 3,
                )
                DdHairline()
                DdListRow(
                    icon = DdIcons.Info,
                    title = stringResource(R.string.privacy_uninstall_title),
                    subtitle = stringResource(R.string.privacy_uninstall_body),
                    subtitleMaxLines = 3,
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
