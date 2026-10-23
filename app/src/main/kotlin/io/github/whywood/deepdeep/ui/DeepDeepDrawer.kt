package io.github.whywood.deepdeep.ui

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdMenu
import io.github.whywood.deepdeep.design.DdMenuItem
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.DdWordmark
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.runtime.RuntimeWorkspace
import io.github.whywood.deepdeep.ui.common.LocalHapticsEnabled
import io.github.whywood.deepdeep.ui.common.rememberRelativeTime
import io.github.whywood.deepdeep.ui.sessions.DeleteSessionDialog
import io.github.whywood.deepdeep.ui.sessions.RenameSessionDialog
import io.github.whywood.deepdeep.ui.sessions.SessionAction
import io.github.whywood.deepdeep.ui.sessions.SessionActionMenu

private sealed interface DrawerEntry {
    val key: String

    data object WorkspacesHeader : DrawerEntry {
        override val key: String = "workspaces_header"
    }

    data class WorkspaceEntry(val workspace: RuntimeWorkspace, val first: Boolean) : DrawerEntry {
        override val key: String = "workspace_${workspace.id}"
    }

    data class WorkspaceEmpty(val workspaceId: String) : DrawerEntry {
        override val key: String = "workspace_empty_$workspaceId"
    }

    data class Session(val conversation: ConversationSummary, val workspaceId: String) : DrawerEntry {
        override val key: String = "session_${workspaceId}_${conversation.id}"
    }
}

