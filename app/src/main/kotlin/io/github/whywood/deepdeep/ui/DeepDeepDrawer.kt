package io.github.whywood.deepdeep.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.DdWordmark
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.ui.sessions.DeleteSessionDialog
import io.github.whywood.deepdeep.ui.sessions.RenameSessionDialog
import io.github.whywood.deepdeep.ui.sessions.SessionAction
import io.github.whywood.deepdeep.ui.sessions.SessionActionMenu

private sealed interface DrawerEntry {
    val key: String

    data class Header(val title: String, override val key: String) : DrawerEntry

    data class Session(val conversation: ConversationSummary) : DrawerEntry {
        override val key: String = conversation.id
    }
}

@Composable
fun DeepDeepDrawer(
    conversations: List<ConversationSummary>,
    activeConversationId: String?,
    historyLoaded: Boolean,
    archivedCount: Int,
    currentProjectName: String,
    onOpenProjects: () -> Unit,
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
    val context = LocalContext.current
    val comingSoon = stringResource(R.string.drawer_coming_soon)
    val pinnedLabel = stringResource(R.string.drawer_pinned_section)
    val historyLabel = stringResource(R.string.drawer_history_section)
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
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
    val query = searchQuery.trim()
    val filtering = query.isNotEmpty()
    val visiblePinned = remember(pinned, query) {
        if (query.isEmpty()) pinned else pinned.filter { it.title.contains(query, ignoreCase = true) }
    }
    val visibleRecent = remember(recent, query) {
        if (query.isEmpty()) recent else recent.filter { it.title.contains(query, ignoreCase = true) }
    }
    val entries = remember(visiblePinned, visibleRecent, filtering, pinnedLabel, historyLabel) {
        buildList {
            if (filtering) {
                visiblePinned.forEach { add(DrawerEntry.Session(it)) }
                visibleRecent.forEach { add(DrawerEntry.Session(it)) }
            } else {
                if (visiblePinned.isNotEmpty()) {
                    add(DrawerEntry.Header(pinnedLabel, "pinned_header"))
                    visiblePinned.forEach { add(DrawerEntry.Session(it)) }
                }
                if (visibleRecent.isNotEmpty()) {
                    add(DrawerEntry.Header(historyLabel, "recent_header"))
                    visibleRecent.forEach { add(DrawerEntry.Session(it)) }
                }
            }
        }
    }
    val closeSearch = {
        searchOpen = false
        searchQuery = ""
    }
    BackHandler(enabled = searchOpen, onBack = closeSearch)
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
        Spacer(Modifier.height(6.dp))
        DdPressable(
            onClick = onOpenProjects,
            modifier = Modifier.fillMaxWidth().testTag("drawer_projects"),
            background = colors.wash,
            shape = RoundedCornerShape(10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DdIcon(icon = DdIcons.Folder, tint = colors.inkSecondary, size = 15.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.drawer_project_current, currentProjectName),
                    style = DdType.bodySmall,
                    color = colors.inkSecondary,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (conversations.isNotEmpty() || searchOpen) {
            Spacer(Modifier.height(6.dp))
            if (searchOpen) {
                DrawerSearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onClose = closeSearch,
                )
            } else {
                DrawerSearchRow(onOpen = { searchOpen = true })
            }
        } else {
            Spacer(Modifier.height(14.dp))
        }
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
        } else if (filtering && entries.isEmpty()) {
            Text(
                text = stringResource(R.string.drawer_search_empty),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                style = DdType.bodySmall,
                color = colors.inkTertiary,
            )
            Spacer(Modifier.weight(1f))
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(entries, key = { it.key }) { entry ->
                    when (entry) {
                        is DrawerEntry.Header -> DrawerSectionLabel(entry.title)
                        is DrawerEntry.Session -> DrawerHistoryRow(
                            conversation = entry.conversation,
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
private fun DrawerSearchRow(onOpen: () -> Unit) {
    val colors = deepDeepColors()
    val searchLabel = stringResource(R.string.drawer_search)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.wash)
            .clickable(onClick = onOpen)
            .semantics { this.contentDescription = searchLabel }
            .testTag("drawer_search_toggle")
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.drawer_search_placeholder),
            style = DdType.bodySmall,
            color = colors.inkTertiary,
            maxLines = 1,
        )
    }
}

@Composable
private fun DrawerSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val colors = deepDeepColors()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        withFrameNanos { }
        focusRequester.requestFocus()
        keyboard?.show()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.raised)
                .border(1.dp, colors.hairline.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                            onClose()
                            true
                        } else {
                            false
                        }
                    }
                    .testTag("drawer_search_field"),
                textStyle = DdType.bodySmall.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.accent),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.drawer_search_placeholder),
                            style = DdType.bodySmall,
                            color = colors.inkTertiary,
                        )
                    }
                    inner()
                },
            )
            if (query.isNotEmpty()) {
                DdIconButton(
                    icon = DdIcons.Close,
                    onClick = { onQueryChange("") },
                    contentDescription = stringResource(R.string.drawer_search_clear),
                    modifier = Modifier.testTag("drawer_search_clear"),
                    size = 32.dp,
                    iconSize = 15.dp,
                    tint = colors.inkTertiary,
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        DdTextAction(
            text = stringResource(R.string.drawer_cancel),
            onClick = onClose,
            modifier = Modifier.testTag("drawer_search_cancel"),
            color = colors.inkSecondary,
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
