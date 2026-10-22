package io.github.whywood.deepdeep.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.DdWordmark
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.ui.sessions.SessionActionsSheet

@OptIn(ExperimentalFoundationApi::class)
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
    var actionTarget by remember { mutableStateOf<ConversationSummary?>(null) }
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
            .background(colors.raised)
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
                selected = true,
                onClick = onChat,
                testTag = "drawer_chat",
            )
            DrawerNavItem(
                icon = DdIcons.Folder,
                label = stringResource(R.string.drawer_nav_archived),
                selected = false,
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
                selected = false,
                onClick = onNewChat,
                testTag = "drawer_new_chat",
            )
        }
        Spacer(Modifier.height(18.dp))
        DdHairline()
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
                            selected = conversation.id == activeConversationId,
                            onClick = { onConversation(conversation.id) },
                            onLongClick = { actionTarget = conversation },
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
                            selected = conversation.id == activeConversationId,
                            onClick = { onConversation(conversation.id) },
                            onLongClick = { actionTarget = conversation },
                            testTag = "recent_${conversation.id}",
                        )
                    }
                }
            }
        }
        DdHairline()
        Spacer(Modifier.height(12.dp))
        DrawerAccountRow(userName = userName, onClick = onSettings)
        Spacer(Modifier.height(12.dp))
    }
    actionTarget?.let { session ->
        SessionActionsSheet(
            session = session,
            onDismiss = { actionTarget = null },
            onRename = { title -> onRename(session.id, title) },
            onTogglePinned = { onSetPinned(session.id, !session.pinned) },
            onToggleArchived = { onSetArchived(session.id, !session.archived) },
            onDelete = { onDelete(session.id) },
        )
    }
}

@Composable
private fun DrawerNavItem(
    icon: DdIcon,
    label: String,
    selected: Boolean,
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
            .background(if (selected) colors.accentSoft else colors.raised)
            .clickable(onClick = onClick)
            .testTag(testTag)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        DdIcon(
            icon = icon,
            tint = if (selected) colors.accent else colors.inkSecondary,
            size = 20.dp,
        )
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = DdType.body.copy(fontSize = 16.sp, lineHeight = 22.sp),
            color = if (selected) colors.accent else colors.ink,
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
        color = colors.inkTertiary,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerHistoryRow(
    conversation: ConversationSummary,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    testTag: String,
) {
    val colors = deepDeepColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.accentSoft else colors.raised)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag(testTag)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = conversation.title,
            style = DdType.bodySmall,
            color = if (selected) colors.accent else colors.inkSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