@Composable
fun DeepDeepDrawer(
    sessions: List<ConversationSummary>,
    archivedCount: Int,
    workspaces: List<RuntimeWorkspace>,
    pinnedWorkspaceIds: Set<String>,
    expandedWorkspaceIds: Set<String>,
    busySessionIds: Set<String>,
    userName: String,
    onChat: () -> Unit,
    onArchived: () -> Unit,
    onOpenSearch: () -> Unit,
    onCreateWorkspace: () -> Unit,
    onToggleWorkspaceExpanded: (String) -> Unit,
    onToggleWorkspacePinned: (String) -> Unit,
    onNewSessionInWorkspace: (String) -> Unit,
    onRenameWorkspace: (String) -> Unit,
    onDeleteWorkspace: (String) -> Unit,
    onConversation: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onSetPinned: (String, Boolean) -> Unit,
    onSetArchived: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val context = LocalContext.current
    val comingSoon = stringResource(R.string.drawer_coming_soon)
    var workspaceMenuTarget by remember { mutableStateOf<RuntimeWorkspace?>(null) }
    var workspaceMenuAnchor by remember { mutableStateOf(Offset.Zero) }
    var sessionMenuTarget by remember { mutableStateOf<ConversationSummary?>(null) }
    var sessionMenuAnchor by remember { mutableStateOf(Offset.Zero) }
    var renameTarget by remember { mutableStateOf<ConversationSummary?>(null) }
    var deleteTarget by remember { mutableStateOf<ConversationSummary?>(null) }
    val orderedWorkspaces = remember(workspaces, pinnedWorkspaceIds) {
        workspaces.filter { it.id in pinnedWorkspaceIds } + workspaces.filterNot { it.id in pinnedWorkspaceIds }
    }
    val sessionsByCwd = remember(sessions) {
        sessions.filterNot { it.archived }.groupBy { it.cwd }
    }
    val entries = remember(orderedWorkspaces, sessionsByCwd, expandedWorkspaceIds) {
        buildList {
            add(DrawerEntry.WorkspacesHeader)
            orderedWorkspaces.forEachIndexed { index, workspace ->
                add(DrawerEntry.WorkspaceEntry(workspace, index == 0))
                if (workspace.id in expandedWorkspaceIds) {
                    val workspaceSessions = sessionsByCwd[workspace.cwd].orEmpty()
                        .sortedWith(
                            compareByDescending<ConversationSummary> { it.pinned }
                                .thenByDescending { it.updatedAt },
                        )
                    if (workspaceSessions.isEmpty()) {
                        add(DrawerEntry.WorkspaceEmpty(workspace.id))
                    } else {
                        workspaceSessions.forEach { add(DrawerEntry.Session(it, workspace.id)) }
                    }
                }
            }
        }
    }
    Column(
        modifier
            .fillMaxHeight()
            .width(304.dp)
            .background(colors.paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .testTag("drawer_panel"),
    ) {
        Spacer(Modifier.height(22.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdWordmark(
                style = DdType.wordmark.copy(fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp),
            )
            Spacer(Modifier.weight(1f))
            DdIconButton(
                icon = DdIcons.Search,
                onClick = onOpenSearch,
                contentDescription = stringResource(R.string.drawer_search),
                modifier = Modifier.testTag("drawer_search"),
                size = 40.dp,
                iconSize = 20.dp,
                tint = colors.inkSecondary,
            )
        }
        Spacer(Modifier.height(18.dp))
        Column(Modifier.fillMaxWidth()) {
            DrawerNavItem(
                icon = DdIcons.Message,
                label = stringResource(R.string.drawer_nav_chat),
                onClick = onChat,
                testTag = "drawer_chat",
            )
            DrawerNavItem(
                icon = DdIcons.Folder,
                label = stringResource(R.string.drawer_nav_archived),
                onClick = onArchived,
                testTag = "drawer_archived",
                trailing = {
                    Text(
                        text = stringResource(R.string.drawer_archived_count, archivedCount),
                        style = DdType.numeric,
                        color = colors.inkTertiary,
                    )
                },
            )
            Spacer(Modifier.height(6.dp))
            DrawerNavItem(
                icon = DdIcons.Sparkles,
                label = stringResource(R.string.drawer_nav_skills),
                onClick = { Toast.makeText(context, comingSoon, Toast.LENGTH_SHORT).show() },
                testTag = "drawer_skills",
            )
            DrawerNavItem(
                icon = DdIcons.Plug,
                label = stringResource(R.string.drawer_nav_plugins),
                onClick = { Toast.makeText(context, comingSoon, Toast.LENGTH_SHORT).show() },
                testTag = "drawer_plugins",
            )
            DrawerNavItem(
                icon = DdIcons.Clock,
                label = stringResource(R.string.drawer_nav_scheduled),
                onClick = { Toast.makeText(context, comingSoon, Toast.LENGTH_SHORT).show() },
                testTag = "drawer_scheduled",
            )
        }
        Spacer(Modifier.height(18.dp))
        DdHairline(color = colors.hairline.copy(alpha = 0.45f))
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = 8.dp, bottom = 4.dp),
        ) {
            items(entries, key = { it.key }) { entry ->
                when (entry) {
                    DrawerEntry.WorkspacesHeader -> DrawerWorkspacesHeader(
                        onCreate = onCreateWorkspace,
                        modifier = Modifier.animateItem(),
                    )

                    is DrawerEntry.WorkspaceEntry -> DrawerWorkspaceRow(
                        workspace = entry.workspace,
                        first = entry.first,
                        pinned = entry.workspace.id in pinnedWorkspaceIds,
                        expanded = entry.workspace.id in expandedWorkspaceIds,
                        menuExpanded = workspaceMenuTarget?.id == entry.workspace.id,
                        menuAnchor = workspaceMenuAnchor,
                        modifier = Modifier.animateItem(),
                        onToggleExpanded = { onToggleWorkspaceExpanded(entry.workspace.id) },
                        onLongClick = { offset ->
                            workspaceMenuAnchor = offset
                            workspaceMenuTarget = entry.workspace
                        },
                        onDismissMenu = { workspaceMenuTarget = null },
                        onTogglePinned = { onToggleWorkspacePinned(entry.workspace.id) },
                        onNewSession = { onNewSessionInWorkspace(entry.workspace.id) },
                        onRename = { onRenameWorkspace(entry.workspace.id) },
                        onDelete = { onDeleteWorkspace(entry.workspace.id) },
                    )

                    is DrawerEntry.WorkspaceEmpty -> DrawerWorkspaceEmptyRow(
                        modifier = Modifier.animateItem(),
                    )

                    is DrawerEntry.Session -> DrawerSessionRow(
                        conversation = entry.conversation,
                        busy = entry.conversation.id in busySessionIds,
                        menuExpanded = sessionMenuTarget?.id == entry.conversation.id,
                        menuAnchor = sessionMenuAnchor,
                        modifier = Modifier.animateItem(),
                        onClick = { onConversation(entry.conversation.id) },
                        onLongClick = { offset ->
                            sessionMenuAnchor = offset
                            sessionMenuTarget = entry.conversation
                        },
                        onDismissMenu = { sessionMenuTarget = null },
                        onAction = { action ->
                            when (action) {
                                SessionAction.RENAME -> renameTarget = entry.conversation
                                SessionAction.TOGGLE_PIN ->
                                    onSetPinned(entry.conversation.id, !entry.conversation.pinned)

                                SessionAction.TOGGLE_ARCHIVE ->
                                    onSetArchived(entry.conversation.id, !entry.conversation.archived)

                                SessionAction.DELETE -> deleteTarget = entry.conversation
                            }
                        },
                        testTag = "recent_${entry.conversation.id}",
                    )
                }
            }
        }
        DdHairline(color = colors.hairline.copy(alpha = 0.45f))
        Spacer(Modifier.height(12.dp))
        DrawerAccountRow(userName = userName, onClick = onSettings)
        Spacer(Modifier.height(12.dp))
    }
    renameTarget?.let { session ->
        RenameSessionDialog(
            initialTitle = session.title,
            onDismiss = { renameTarget = null },
            onConfirm = { title ->
                renameTarget = null
                onRename(session.id, title)
            },
        )
    }
    deleteTarget?.let { session ->
        DeleteSessionDialog(
            sessionTitle = session.title,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                deleteTarget = null
                onDelete(session.id)
            },
        )
    }
}

