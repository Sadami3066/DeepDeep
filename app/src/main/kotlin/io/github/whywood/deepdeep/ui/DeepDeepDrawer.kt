package io.github.whywood.deepdeep.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.DdWordmark
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.ConversationSummary

@Composable
fun DeepDeepDrawer(
    conversations: List<ConversationSummary>,
    activeConversationId: String?,
    historyLoaded: Boolean,
    userName: String,
    onChat: () -> Unit,
    onConversation: (String) -> Unit,
    onNewChat: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
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
        Spacer(Modifier.height(28.dp))
        Column(Modifier.selectableGroup()) {
            DrawerNavItem(
                icon = DdIcons.Message,
                label = stringResource(R.string.drawer_chat),
                selected = true,
                onClick = onChat,
                testTag = "drawer_chat",
            )
        }
        Spacer(Modifier.height(22.dp))
        DdHairline()
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.drawer_recent),
            style = DdType.bodySmall,
            color = colors.inkTertiary,
        )
        Spacer(Modifier.height(6.dp))
        if (conversations.isEmpty()) {
            Text(
                text = stringResource(
                    if (historyLoaded) R.string.drawer_recent_empty else R.string.drawer_recent_loading,
                ),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                style = DdType.bodySmall,
                color = colors.inkTertiary,
            )
            Spacer(Modifier.weight(1f))
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(conversations, key = { it.id }) { conversation ->
                    RecentConversationRow(
                        title = conversation.title,
                        selected = conversation.id == activeConversationId,
                        onClick = { onConversation(conversation.id) },
                        testTag = "recent_${conversation.id}",
                    )
                }
            }
        }
        DdHairline()
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onSettings)
                    .testTag("drawer_account")
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(38.dp)
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
                        text = stringResource(R.string.drawer_settings_hint),
                        style = DdType.micro.copy(letterSpacing = 0.2.sp),
                        color = colors.inkTertiary,
                        maxLines = 1,
                    )
                }
                DdIcon(icon = DdIcons.ChevronRight, tint = colors.inkTertiary, size = 15.dp)
            }
            Spacer(Modifier.width(6.dp))
            DdPressable(
                onClick = onNewChat,
                modifier = Modifier.height(46.dp).testTag("drawer_new_chat"),
                shape = RoundedCornerShape(16.dp),
                background = colors.accent,
                contentDescription = stringResource(R.string.drawer_new_chat),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    DdIcon(icon = DdIcons.Plus, tint = colors.onAccent, size = 15.dp)
                    Text(
                        text = stringResource(R.string.drawer_new_chat),
                        style = DdType.chip,
                        color = colors.onAccent,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun DrawerNavItem(
    icon: DdIcon,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    val colors = deepDeepColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) colors.accentSoft else colors.raised)
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick,
            )
            .testTag(testTag)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        DdIcon(
            icon = icon,
            tint = if (selected) colors.accent else colors.inkSecondary,
            size = 21.dp,
        )
        Text(
            text = label,
            style = DdType.body.copy(fontSize = 17.sp, lineHeight = 24.sp),
            color = if (selected) colors.accent else colors.ink,
        )
    }
}

@Composable
private fun RecentConversationRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    val colors = deepDeepColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.wash else colors.raised)
            .clickable(onClick = onClick)
            .testTag(testTag)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DdIcon(
            icon = DdIcons.Message,
            tint = if (selected) colors.accent else colors.inkTertiary,
            size = 16.dp,
        )
        Text(
            text = title,
            style = DdType.bodySmall,
            color = if (selected) colors.ink else colors.inkSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
