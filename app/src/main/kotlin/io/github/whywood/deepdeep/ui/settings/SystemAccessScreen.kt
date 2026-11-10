package io.github.whywood.deepdeep.ui.settings

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.adb.AdbBridgeError
import io.github.whywood.deepdeep.adb.AdbBridgeState
import io.github.whywood.deepdeep.data.DevBundle
import io.github.whywood.deepdeep.data.DevPackagesState
import io.github.whywood.deepdeep.data.StorageMount
import io.github.whywood.deepdeep.design.DdDialog
import io.github.whywood.deepdeep.design.DdGroupCard
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdSectionLabel
import io.github.whywood.deepdeep.design.DdSpacing
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdTextField
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.host.HostAccess
import io.github.whywood.deepdeep.runtime.host.HostAccessFallback
import io.github.whywood.deepdeep.runtime.host.HostAccessState
import io.github.whywood.deepdeep.shell.LinuxInstallState
import io.github.whywood.deepdeep.shell.RootfsState
import io.github.whywood.deepdeep.shell.RootfsStatus

@Composable
fun SystemAccessScreen(
    state: HostAccessState,
    adb: AdbBridgeState,
    rootfs: RootfsStatus,
    linuxState: LinuxInstallState,
    linuxSupported: Boolean,
    linuxEnabled: Boolean,
    sharedStorageEnabled: Boolean,
    mounts: List<StorageMount>,
    devPackages: DevPackagesState,
    devPackagesAvailable: Boolean,
    onSelect: (HostAccess) -> Unit,
    onRefresh: () -> Unit,
    onPair: () -> Unit,
    onPairingCode: (String) -> Unit,
    onConnect: () -> Unit,
    onOpenWirelessDebugging: () -> Unit,
    onRetryRootfs: () -> Unit,
    onRebuildRootfs: () -> Unit,
    onLinuxInstall: () -> Unit,
    onLinuxReset: () -> Unit,
    onLinuxEnabledChange: (Boolean) -> Unit,
    onSharedStorageChange: (Boolean) -> Unit,
    onSaveMount: (StorageMount) -> Unit,
    onRemoveMount: (String) -> Unit,
    onInstallBundle: (DevBundle) -> Unit,
    onRefreshPackages: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    var showPairingDialog by remember { mutableStateOf(false) }
    var showRebuildDialog by remember { mutableStateOf(false) }
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
            .testTag("system_access"),
    ) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = DdSpacing.Sm),
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
                .padding(horizontal = DdSpacing.ScreenHorizontal),
        ) {
            Text(
                text = stringResource(R.string.settings_access),
                style = DdType.pageTitle,
                color = colors.ink,
            )
            Spacer(Modifier.height(DdSpacing.Xl))
            DdSectionLabel(stringResource(R.string.access_section_permission))
            Spacer(Modifier.height(DdSpacing.Sm))
            SettingsGroup {
                AccessLevelRow(
                    access = HostAccess.LOCAL,
                    subtitle = stringResource(R.string.access_status_local),
                    selected = state.selected == HostAccess.LOCAL,
                    onSelect = onSelect,
                    testTag = "access_level_local",
                )
                DdHairline()
                AccessLevelRow(
                    access = HostAccess.ADB,
                    subtitle = adbStatusText(adb),
                    selected = state.selected == HostAccess.ADB,
                    onSelect = onSelect,
                    testTag = "access_level_adb",
                )
                DdHairline()
                AccessLevelRow(
                    access = HostAccess.ROOT,
                    subtitle = stringResource(
                        if (state.rootReady) R.string.access_status_root_ready else R.string.access_status_root_missing,
                    ),
                    selected = state.selected == HostAccess.ROOT,
                    onSelect = onSelect,
                    testTag = "access_level_root",
                )
            }
            Spacer(Modifier.height(DdSpacing.Md))
            SettingsGroup {
                DdListRow(
                    title = stringResource(R.string.access_effective),
                    value = stringResource(accessLevelLabel(state.effective)),
                    modifier = Modifier.testTag("access_effective"),
                )
            }
            state.fallback?.let { fallback ->
                Spacer(Modifier.height(DdSpacing.Sm))
                Text(
                    text = stringResource(fallbackText(fallback), stringResource(accessLevelLabel(state.effective))),
                    style = DdType.bodySmall,
                    color = colors.danger,
                )
            }
            Spacer(Modifier.height(DdSpacing.Xl))
            when (state.selected) {
                HostAccess.ROOT -> {
                    DdSectionLabel(stringResource(R.string.access_section_root))
                    Spacer(Modifier.height(DdSpacing.Sm))
                    DdGroupCard {
                        DdListRow(
                            title = stringResource(R.string.access_root_state),
                            subtitle = rootfsText(rootfs),
                            subtitleMaxLines = 3,
                            modifier = Modifier.testTag("access_root_state"),
                        )
                        if (rootfs.state is RootfsState.Failed) {
                            DdHairline()
                            DdListRow(
                                title = stringResource(R.string.access_root_retry),
                                showChevron = true,
                                onClick = onRetryRootfs,
                                modifier = Modifier.testTag("access_root_retry"),
                            )
                        }
                        val rebuilding = rootfs.state is RootfsState.Preparing
                        DdHairline()
                        DdListRow(
                            title = stringResource(R.string.access_root_rebuild),
                            subtitle = if (rebuilding) {
                                stringResource(R.string.access_root_rebuild_wait)
                            } else {
                                null
                            },
                            subtitleMaxLines = 2,
                            showChevron = true,
                            enabled = !rebuilding,
                            onClick = { showRebuildDialog = true },
                            modifier = Modifier.testTag("access_root_rebuild"),
                        )
                        if (!state.rootReady) {
                            DdHairline()
                            DdListRow(
                                icon = DdIcons.Shield,
                                title = stringResource(R.string.access_action_root_check),
                                showChevron = true,
                                onClick = onRefresh,
                                modifier = Modifier.testTag("access_root_check"),
                            )
                        }
                    }
                    Spacer(Modifier.height(DdSpacing.Md))
                    Text(
                        text = stringResource(R.string.access_root_hint),
                        style = DdType.bodySmall,
                        color = colors.inkTertiary,
                    )
                }

                else -> {
                    if (state.selected == HostAccess.ADB) {
                        DdGroupCard {
                            when {
                                adb.busy -> DdListRow(
                                    icon = DdIcons.Refresh,
                                    title = stringResource(R.string.access_status_adb_connecting),
                                    enabled = false,
                                )

                                !adb.paired -> AdbAction(
                                    title = stringResource(R.string.access_action_adb_pair),
                                    onClick = {
                                        showPairingDialog = true
                                        onPair()
                                    },
                                    testTag = "access_adb_pair",
                                )

                                adb.error != null -> AdbAction(
                                    title = stringResource(R.string.access_action_adb_retry),
                                    onClick = onConnect,
                                    testTag = "access_adb_retry",
                                )

                                !adb.daemon -> AdbAction(
                                    title = stringResource(R.string.access_action_adb_connect),
                                    onClick = onConnect,
                                    testTag = "access_adb_connect",
                                )

                                else -> AdbAction(
                                    title = stringResource(R.string.access_action_adb_restart),
                                    onClick = onConnect,
                                    testTag = "access_adb_restart",
                                )
                            }
                            if (adb.paired || adb.daemon) {
                                DdHairline()
                                AdbAction(
                                    title = stringResource(R.string.access_action_adb_repair),
                                    onClick = {
                                        showPairingDialog = true
                                        onPair()
                                    },
                                    testTag = "access_adb_repair",
                                )
                            }
                            DdHairline()
                            AdbAction(
                                title = stringResource(R.string.access_action_adb_settings),
                                onClick = onOpenWirelessDebugging,
                                testTag = "access_adb_settings",
                            )
                        }
                        Spacer(Modifier.height(DdSpacing.Md))
                        Text(
                            text = stringResource(R.string.access_hint_adb),
                            style = DdType.bodySmall,
                            color = colors.inkTertiary,
                        )
                        Spacer(Modifier.height(DdSpacing.Xl))
                    }
                    DdSectionLabel(stringResource(R.string.access_section_linux))
                    Spacer(Modifier.height(DdSpacing.Sm))
                    SettingsGroup {
                        LinuxEnvironmentSection(
                            state = linuxState,
                            supported = linuxSupported,
                            enabled = linuxEnabled,
                            sharedStorageEnabled = sharedStorageEnabled,
                            onInstall = onLinuxInstall,
                            onReset = onLinuxReset,
                            onEnabledChange = onLinuxEnabledChange,
                            onSharedStorageChange = onSharedStorageChange,
                            mounts = mounts,
                            onSaveMount = onSaveMount,
                            onRemoveMount = onRemoveMount,
                        )
                    }
                }
            }
            Spacer(Modifier.height(DdSpacing.Xxl))
            DevPackagesSection(
                state = devPackages,
                access = state.effective,
                environmentReady = devPackagesAvailable,
                onInstallBundle = onInstallBundle,
                onRefresh = onRefreshPackages,
            )
            Spacer(Modifier.height(DdSpacing.Huge))
        }
    }
    if (showPairingDialog) {
        PairingCodeDialog(
            adb = adb,
            onDismiss = { showPairingDialog = false },
            onConfirm = onPairingCode,
        )
    }
    if (showRebuildDialog) {
        DdDialog(
            onDismissRequest = { showRebuildDialog = false },
            modifier = Modifier.testTag("access_root_rebuild_dialog"),
            title = stringResource(R.string.access_root_rebuild),
            content = {
                Text(
                    text = stringResource(R.string.access_root_rebuild_message),
                    style = DdType.bodySmall,
                    color = colors.inkSecondary,
                )
            },
            actions = {
                DdTextAction(
                    text = stringResource(R.string.access_root_rebuild_cancel),
                    onClick = { showRebuildDialog = false },
                    color = colors.inkSecondary,
                )
                DdTextAction(
                    text = stringResource(R.string.access_root_rebuild_confirm),
                    onClick = {
                        showRebuildDialog = false
                        onRebuildRootfs()
                    },
                    color = colors.danger,
                )
            },
        )
    }
}