@Composable
private fun DrawerWorkspacesHeader(
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.workspace_section),
            modifier = Modifier.weight(1f),
            style = DdType.chip,
            color = colors.inkTertiary,
            maxLines = 1,
        )
        DdIconButton(
            icon = DdIcons.Plus,
            onClick = onCreate,
            contentDescription = stringResource(R.string.workspace_create),
            modifier = Modifier.testTag("drawer_workspace_create"),
            size = 32.dp,
            iconSize = 16.dp,
            tint = colors.inkTertiary,
        )
    }
}

@Composable
private fun DrawerWorkspaceRow(
    workspace: RuntimeWorkspace,
    first: Boolean,
    pinned: Boolean,
    expanded: Boolean,
    menuExpanded: Boolean,
    menuAnchor: Offset,
    onToggleExpanded: () -> Unit,
    onLongClick: (Offset) -> Unit,
    onDismissMenu: () -> Unit,
    onTogglePinned: () -> Unit,
    onNewSession: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val haptic = LocalHapticFeedback.current
    val hapticsEnabled = LocalHapticsEnabled.current
    val density = LocalDensity.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val wash by animateFloatAsState(
        targetValue = if (pressed) 0.05f else 0f,
        animationSpec = if (pressed) snap() else tween(DdMotion.PRESS_OUT, easing = DdMotion.EaseOut),
        label = "workspace-row-wash",
    )
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(DdMotion.BASE, easing = DdMotion.EaseOut),
        label = "workspace-chevron",
    )
    Box(modifier.fillMaxWidth().padding(top = if (first) 0.dp else 6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.ink.copy(alpha = wash))
                .pointerInput(workspace.id) {
                    detectTapGestures(
                        onPress = { offset ->
                            val press = PressInteraction.Press(offset)
                            interaction.emit(press)
                            if (tryAwaitRelease()) {
                                interaction.emit(PressInteraction.Release(press))
                            } else {
                                interaction.emit(PressInteraction.Cancel(press))
                            }
                        },
                        onTap = { onToggleExpanded() },
                        onLongPress = { offset ->
                            if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongClick(offset)
                        },
                    )
                }
                .testTag("workspace_${workspace.id}")
                .padding(start = 10.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIcon(icon = DdIcons.Folder, tint = colors.inkTertiary, size = 16.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = workspace.name,
                modifier = Modifier.weight(1f),
                style = DdType.label.copy(fontSize = 15.sp, lineHeight = 20.sp),
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (pinned) {
                DdIcon(icon = DdIcons.Pin, tint = colors.inkTertiary, size = 12.dp)
                Spacer(Modifier.width(6.dp))
            }
            if (expanded) {
                DdIconButton(
                    icon = DdIcons.Plus,
                    onClick = onNewSession,
                    contentDescription = stringResource(R.string.drawer_workspace_new_session),
                    modifier = Modifier.testTag("workspace_new_session_${workspace.id}"),
                    size = 30.dp,
                    iconSize = 15.dp,
                    tint = colors.inkTertiary,
                )
                Spacer(Modifier.width(2.dp))
            }
            DdIcon(
                icon = DdIcons.ChevronRight,
                tint = colors.inkTertiary,
                size = 14.dp,
                modifier = Modifier
                    .graphicsLayer { rotationZ = chevronRotation }
                    .testTag("workspace_chevron_${workspace.id}"),
            )
        }
        DdMenu(
            expanded = menuExpanded,
            onDismissRequest = onDismissMenu,
            offset = with(density) { DpOffset(menuAnchor.x.toDp(), menuAnchor.y.toDp() - 24.dp) },
            menuWidth = 176.dp,
        ) {
            DdMenuItem(
                text = stringResource(
                    if (pinned) R.string.drawer_action_unpin else R.string.drawer_action_pin,
                ),
                onClick = {
                    onDismissMenu()
                    onTogglePinned()
                },
                icon = DdIcons.Pin,
                testTag = "workspace_action_pin",
            )
            DdMenuItem(
                text = stringResource(R.string.drawer_workspace_new_session),
                onClick = {
                    onDismissMenu()
                    onNewSession()
                },
                icon = DdIcons.Plus,
                testTag = "workspace_action_new_session",
            )
            if (!workspace.isHome) {
                DdMenuItem(
                    text = stringResource(R.string.drawer_action_rename),
                    onClick = {
                        onDismissMenu()
                        onRename()
                    },
                    icon = DdIcons.Compose,
                    testTag = "workspace_action_rename",
                )
                DdMenuItem(
                    text = stringResource(R.string.drawer_action_delete),
                    onClick = {
                        onDismissMenu()
                        onDelete()
                    },
                    icon = DdIcons.Trash,
                    destructive = true,
                    testTag = "workspace_action_delete",
                )
            }
        }
    }
}

