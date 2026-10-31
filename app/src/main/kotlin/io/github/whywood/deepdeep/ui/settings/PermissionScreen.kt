package io.github.whywood.deepdeep.ui.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.adb.AdbBridgeError
import io.github.whywood.deepdeep.adb.AdbBridgeState
import io.github.whywood.deepdeep.design.DdGroupCard
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.privilege.PrivilegeLevel
import io.github.whywood.deepdeep.privilege.PrivilegeState
import io.github.whywood.deepdeep.shell.RootStatus

@Composable
fun PermissionScreen(
    state: PrivilegeState,
    onSelect: (PrivilegeLevel) -> Unit,
    onRefresh: () -> Unit,
    onPair: () -> Unit,
    onConnect: () -> Unit,
    onOpenWirelessDebugging: () -> Unit,
    onProbeRoot: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    LifecycleResumeEffect(Unit) {
        onRefresh()
        onPauseOrDispose {}
    }
    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("permission_settings"),
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
                text = stringResource(R.string.permission_title),
                style = DdType.pageTitle,
                color = colors.ink,
            )
            Spacer(Modifier.height(20.dp))
            SettingsGroup {
                PrivilegeLevelRow(
                    level = PrivilegeLevel.NORMAL,
                    status = stringResource(R.string.permission_status_normal),
                    checked = state.selected == PrivilegeLevel.NORMAL,
                    onSelect = { onSelect(PrivilegeLevel.NORMAL) },
                    testTag = "permission_level_normal",
                )
                DdHairline()
                PrivilegeLevelRow(
                    level = PrivilegeLevel.ADB,
                    status = adbStatusText(state.adb),
                    checked = state.selected == PrivilegeLevel.ADB,
                    onSelect = { onSelect(PrivilegeLevel.ADB) },
                    testTag = "permission_level_adb",
                )
                DdHairline()
                PrivilegeLevelRow(
                    level = PrivilegeLevel.ROOT,
                    status = rootStatusText(state.root),
                    checked = state.selected == PrivilegeLevel.ROOT,
                    onSelect = { onSelect(PrivilegeLevel.ROOT) },
                    testTag = "permission_level_root",
                )
            }
            Spacer(Modifier.height(12.dp))
            PrivilegeLevelAction(
                state = state,
                onPair = onPair,
                onConnect = onConnect,
                onOpenWirelessDebugging = onOpenWirelessDebugging,
                onProbeRoot = onProbeRoot,
            )
            Spacer(Modifier.height(24.dp))
            SettingsGroup {
                DdListRow(
                    title = stringResource(R.string.permission_effective),
                    value = levelLabel(state.effective),
                    modifier = Modifier.testTag("permission_effective"),
                )
            }
            if (state.effective != state.selected) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.permission_effective_fallback, levelLabel(state.effective)),
                    style = DdType.bodySmall,
                    color = colors.danger,
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun PrivilegeLevelRow(
    level: PrivilegeLevel,
    status: String,
    checked: Boolean,
    onSelect: () -> Unit,
    testTag: String,
) {
    DdListRow(
        title = levelLabel(level),
        subtitle = status,
        subtitleMaxLines = 2,
        onClick = onSelect,
        showChevron = false,
        modifier = Modifier
            .semantics { selected = checked }
            .testTag(testTag),
        trailing = { PrivilegeRadio(checked = checked) },
    )
}

@Composable
private fun PrivilegeRadio(checked: Boolean) {
    val colors = deepDeepColors()
    Box(
        Modifier
            .size(20.dp)
            .clip(CircleShape)
            .border(1.5.dp, if (checked) colors.accent else colors.hairline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(colors.accent),
            )
        }
    }
}

