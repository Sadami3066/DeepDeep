package io.github.whywood.deepdeep.ui.sessions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
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
    var actionTarget by remember { mutableStateOf<ConversationSummary?>(null) }
    val ordered = remember(archivedSessions) { archivedSessions.sortedByDescending { it.updatedAt } }
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                items(ordered, key = { it.id }) { session ->
                    ArchivedSessionRow(
                        title = session.title,
                        onClick = { onOpen(session.id) },
                        onLongClick = { actionTarget = session },
                        testTag = "archived_${session.id}",
                    )
                }
            }
        }
    }
    actionTarget?.let { session ->
        SessionActionsSheet(
            session = session,
            showPin = false,
            onDismiss = { actionTarget = null },
            onRename = { title -> onRename(session.id, title) },
            onTogglePinned = {},
            onToggleArchived = { onUnarchive(session.id) },
            onDelete = { onDelete(session.id) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArchivedSessionRow(
    title: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    testTag: String,
) {
    val colors = deepDeepColors()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag(testTag)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = title,
            style = DdType.body,
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