@Composable
private fun AccessLevelRow(
    access: HostAccess,
    subtitle: String,
    selected: Boolean,
    onSelect: (HostAccess) -> Unit,
    testTag: String,
) {
    DdListRow(
        title = stringResource(accessLevelLabel(access)),
        subtitle = subtitle,
        subtitleMaxLines = 2,
        onClick = { onSelect(access) },
        modifier = Modifier
            .semantics { this.selected = selected }
            .testTag(testTag),
        trailing = { AccessRadio(checked = selected) },
    )
}

@Composable
private fun AccessRadio(checked: Boolean) {
    val colors = deepDeepColors()
    Box(
        Modifier
            .size(20.dp)
            .clip(CircleShape)
            .border(1.5.dp, if (checked) colors.accent else colors.hairline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(colors.accent))
        }
    }
}

@Composable
private fun AdbAction(title: String, onClick: () -> Unit, testTag: String) {
    DdListRow(
        icon = DdIcons.Plug,
        title = title,
        onClick = onClick,
        showChevron = true,
        modifier = Modifier.testTag(testTag),
    )
}

@Composable
private fun PairingCodeDialog(
    adb: AdbBridgeState,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val colors = deepDeepColors()
    var code by remember { mutableStateOf("") }
    LaunchedEffect(adb.paired) {
        if (adb.paired) onDismiss()
    }
    DdDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("access_pairing_dialog"),
        title = stringResource(R.string.adb_pairing_dialog_title),
        content = {
            Text(
                text = stringResource(R.string.adb_pairing_dialog_steps),
                style = DdType.bodySmall,
                color = colors.inkSecondary,
            )
            Spacer(Modifier.height(DdSpacing.Md))
            DdTextField(
                value = code,
                onValueChange = { value -> code = value.filter(Char::isDigit).take(6) },
                placeholder = stringResource(R.string.adb_pairing_dialog_code_placeholder),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                testTag = "access_pairing_code",
            )
            if (adb.busy) {
                Spacer(Modifier.height(DdSpacing.Sm))
                Text(
                    text = stringResource(R.string.adb_pairing_dialog_waiting),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                )
            } else {
                adb.error?.let { error ->
                    Spacer(Modifier.height(DdSpacing.Sm))
                    Text(
                        text = adbErrorLabel(error),
                        style = DdType.bodySmall,
                        color = colors.danger,
                    )
                }
            }
        },
        actions = {
            DdTextAction(
                text = stringResource(R.string.adb_pairing_dialog_cancel),
                onClick = onDismiss,
                color = colors.inkSecondary,
            )
            DdTextAction(
                text = stringResource(R.string.adb_pairing_dialog_confirm),
                onClick = { onConfirm(code) },
                enabled = code.length == 6 && !adb.busy,
            )
        },
    )
}

