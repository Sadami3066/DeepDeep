package io.github.whywood.deepdeep.ui

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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.DdWordmark
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.ui.sessions.DeleteSessionDialog
import io.github.whywood.deepdeep.ui.sessions.RenameSessionDialog
import io.github.whywood.deepdeep.ui.sessions.SessionAction
import io.github.whywood.deepdeep.ui.sessions.SessionActionMenu

@Composable
fun DeepDeepDrawer(
    conversations: List<ConversationSummary>,
    activeConversationId: String?,
    historyLoaded: Boolean,
    archivedCount: Int,
    userName: String,
    onChat: () -> Unit,
    onArchived: () -> Unit,
    onNewChat: () -> Unit,
    onConversation: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onSetPinned: (String, Boolean) -> Unit,
    onSetArchived: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
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
        DdWordmark(
            style = DdType.wordmark.copy(fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp),
        )
        Spacer(Modifier.height(24.dp))
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
        }
        Spacer(Modifier.height(18.dp))
        DdHairline(color = colors.hairline.copy(alpha = 0.45f))
        Spacer(Modifier.height(14.dp))
        if (conversations.isEmpty()) {
            Text(
                text = stringResource(
                    if (historyLoaded) R.string.drawer_history_empty else R.string.drawer_history_loading,
                ),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                style = DdType.bodySmall,
                color = colors.inkTertiary,
            )
            Spacer(Modifier.weight(1f))
        } else {
            LazyColumn(Modifier.weight(1f)) {
                if (pinned.isNotEmpty()) {
                    item(key = "pinned_header") {
                        DrawerSectionLabel(stringResource(R.string.drawer_pinned_section))
                    }
                    items(pinned, key = { it.id }) { conversation ->
                        DrawerHistoryRow(
                            conversation = conversation,
                            menuExpanded = menuTarget?.id == conversation.id,
                            onClick = { onConversation(conversation.id) },
                            onLongClick = { offset ->
                                menuAnchor = offset
                                menuTarget = conversation
                            },
                            menuAnchor = menuAnchor,
                            onDismissMenu = { menuTarget = null },
                            onAction = { action ->
                                when (action) {
                                    SessionAction.RENAME -> renameTarget = conversation
                                    SessionAction.TOGGLE_PIN -> onSetPinned(conversation.id, !conversation.pinned)
                                    SessionAction.TOGGLE_ARCHIVE ->
                                        onSetArchived(conversation.id, !conversation.archived)

                                    SessionAction.DELETE -> deleteTarget = conversation
                                }
                            },
                            testTag = "recent_${conversation.id}",
                        )
                    }
                }
                if (recent.isNotEmpty()) {
                    item(key = "recent_header") {
                        DrawerSectionLabel(stringResource(R.string.drawer_history_section))
                    }
                    items(recent, key = { it.id }) { conversation ->
                        DrawerHistoryRow(
                            conversation = conversation,
                            menuExpanded = menuTarget?.id == conversation.id,
                            onClick = { onConversation(conversation.id) },
                            onLongClick = { offset ->
                                menuAnchor = offset
                                menuTarget = conversation
                            },
                            menuAnchor = menuAnchor,
                            onDismissMenu = { menuTarget = null },
                            onAction = { action ->
                                when (action) {
                                    SessionAction.RENAME -> renameTarget = conversation
                                    SessionAction.TOGGLE_PIN -> onSetPinned(conversation.id, !conversation.pinned)
                                    SessionAction.TOGGLE_ARCHIVE ->
                                        onSetArchived(conversation.id, !conversation.archived)

                                    SessionAction.DELETE -> deleteTarget = conversation
                                }
                            },
                            testTag = "recent_${conversation.id}",
                        )
                    }
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
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val wash by animateFloatAsState(
        targetValue = if (pressed) 0.06f else 0f,
        animationSpec = if (pressed) snap() else tween(DdMotion.PRESS_OUT, easing = DdMotion.EaseOut),
        label = "session-row-wash",
    )
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
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                style = DdType.bodySmall,
                color = colors.inkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
