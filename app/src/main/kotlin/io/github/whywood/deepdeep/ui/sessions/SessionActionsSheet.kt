package io.github.whywood.deepdeep.ui.sessions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.ConversationSummary

private enum class SessionSheetDialog { RENAME, DELETE }

@Composable
fun SessionActionsSheet(
    session: ConversationSummary,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onTogglePinned: () -> Unit,
    onToggleArchived: () -> Unit,
    onDelete: () -> Unit,
    showPin: Boolean = true,
) {
    val colors = deepDeepColors()
    var dialog by remember(session.id) { mutableStateOf<SessionSheetDialog?>(null) }
    when (dialog) {
        null -> DdSheet(onDismiss = onDismiss) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(start = 24.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = session.title,
                    modifier = Modifier.weight(1f),
                    style = DdType.heading,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                DdIconButton(
                    icon = DdIcons.Close,
                    onClick = onDismiss,
                    contentDescription = stringResource(R.string.drawer_close),
                    tint = colors.inkSecondary,
                    iconSize = 20.dp,
                )
            }
            Spacer(Modifier.height(4.dp))
            SheetActionRow(
                text = stringResource(R.string.drawer_action_rename),
                testTag = "session_action_rename",
                onClick = { dialog = SessionSheetDialog.RENAME },
            )
            if (showPin) {
                SheetActionRow(
                    text = stringResource(
                        if (session.pinned) R.string.drawer_action_unpin else R.string.drawer_action_pin,
                    ),
                    testTag = "session_action_pin",
                    onClick = {
                        onTogglePinned()
                        onDismiss()
                    },
                )
            }
            SheetActionRow(
                text = stringResource(
                    if (session.archived) R.string.drawer_action_unarchive else R.string.drawer_action_archive,
                ),
                testTag = "session_action_archive",
                onClick = {
                    onToggleArchived()
                    onDismiss()
                },
            )
            SheetActionRow(
                text = stringResource(R.string.drawer_action_delete),
                testTag = "session_action_delete",
                onClick = { dialog = SessionSheetDialog.DELETE },
            )
            Spacer(Modifier.height(12.dp))
        }

        SessionSheetDialog.RENAME -> RenameSessionDialog(
            initialTitle = session.title,
            onDismiss = onDismiss,
            onConfirm = { title ->
                onRename(title)
                onDismiss()
            },
        )

        SessionSheetDialog.DELETE -> DeleteSessionDialog(
            sessionTitle = session.title,
            onDismiss = onDismiss,
            onConfirm = {
                onDelete()
                onDismiss()
            },
        )
    }
}

@Composable
private fun SheetActionRow(
    text: String,
    testTag: String,
    onClick: () -> Unit,
) {
    val colors = deepDeepColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = DdType.body,
            color = colors.ink,
        )
    }
}