@Composable
private fun adbStatusText(adb: AdbBridgeState): String = when {
    adb.busy -> stringResource(R.string.access_status_adb_connecting)
    adb.daemon -> stringResource(R.string.access_status_adb_running)
    adb.error != null -> stringResource(R.string.access_status_adb_error, adbErrorLabel(adb.error))
    adb.paired -> stringResource(R.string.access_status_adb_paired)
    else -> stringResource(R.string.access_status_adb_unpaired)
}

@Composable
private fun adbErrorLabel(error: AdbBridgeError): String = stringResource(
    when (error) {
        AdbBridgeError.WIRELESS_DEBUGGING_DISABLED -> R.string.access_error_wireless_debugging_disabled
        AdbBridgeError.PAIRING_CODE_INVALID -> R.string.access_error_pairing_code_invalid
        AdbBridgeError.NO_PAIRING_SERVICE -> R.string.access_error_no_pairing_service
        AdbBridgeError.PAIRING_FAILED -> R.string.access_error_pairing_failed
        AdbBridgeError.NOT_PAIRED -> R.string.access_error_not_paired
        AdbBridgeError.CONNECT_FAILED -> R.string.access_error_connect_failed
        AdbBridgeError.DAEMON_START_FAILED -> R.string.access_error_daemon_start_failed
    },
)

@StringRes
internal fun accessLevelLabel(access: HostAccess): Int = when (access) {
    HostAccess.LOCAL -> R.string.access_level_local
    HostAccess.ADB -> R.string.access_level_adb
    HostAccess.ROOT -> R.string.access_level_root
}

@StringRes
private fun fallbackText(fallback: HostAccessFallback): Int = when (fallback) {
    HostAccessFallback.ADB_UNAVAILABLE -> R.string.access_fallback_adb
    HostAccessFallback.ROOT_UNAVAILABLE -> R.string.access_fallback_root
}

@Composable
private fun rootfsText(rootfs: RootfsStatus): String = when (val state = rootfs.state) {
    RootfsState.Idle -> stringResource(R.string.access_root_idle)
    RootfsState.Preparing -> stringResource(R.string.access_root_preparing)
    RootfsState.Ready -> stringResource(R.string.access_root_ready)
    is RootfsState.Failed -> stringResource(R.string.access_root_failed_reason, state.reason)
}
