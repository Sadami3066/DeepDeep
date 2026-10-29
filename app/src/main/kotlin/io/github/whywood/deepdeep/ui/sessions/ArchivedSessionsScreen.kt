package io.github.whywood.deepdeep.ui.sessions

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.DdSearchField
import io.github.whywood.deepdeep.ui.common.LocalHapticsEnabled
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.ConversationSummary

@Composable
fun ArchivedSessionsScreen(
    archivedSessions: List<ConversationSummary>,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onUnarchive: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    var menuTarget by remember { mutableStateOf<ConversationSummary?>(null) }
    var menuAnchor by remember { mutableStateOf(Offset.Zero) }
    var renameTarget by remember { mutableStateOf<ConversationSummary?>(null) }
    var deleteTarget by remember { mutableStateOf<ConversationSummary?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val ordered = remember(archivedSessions) { archivedSessions.sortedByDescending { it.updatedAt } }
    val filtered = remember(ordered, query) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) ordered else ordered.filter { it.title.contains(trimmed, ignoreCase = true) }
    }
    BackHandler(onBack = onBack)
    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("archived_sessions"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIconButton(
                icon = DdIcons.ArrowLeft,
                onClick = onBack,
                contentDescription = stringResource(R.string.drawer_back),
                modifier = Modifier.testTag("archived_back"),
                tint = colors.ink,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.drawer_archived_title),
                modifier = Modifier.weight(1f),
                style = DdType.heading,
                color = colors.ink,
            )
            if (ordered.isNotEmpty()) {
                Text(
                    text = "${ordered.size}",
                    modifier = Modifier.padding(end = 16.dp),
                    style = DdType.numeric,
                    color = colors.inkTertiary,
                )
            }
        }
        if (ordered.isEmpty()) {
            Text(
                text = stringResource(R.string.drawer_archived_empty),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
                style = DdType.body,
                color = colors.inkTertiary,
            )
        } else {
            DdSearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = stringResource(R.string.drawer_search_archived_placeholder),
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("archived_search"),
                clearContentDescription = stringResource(R.string.drawer_search_clear),
                fieldTestTag = "archived_search_field",
                clearTestTag = "archived_search_clear",
            )
            if (filtered.isEmpty()) {
                Text(
                    text = stringResource(R.string.drawer_search_empty),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    items(filtered, key = { it.id }) { session ->
                        ArchivedSessionRow(
                            session = session,
                            menuExpanded = menuTarget?.id == session.id,
                            onClick = { onOpen(session.id) },
                            onLongClick = { offset ->
                                menuAnchor = offset
                                menuTarget = session
                            },
                            menuAnchor = menuAnchor,
                            onDismissMenu = { menuTarget = null },
                            onAction = { action ->
                                when (action) {
                                    SessionAction.RENAME -> renameTarget = session
                                    SessionAction.TOGGLE_PIN -> Unit
                                    SessionAction.TOGGLE_ARCHIVE -> onUnarchive(session.id)
                                    SessionAction.DELETE -> deleteTarget = session
                                }
                            },
                            testTag = "archived_${session.id}",
                        )
                    }
                }
            }
        }
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
private fun ArchivedSessionRow(
    session: ConversationSummary,
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
        label = "archived-row-wash",
    )
    Box(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
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
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = session.title,
                style = DdType.body,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SessionActionMenu(
            session = session,
            expanded = menuExpanded,
            showPin = false,
            onDismissRequest = onDismissMenu,
            onAction = onAction,
            anchor = menuAnchor,
        )
    }
}

