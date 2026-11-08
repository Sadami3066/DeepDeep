package io.github.whywood.deepdeep.ui.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.remote.RemoteComputer
import io.github.whywood.deepdeep.data.remote.RemoteReachability
import io.github.whywood.deepdeep.data.remote.RemoteStatus
import io.github.whywood.deepdeep.design.DdEmptyState
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdMenu
import io.github.whywood.deepdeep.design.DdMenuItem
import io.github.whywood.deepdeep.design.DdPageScaffold
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdRowDivider
import io.github.whywood.deepdeep.design.DdShapes
import io.github.whywood.deepdeep.design.DdSpacing
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors

@Composable
internal fun RemoteScreen(
    statuses: List<RemoteStatus>,
    menuFor: String?,
    onBack: () -> Unit,
    onScan: () -> Unit,
    onPaste: () -> Unit,
    onGuide: () -> Unit,
    onOpen: (String) -> Unit,
    onMenuOpen: (String) -> Unit,
    onMenuDismiss: () -> Unit,
    onRename: (RemoteComputer) -> Unit,
    onRepair: () -> Unit,
    onDelete: (RemoteComputer) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    var addMenuOpen by remember { mutableStateOf(false) }
    val sorted = remember(statuses) {
        statuses.sortedByDescending { it.computer.lastUsedAt }
    }
    DdPageScaffold(
        topBar = {
            DdIconButton(
                icon = DdIcons.ArrowLeft,
                onClick = onBack,
                contentDescription = stringResource(R.string.remote_back),
                iconSize = 21.dp,
                tint = colors.ink,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.remote_title),
                modifier = Modifier.weight(1f),
                style = DdType.heading,
                color = colors.ink,
            )
            Box {
                DdIconButton(
                    icon = DdIcons.Plus,
                    onClick = { addMenuOpen = true },
                    contentDescription = stringResource(R.string.remote_add),
                    iconSize = 21.dp,
                    tint = colors.ink,
                )
                DdMenu(
                    expanded = addMenuOpen,
                    onDismissRequest = { addMenuOpen = false },
                    menuWidth = 192.dp,
                ) {
                    DdMenuItem(
                        text = stringResource(R.string.remote_scan),
                        icon = DdIcons.Scan,
                        onClick = {
                            addMenuOpen = false
                            onScan()
                        },
                    )
                    DdMenuItem(
                        text = stringResource(R.string.remote_scan_paste),
                        icon = DdIcons.Paperclip,
                        onClick = {
                            addMenuOpen = false
                            onPaste()
                        },
                    )
                    DdMenuItem(
                        text = stringResource(R.string.remote_usage_guide),
                        icon = DdIcons.Book,
                        onClick = {
                            addMenuOpen = false
                            onGuide()
                        },
                    )
                }
            }
        },
        modifier = modifier.testTag("remote_screen"),
    ) {
        if (sorted.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                DdEmptyState(
                    text = stringResource(R.string.remote_empty_title),
                    icon = DdIcons.Desktop,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = DdSpacing.Sm),
            ) {
                sorted.forEachIndexed { index, status ->
                    ComputerRow(
                        status = status,
                        menuExpanded = menuFor == status.computer.instanceId,
                        onOpen = { onOpen(status.computer.instanceId) },
                        onMenuOpen = { onMenuOpen(status.computer.instanceId) },
                        onMenuDismiss = onMenuDismiss,
                        onRename = { onRename(status.computer) },
                        onRepair = onRepair,
                        onDelete = { onDelete(status.computer) },
                    )
                    if (index != sorted.lastIndex) {
                        DdRowDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ComputerRow(
    status: RemoteStatus,
    menuExpanded: Boolean,
    onOpen: () -> Unit,
    onMenuOpen: () -> Unit,
    onMenuDismiss: () -> Unit,
    onRename: () -> Unit,
    onRepair: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = deepDeepColors()
    DdPressable(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = DdShapes.Row,
        contentDescription = status.computer.name,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIcon(
                icon = DdIcons.Desktop,
                size = 22.dp,
                tint = colors.inkSecondary,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    text = status.computer.name,
                    style = DdType.body,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(statusDotColor(status.reachability)),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(status.reachability.labelRes()),
                        style = DdType.bodySmall,
                        color = colors.inkTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box {
                DdIconButton(
                    icon = DdIcons.DotsVertical,
                    onClick = onMenuOpen,
                    contentDescription = stringResource(R.string.remote_menu),
                    iconSize = 18.dp,
                    tint = colors.inkTertiary,
                )
                DdMenu(
                    expanded = menuExpanded,
                    onDismissRequest = onMenuDismiss,
                    menuWidth = 168.dp,
                ) {
                    DdMenuItem(
                        text = stringResource(R.string.remote_menu_rename),
                        icon = DdIcons.Compose,
                        onClick = {
                            onMenuDismiss()
                            onRename()
                        },
                    )
                    DdMenuItem(
                        text = stringResource(R.string.remote_menu_repair),
                        icon = DdIcons.Scan,
                        onClick = {
                            onMenuDismiss()
                            onRepair()
                        },
                    )
                    DdMenuItem(
                        text = stringResource(R.string.remote_menu_delete),
                        icon = DdIcons.Trash,
                        destructive = true,
                        onClick = {
                            onMenuDismiss()
                            onDelete()
                        },
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            DdIcon(
                icon = DdIcons.ChevronRight,
                tint = colors.inkTertiary,
                size = 15.dp,
            )
        }
    }
}

@Composable
private fun statusDotColor(reachability: RemoteReachability): Color {
    val colors = deepDeepColors()
    return when (reachability) {
        RemoteReachability.OnlineLan,
        RemoteReachability.OnlinePublic,
        -> colors.success
        RemoteReachability.NeedsPairing,
        RemoteReachability.Offline,
        -> colors.danger
        RemoteReachability.CertChanged -> colors.warning
        RemoteReachability.Unknown -> colors.inkTertiary
    }
}
