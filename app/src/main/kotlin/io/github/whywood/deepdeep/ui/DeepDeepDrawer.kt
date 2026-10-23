package io.github.whywood.deepdeep.ui

import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.DdWordmark
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.ui.common.LocalHapticsEnabled
import io.github.whywood.deepdeep.runtime.RuntimeWorkspace
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

    data class WorkspaceEntry(val workspace: RuntimeWorkspace) : DrawerEntry {
        override val key: String = "workspace_${workspace.id}"
    }

    data class Header(val title: String, override val key: String) : DrawerEntry

    data class Session(val conversation: ConversationSummary) : DrawerEntry {
        override val key: String = conversation.id
    }

    data object HistoryEmpty : DrawerEntry {
        override val key: String = "history_empty"
    }

    data object HistoryLoading : DrawerEntry {
        override val key: String = "history_loading"
    }
}

@Composable
fun DeepDeepDrawer(
    conversations: List<ConversationSummary>,
    activeConversationId: String?,
    historyLoaded: Boolean,
    archivedCount: Int,
    workspaces: List<RuntimeWorkspace>,
    currentWorkspaceId: String,
    busySessionIds: Set<String>,
    userName: String,
    onChat: () -> Unit,
    onArchived: () -> Unit,
    onNewChat: () -> Unit,
    onOpenSearch: () -> Unit,
    onCreateWorkspace: () -> Unit,
    onSelectWorkspace: (String) -> Unit,
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
    val pinnedLabel = stringResource(R.string.drawer_pinned_section)
    val historyLabel = stringResource(R.string.drawer_history_section)
    var workspaceMenuTarget by remember { mutableStateOf<RuntimeWorkspace?>(null) }
    var menuTarget by remember { mutableStateOf<ConversationSummary?>(null) }
    var menuAnchor by remember { mutableStateOf(Offset.Zero) }
    var renameTarget by remember { mutableStateOf<ConversationSummary?>(null) }
    var deleteTarget by remember { mutableStateOf<ConversationSummary?>(null) }
    val pinned = remember(conversations) {
        conversations.filter { it.pinned && !it.archived }.sortedByDescending { it.updatedAt }
    }
    val recent = remember(conversations) {
        conversations.filter { !it.pinned && !it.archived }.sortedByDescending { it.updatedAt }
    }
    val entries = remember(workspaces, conversations, pinned, recent, historyLoaded, pinnedLabel, historyLabel) {
        buildList {
            add(DrawerEntry.WorkspacesHeader)
            workspaces.forEach { add(DrawerEntry.WorkspaceEntry(it)) }
            if (conversations.isEmpty()) {
                add(if (historyLoaded) DrawerEntry.HistoryEmpty else DrawerEntry.HistoryLoading)
            } else {
                if (pinned.isNotEmpty()) {
                    add(DrawerEntry.Header(pinnedLabel, "pinned_header"))
                    pinned.forEach { add(DrawerEntry.Session(it)) }
                }
                if (recent.isNotEmpty()) {
                    add(DrawerEntry.Header(historyLabel, "recent_header"))
                    recent.forEach { add(DrawerEntry.Session(it)) }
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
            DrawerNavItem(
                icon = DdIcons.Plus,
                label = stringResource(R.string.drawer_nav_new_chat),
                onClick = onNewChat,
                testTag = "drawer_new_chat",
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
                    DrawerEntry.WorkspacesHeader -> DrawerWorkspacesHeader(onCreate = onCreateWorkspace)
                    is DrawerEntry.WorkspaceEntry -> DrawerWorkspaceRow(
                        workspace = entry.workspace,
                        selected = entry.workspace.id == currentWorkspaceId,
                        menuExpanded = workspaceMenuTarget?.id == entry.workspace.id,
                        onClick = { onSelectWorkspace(entry.workspace.id) },
                        onLongClick = { workspaceMenuTarget = entry.workspace },
                        onDismissMenu = { workspaceMenuTarget = null },
                        onRename = { onRenameWorkspace(entry.workspace.id) },
                        onDelete = { onDeleteWorkspace(entry.workspace.id) },
                    )

                    is DrawerEntry.Header -> DrawerSectionLabel(entry.title)
                    is DrawerEntry.Session -> DrawerHistoryRow(
                        conversation = entry.conversation,
                        busy = entry.conversation.id in busySessionIds,
                        menuExpanded = menuTarget?.id == entry.conversation.id,
                        onClick = { onConversation(entry.conversation.id) },
                        onLongClick = { offset ->
                            menuAnchor = offset
                            menuTarget = entry.conversation
                        },
                        menuAnchor = menuAnchor,
                        onDismissMenu = { menuTarget = null },
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

                    DrawerEntry.HistoryEmpty -> DrawerHistoryHint(stringResource(R.string.drawer_history_empty))
                    DrawerEntry.HistoryLoading -> DrawerHistoryHint(stringResource(R.string.drawer_history_loading))
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
private fun DrawerWorkspacesHeader(onCreate: () -> Unit) {
    val colors = deepDeepColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.workspace_section),
            modifier = Modifier.weight(1f),
            style = DdType.bodySmall,
            color = colors.inkSecondary.copy(alpha = 0.85f),
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerWorkspaceRow(
    workspace: RuntimeWorkspace,
    selected: Boolean,
    menuExpanded: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDismissMenu: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = deepDeepColors()
    Box(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (selected) colors.wash else colors.paper)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = if (workspace.isHome) null else onLongClick,
                )
                .testTag("workspace_${workspace.id}")
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = workspace.name,
                style = DdType.bodySmall,
                color = if (selected) colors.ink else colors.inkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DdMenu(
            expanded = menuExpanded,
            onDismissRequest = onDismissMenu,
            menuWidth = 168.dp,
        ) {
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

@Composable
private fun DrawerHistoryHint(text: String) {
    val colors = deepDeepColors()
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
        style = DdType.bodySmall,
        color = colors.inkTertiary,
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
private fun DrawerSectionLabel(text: String) {
    val colors = deepDeepColors()
    Text(
        text = text,
        modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 4.dp),
        style = DdType.bodySmall,
        color = colors.inkSecondary.copy(alpha = 0.85f),
    )
}

@Composable
private fun DrawerHistoryRow(
    conversation: ConversationSummary,
    busy: Boolean,
    menuExpanded: Boolean,
    onClick: () -> Unit,
    onLongClick: (Offset) -> Unit,
    menuAnchor: Offset,
    onDismissMenu: () -> Unit,
    onAction: (SessionAction) -> Unit,
    testTag: String,
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
    Box(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
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
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = conversation.title,
                modifier = Modifier.weight(1f),
                style = DdType.bodySmall,
                color = colors.inkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier.widthIn(min = 40.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Crossfade(
                    targetState = busy,
                    animationSpec = tween(DdMotion.FAST),
                    label = "session-trailing",
                ) { running ->
                    if (running) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(12.dp)
                                .testTag("session_busy_${conversation.id}"),
                            color = colors.inkTertiary,
                            strokeWidth = 1.5.dp,
                        )
                    } else {
                        Text(
                            text = relativeTime,
                            style = DdType.bodySmall.copy(fontSize = 11.sp),
                            color = colors.inkTertiary,
                            maxLines = 1,
                        )
                    }
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
