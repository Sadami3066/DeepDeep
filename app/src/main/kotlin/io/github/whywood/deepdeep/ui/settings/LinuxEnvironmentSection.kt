package io.github.whywood.deepdeep.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdDialog
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdPrimaryButton
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdSwitch
import io.github.whywood.deepdeep.design.DdTextAction
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
    val failed = state as? LinuxInstallState.Failed
    var resetConfirm by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth()) {
        if (!supported) {
            Text(
                text = stringResource(R.string.settings_linux_unsupported),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = DdType.bodySmall,
                color = colors.inkTertiary,
            )
            DdHairline()
        }
        if (installing) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DdSpinner(size = 18.dp, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.settings_linux_installing),
                    style = DdType.bodySmall,
                    color = colors.inkSecondary,
                )
            }
            DdHairline()
        }
        if (!installed && !installing) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                if (failed != null) {
                    Text(
                        text = stringResource(R.string.settings_linux_status_failed, failed.message),
                        style = DdType.bodySmall,
                        color = colors.inkSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                DdPrimaryButton(
                    text = stringResource(R.string.settings_linux_install),
                    onClick = onInstall,
                    modifier = Modifier.fillMaxWidth().testTag("linux_install"),
                    enabled = supported,
                )
            }
            DdHairline()
        }
        DdListRow(
            title = stringResource(R.string.settings_linux_enable),
            enabled = installed && supported,
            trailing = {
                DdSwitch(
                    checked = enabled && installed,
                    onCheckedChange = onEnabledChange,
                    enabled = installed && supported,
                    stateDescription = stringResource(
                        if (enabled && installed) {
                            R.string.settings_switch_on
                        } else {
                            R.string.settings_switch_off
                        },
                    ),
                    modifier = Modifier.testTag("linux_enable_switch"),
                )
            },
        )
        if (installedState != null) {
            DdHairline()
            DdListRow(
                title = stringResource(R.string.settings_linux_size_label),
                value = stringResource(
                    R.string.settings_linux_size_value,
                    (installedState.sizeBytes / (1024 * 1024)).toInt(),
                ),
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
        DdDialog(
            onDismissRequest = { resetConfirm = false },
            title = stringResource(R.string.settings_linux_reset_title),
            content = {
                Text(
                    text = stringResource(R.string.settings_linux_reset_dialog),
                    style = DdType.bodySmall,
                    color = colors.inkSecondary,
                )
            },
            actions = {
                DdTextAction(
                    text = stringResource(R.string.settings_linux_cancel),
                    onClick = { resetConfirm = false },
                    color = colors.inkSecondary,
                )
                DdTextAction(
                    text = stringResource(R.string.settings_linux_reset_confirm),
                    onClick = {
                        resetConfirm = false
                        onReset()
                    },
                )
            },
        )
    }
}
