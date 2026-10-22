package io.github.whywood.deepdeep.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdPrimaryButton
import io.github.whywood.deepdeep.design.DdSectionLabel
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.shell.LinuxInstallState

@Composable
fun LinuxSettingsScreen(
    state: LinuxInstallState,
    supported: Boolean,
    enabled: Boolean,
    onInstall: () -> Unit,
    onReset: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val installed = state is LinuxInstallState.Installed
    val installing = state is LinuxInstallState.Installing
    var resetConfirm by remember { mutableStateOf(false) }

    Column(modifier) {
        DdSectionLabel(stringResource(R.string.settings_linux_title))
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surface)
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Text(text = statusText(state), style = DdType.body, color = colors.ink)
            if (state is LinuxInstallState.Installed) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_linux_size, (state.sizeBytes / (1024 * 1024)).toInt()),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                )
            }
            if (!supported) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_linux_unsupported),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                )
            }
        }

        if (!installed) {
            Spacer(Modifier.height(16.dp))
            DdPrimaryButton(
                text = stringResource(
                    if (installing) R.string.settings_linux_installing else R.string.settings_linux_install,
                ),
                onClick = onInstall,
                modifier = Modifier.fillMaxWidth().testTag("linux_install"),
                enabled = supported && !installing,
            )
        }

        Spacer(Modifier.height(16.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surface)
                .padding(horizontal = 16.dp),
        ) {
            DdListRow(
                title = stringResource(R.string.settings_linux_enable),
                enabled = installed && supported,
                trailing = {
                    Switch(
                        checked = enabled && installed,
                        onCheckedChange = onEnabledChange,
                        enabled = installed && supported,
                        modifier = Modifier.testTag("linux_enable_switch"),
                    )
                },
            )
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.settings_linux_boundary),
            style = DdType.bodySmall,
            color = colors.inkTertiary,
        )

        if (installed) {
            Spacer(Modifier.height(20.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.surface)
                    .padding(horizontal = 16.dp),
            ) {
                DdListRow(
                    icon = DdIcons.Refresh,
                    title = stringResource(R.string.settings_linux_reset),
                    subtitle = stringResource(R.string.settings_linux_reset_body),
                    onClick = { resetConfirm = true },
                    showChevron = true,
                    modifier = Modifier.testTag("linux_reset"),
                )
            }
        }
    }

    if (resetConfirm) {
        AlertDialog(
            onDismissRequest = { resetConfirm = false },
            title = { Text(stringResource(R.string.settings_linux_reset_title), style = DdType.heading) },
            text = { Text(stringResource(R.string.settings_linux_reset_dialog), style = DdType.body) },
            confirmButton = {
                TextButton(
                    onClick = {
                        resetConfirm = false
                        onReset()
                    },
                ) {
                    Text(stringResource(R.string.settings_linux_reset_confirm), color = colors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { resetConfirm = false }) {
                    Text(stringResource(R.string.settings_linux_cancel), color = colors.inkSecondary)
                }
            },
            containerColor = colors.raised,
        )
    }
}

@Composable
private fun statusText(state: LinuxInstallState): String = when (state) {
    LinuxInstallState.NotInstalled -> stringResource(R.string.settings_linux_status_not_installed)
    LinuxInstallState.Installing -> stringResource(R.string.settings_linux_status_installing)
    is LinuxInstallState.Installed -> stringResource(R.string.settings_linux_status_installed, state.version)
    is LinuxInstallState.Failed -> stringResource(R.string.settings_linux_status_failed, state.message)
}
