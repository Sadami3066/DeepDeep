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
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdPrimaryButton
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.shell.LinuxInstallState

@Composable
internal fun LinuxEnvironmentSection(
    state: LinuxInstallState,
    supported: Boolean,
    enabled: Boolean,
    onInstall: () -> Unit,
    onReset: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val installedState = state as? LinuxInstallState.Installed
    val installed = installedState != null
    val installing = state is LinuxInstallState.Installing
    var resetConfirm by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.wash)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (!installed) {
            DdPrimaryButton(
                text = stringResource(
                    if (installing) R.string.settings_linux_installing else R.string.settings_linux_install,
                ),
                onClick = onInstall,
                modifier = Modifier.fillMaxWidth().testTag("linux_install"),
                enabled = supported && !installing,
            )
            Spacer(Modifier.height(10.dp))
        }
        if (!supported) {
            Text(
                text = stringResource(R.string.settings_linux_unsupported),
                style = DdType.bodySmall,
                color = colors.inkTertiary,
            )
            Spacer(Modifier.height(4.dp))
        }
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
        if (installed) {
            DdHairline()
            Text(
                text = stringResource(R.string.settings_linux_size, (state.sizeBytes / (1024 * 1024)).toInt()),
                modifier = Modifier.padding(vertical = 8.dp),
                style = DdType.bodySmall,
                color = colors.inkTertiary,
            )
            DdHairline()
            DdListRow(
                icon = DdIcons.Refresh,
                title = stringResource(R.string.settings_linux_reset),
                onClick = { resetConfirm = true },
                showChevron = true,
                modifier = Modifier.testTag("linux_reset"),
            )
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