@Composable
private fun DrawerWorkspaceEmptyRow(modifier: Modifier = Modifier) {
    val colors = deepDeepColors()
    Text(
        text = stringResource(R.string.drawer_workspace_empty),
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 34.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        style = DdType.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
        color = colors.inkTertiary,
        maxLines = 1,
    )
}

@Composable
private fun DrawerNavItem(
    icon: DdIcon,
    label: String,
    onClick: () -> Unit,
    testTag: String,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = deepDeepColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        DdIcon(
            icon = icon,
            tint = colors.inkSecondary,
            size = 20.dp,
        )
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = DdType.body.copy(fontSize = 16.sp, lineHeight = 22.sp),
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing?.invoke()
    }
}

@Composable
private fun DrawerSessionRow(
    conversation: ConversationSummary,
    busy: Boolean,
    menuExpanded: Boolean,
    menuAnchor: Offset,
    onClick: () -> Unit,
    onLongClick: (Offset) -> Unit,
    onDismissMenu: () -> Unit,
    onAction: (SessionAction) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val haptic = LocalHapticFeedback.current
    val hapticsEnabled = LocalHapticsEnabled.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val wash by animateFloatAsState(
        targetValue = if (pressed) 0.06f else 0f,
        animationSpec = if (pressed) snap() else tween(DdMotion.PRESS_OUT, easing = DdMotion.EaseOut),
        label = "session-row-wash",
    )
    val relativeTime = rememberRelativeTime(conversation.updatedAt)
    Box(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.ink.copy(alpha = wash))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { offset ->
                            val press = PressInteraction.Press(offset)
                            interaction.emit(press)
                            if (tryAwaitRelease()) {
                                interaction.emit(PressInteraction.Release(press))
                            } else {
                                interaction.emit(PressInteraction.Cancel(press))
                            }
                        },
                        onTap = { onClick() },
                        onLongPress = { offset ->
                            if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongClick(offset)
                        },
                    )
                }
                .testTag(testTag)
                .padding(start = 34.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIcon(icon = DdIcons.Message, tint = colors.inkTertiary, size = 13.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                text = conversation.title,
                modifier = Modifier.weight(1f),
                style = DdType.bodySmall.copy(fontSize = 13.5.sp, lineHeight = 18.sp),
                color = colors.inkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier.widthIn(min = 40.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                when {
                    busy -> DdSpinner(
                        modifier = Modifier.testTag("session_busy_${conversation.id}"),
                        color = colors.inkTertiary,
                        size = 12.dp,
                        strokeWidth = 1.5.dp,
                    )

                    conversation.pinned -> DdIcon(
                        icon = DdIcons.Pin,
                        tint = colors.inkTertiary,
                        size = 12.dp,
                    )

                    else -> Text(
                        text = relativeTime,
                        style = DdType.bodySmall.copy(fontSize = 11.sp),
                        color = colors.inkTertiary,
                        maxLines = 1,
                    )
                }
            }
        }
        SessionActionMenu(
            session = conversation,
            expanded = menuExpanded,
            onDismissRequest = onDismissMenu,
            onAction = onAction,
            anchor = menuAnchor,
        )
    }
}

@Composable
private fun DrawerAccountRow(
    userName: String,
    onClick: () -> Unit,
) {
    val colors = deepDeepColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("drawer_account")
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(colors.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = userName.take(1).uppercase().ifBlank { "D" },
                style = DdType.label,
                color = colors.accent,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = userName,
                style = DdType.label,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.drawer_account_hint),
                style = DdType.micro.copy(letterSpacing = 0.2.sp),
                color = colors.inkTertiary,
                maxLines = 1,
            )
        }
        DdIcon(icon = DdIcons.ChevronRight, tint = colors.inkTertiary, size = 15.dp)
    }
}