@Composable
private fun PrivilegeLevelAction(
    state: PrivilegeState,
    onPair: () -> Unit,
    onConnect: () -> Unit,
    onOpenWirelessDebugging: () -> Unit,
    onProbeRoot: () -> Unit,
) {
    when (state.selected) {
        PrivilegeLevel.NORMAL -> PrivilegeHint(R.string.permission_hint_normal)

        PrivilegeLevel.ADB -> Column(Modifier.fillMaxWidth()) {
            val adb = state.adb
            val context = LocalContext.current
            DdGroupCard {
                when {
                    adb.busy -> DdListRow(
                        icon = DdIcons.Refresh,
                        title = stringResource(R.string.permission_action_adb_connecting),
                        enabled = false,
                    )

                    !adb.paired -> PrivilegeAction(
                        icon = DdIcons.Plug,
                        title = stringResource(R.string.permission_action_adb_pair),
                        onClick = onPair,
                    )

                    adb.error != null -> PrivilegeAction(
                        icon = DdIcons.Refresh,
                        title = stringResource(R.string.permission_action_adb_retry),
                        onClick = onConnect,
                    )

                    !adb.daemon -> PrivilegeAction(
                        icon = DdIcons.Plug,
                        title = stringResource(R.string.permission_action_adb_connect),
                        onClick = onConnect,
                    )

                    else -> PrivilegeAction(
                        icon = DdIcons.Refresh,
                        title = stringResource(R.string.permission_action_adb_restart),
                        onClick = onConnect,
                    )
                }
                if (notificationBlocked(adb.error)) {
                    DdHairline()
                    PrivilegeAction(
                        icon = DdIcons.Settings,
                        title = stringResource(R.string.permission_action_notification_settings),
                    ) {
                        openNotificationSettings(context)
                    }
                }
                DdHairline()
                PrivilegeAction(
                    icon = DdIcons.External,
                    title = stringResource(R.string.permission_action_adb_settings),
                    onClick = onOpenWirelessDebugging,
                )
            }
            Spacer(Modifier.height(10.dp))
            PrivilegeHint(R.string.permission_hint_adb)
        }

        PrivilegeLevel.ROOT -> if (state.root != RootStatus.Granted) {
            DdGroupCard {
                PrivilegeAction(
                    icon = DdIcons.Shield,
                    title = stringResource(R.string.permission_action_root_probe),
                    onClick = onProbeRoot,
                )
            }
        }
    }
}

@Composable
private fun PrivilegeAction(
    icon: DdIcon,
    title: String,
    onClick: () -> Unit,
) {
    DdListRow(
        icon = icon,
        title = title,
        onClick = onClick,
        showChevron = true,
        modifier = Modifier.testTag("permission_action"),
    )
}

@Composable
private fun PrivilegeHint(@StringRes textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = DdType.bodySmall,
        color = deepDeepColors().inkTertiary,
    )
}

@Composable
private fun levelLabel(level: PrivilegeLevel): String = stringResource(
    when (level) {
        PrivilegeLevel.NORMAL -> R.string.permission_level_normal
        PrivilegeLevel.ADB -> R.string.permission_level_adb
        PrivilegeLevel.ROOT -> R.string.permission_level_root
    },
)

@Composable
private fun adbStatusText(adb: AdbBridgeState): String = when {
    adb.busy -> stringResource(R.string.permission_status_adb_connecting)
    adb.daemon -> stringResource(R.string.permission_status_adb_running)
    adb.error != null -> stringResource(R.string.permission_status_adb_error, adbErrorLabel(adb.error))
    adb.paired -> stringResource(R.string.permission_status_adb_paired)
    else -> stringResource(R.string.permission_status_adb_unpaired)
}

@Composable
private fun adbErrorLabel(error: AdbBridgeError): String = stringResource(
    when (error) {
        AdbBridgeError.WIRELESS_DEBUGGING_DISABLED -> R.string.permission_error_wireless_debugging_disabled
        AdbBridgeError.NOTIFICATION_PERMISSION_REQUIRED -> R.string.permission_error_notification_permission_required
        AdbBridgeError.NOTIFICATION_UNAVAILABLE -> R.string.permission_error_notification_unavailable
        AdbBridgeError.PAIRING_CODE_INVALID -> R.string.permission_error_pairing_code_invalid
        AdbBridgeError.NO_PAIRING_SERVICE -> R.string.permission_error_no_pairing_service
        AdbBridgeError.PAIRING_FAILED -> R.string.permission_error_pairing_failed
        AdbBridgeError.NOT_PAIRED -> R.string.permission_error_not_paired
        AdbBridgeError.CONNECT_FAILED -> R.string.permission_error_connect_failed
        AdbBridgeError.DAEMON_START_FAILED -> R.string.permission_error_daemon_start_failed
    },
)

@Composable
private fun rootStatusText(status: RootStatus): String = stringResource(
    when (status) {
        RootStatus.Unsupported -> R.string.permission_status_root_unsupported
        RootStatus.NotRequested -> R.string.permission_status_root_not_requested
        RootStatus.Granted -> R.string.permission_status_root_granted
        RootStatus.Denied -> R.string.permission_status_root_denied
        RootStatus.Timeout -> R.string.permission_status_root_timeout
        RootStatus.Error -> R.string.permission_status_root_error
    },
)

private fun notificationBlocked(error: AdbBridgeError?): Boolean =
    error == AdbBridgeError.NOTIFICATION_PERMISSION_REQUIRED || error == AdbBridgeError.NOTIFICATION_UNAVAILABLE

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
