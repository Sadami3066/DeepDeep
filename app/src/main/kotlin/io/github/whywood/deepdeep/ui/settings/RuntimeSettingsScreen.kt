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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.DevBundle
import io.github.whywood.deepdeep.data.DevPackagesState
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdSwitch
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.shell.LinuxInstallState
import io.github.whywood.deepdeep.shell.RootStatus

@Composable
fun RuntimeSettingsScreen(
    linuxState: LinuxInstallState,
    linuxSupported: Boolean,
    linuxEnabled: Boolean,
    sharedStorageEnabled: Boolean,
    devPackages: DevPackagesState,
    rootStatus: RootStatus,
    rootEnabled: Boolean,
    onRootEnabledChange: (Boolean) -> Unit,
    onProbeRoot: () -> Unit,
    onBack: () -> Unit,
    onInstall: () -> Unit,
    onReset: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onSharedStorageChange: (Boolean) -> Unit,
    onInstallBundle: (DevBundle) -> Unit,
    onRefreshPackages: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("runtime_settings"),
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
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_environment),
                style = DdType.pageTitle,
                color = colors.ink,
            )
            Spacer(Modifier.height(20.dp))
            SettingsGroup {
                LinuxEnvironmentSection(
                    state = linuxState,
                    supported = linuxSupported,
                    enabled = linuxEnabled,
                    sharedStorageEnabled = sharedStorageEnabled,
                    onInstall = onInstall,
                    onReset = onReset,
                    onEnabledChange = onEnabledChange,
                    onSharedStorageChange = onSharedStorageChange,
                )
            }
            Spacer(Modifier.height(20.dp))
            SettingsGroup {
                RootSection(
                    status = rootStatus,
                    enabled = rootEnabled,
                    onEnabledChange = onRootEnabledChange,
                    onProbe = onProbeRoot,
                )
            }
            Spacer(Modifier.height(24.dp))
            DevPackagesSection(
                state = devPackages,
                linuxInstalled = linuxState is LinuxInstallState.Installed,
                onInstallBundle = onInstallBundle,
                onRefresh = onRefreshPackages,
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun RootSection(
    status: RootStatus,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onProbe: () -> Unit,
) {
    val colors = deepDeepColors()
    val context = LocalContext.current
    val managerMissing = stringResource(R.string.settings_root_manager_missing)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_root_title),
                    style = DdType.body,
                    color = colors.ink,
                )
                Text(
                    text = rootStatusLabel(status),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                )
            }
            DdSwitch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.testTag("settings_root_switch"),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_root_hint),
            style = DdType.bodySmall,
            color = colors.inkTertiary,
        )
        if (enabled) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                DdTextAction(
                    text = stringResource(R.string.settings_root_probe),
                    onClick = onProbe,
                    modifier = Modifier.testTag("settings_root_probe"),
                )
                Spacer(Modifier.width(20.dp))
                DdTextAction(
                    text = stringResource(R.string.settings_root_open_manager),
                    onClick = {
                        if (!openRootManager(context)) {
                            Toast.makeText(
                                context,
                                managerMissing,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    modifier = Modifier.testTag("settings_root_manager"),
                )
            }
        }
    }
}

@Composable
private fun rootStatusLabel(status: RootStatus): String = stringResource(
    when (status) {
        RootStatus.Unsupported -> R.string.settings_root_status_unsupported
        RootStatus.NotRequested -> R.string.settings_root_status_not_requested
        RootStatus.Granted -> R.string.settings_root_status_granted
        RootStatus.Denied -> R.string.settings_root_status_denied
        RootStatus.Timeout -> R.string.settings_root_status_timeout
        RootStatus.Error -> R.string.settings_root_status_error
    },
)

private val ROOT_MANAGER_PACKAGES = listOf(
    "me.bmax.apatch",
    "com.topjohnwu.magisk",
    "me.weishu.kernelsu",
    "com.dergoogler.mmrl",
)

private fun openRootManager(context: Context): Boolean {
    for (packageName in ROOT_MANAGER_PACKAGES) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: continue
        runCatching { context.startActivity(intent) }
        return true
    }
    return false
}
