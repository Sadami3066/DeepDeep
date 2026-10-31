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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.whywood.deepdeep.R
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
import io.github.whywood.deepdeep.privilege.ShizukuState
import io.github.whywood.deepdeep.shell.RootStatus

@Composable
fun PermissionScreen(
    state: PrivilegeState,
    onSelect: (PrivilegeLevel) -> Unit,
    onRefresh: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onOpenShizuku: () -> Unit,
    onOpenShizukuInstall: () -> Unit,
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
                    level = PrivilegeLevel.SHIZUKU,
                    status = shizukuStatusText(state.shizuku),
                    checked = state.selected == PrivilegeLevel.SHIZUKU,
                    onSelect = { onSelect(PrivilegeLevel.SHIZUKU) },
                    testTag = "permission_level_shizuku",
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
                onRequestShizukuPermission = onRequestShizukuPermission,
                onOpenShizuku = onOpenShizuku,
                onOpenShizukuInstall = onOpenShizukuInstall,
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
    onRequestShizukuPermission: () -> Unit,
    onOpenShizuku: () -> Unit,
    onOpenShizukuInstall: () -> Unit,
) {
    when (state.selected) {
        PrivilegeLevel.NORMAL -> PrivilegeHint(R.string.permission_hint_normal)

        PrivilegeLevel.SHIZUKU -> Column(Modifier.fillMaxWidth()) {
            val shizuku = state.shizuku
            DdGroupCard {
                when {
                    !shizuku.installed -> PrivilegeAction(
                        icon = DdIcons.External,
                        title = stringResource(R.string.permission_action_install_shizuku),
                        onClick = onOpenShizukuInstall,
                    )

                    !shizuku.running -> PrivilegeAction(
                        icon = DdIcons.External,
                        title = stringResource(R.string.permission_action_open_shizuku),
                        onClick = onOpenShizuku,
                    )

                    !shizuku.granted -> PrivilegeAction(
                        icon = DdIcons.Shield,
                        title = stringResource(R.string.permission_action_grant_shizuku),
                        onClick = onRequestShizukuPermission,
                    )

                    else -> PrivilegeAction(
                        icon = DdIcons.External,
                        title = stringResource(R.string.permission_action_open_shizuku),
                        onClick = onOpenShizuku,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            PrivilegeHint(
                if (shizuku.granted && shizuku.uid == 0) {
                    R.string.permission_hint_shizuku_root
                } else {
                    R.string.permission_hint_shizuku
                },
            )
        }

        PrivilegeLevel.ROOT -> Column(Modifier.fillMaxWidth()) {
            PrivilegeHint(R.string.permission_hint_root)
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
        PrivilegeLevel.SHIZUKU -> R.string.permission_level_shizuku
        PrivilegeLevel.ROOT -> R.string.permission_level_root
    },
)

@Composable
private fun shizukuStatusText(shizuku: ShizukuState): String = when {
    shizuku.granted -> stringResource(
        R.string.permission_status_shizuku_granted,
        shizuku.version,
        shizuku.uid,
    )

    shizuku.running -> stringResource(
        R.string.permission_status_shizuku_denied,
        shizuku.version,
        shizuku.uid,
    )

    shizuku.installed -> stringResource(R.string.permission_status_shizuku_stopped)
    else -> stringResource(R.string.permission_status_shizuku_missing)
}

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
