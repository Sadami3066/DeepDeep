package io.github.whywood.deepdeep.ui.sessions

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdMenu
import io.github.whywood.deepdeep.design.DdMenuItem
import io.github.whywood.deepdeep.runtime.ConversationSummary

enum class SessionAction { RENAME, TOGGLE_PIN, TOGGLE_ARCHIVE, DELETE }

@Composable
fun SessionActionMenu(
    session: ConversationSummary,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onAction: (SessionAction) -> Unit,
    showPin: Boolean = true,
    anchor: Offset = Offset.Zero,
) {
    val density = LocalDensity.current
    DdMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = with(density) { DpOffset(anchor.x.toDp(), anchor.y.toDp() - 24.dp) },
        menuWidth = 168.dp,
    ) {
        MenuAction(
            icon = DdIcons.Compose,
            text = stringResource(R.string.drawer_action_rename),
            testTag = "session_action_rename",
            onClick = {
                onDismissRequest()
                onAction(SessionAction.RENAME)
            },
        )
        if (showPin) {
            MenuAction(
                icon = DdIcons.Pin,
                text = stringResource(
                    if (session.pinned) R.string.drawer_action_unpin else R.string.drawer_action_pin,
                ),
                testTag = "session_action_pin",
                onClick = {
                    onDismissRequest()
                    onAction(SessionAction.TOGGLE_PIN)
                },
            )
        }
        MenuAction(
            icon = DdIcons.Archive,
            text = stringResource(
                if (session.archived) R.string.drawer_action_unarchive else R.string.drawer_action_archive,
            ),
            testTag = "session_action_archive",
            onClick = {
                onDismissRequest()
                onAction(SessionAction.TOGGLE_ARCHIVE)
            },
        )
        MenuAction(
            icon = DdIcons.Trash,
            destructive = true,
            text = stringResource(R.string.drawer_action_delete),
            testTag = "session_action_delete",
            onClick = {
                onDismissRequest()
                onAction(SessionAction.DELETE)
            },
        )
    }
}

@Composable
private fun MenuAction(
    text: String,
    testTag: String,
    onClick: () -> Unit,
    icon: DdIcon? = null,
    destructive: Boolean = false,
) {
    DdMenuItem(
        text = text,
        onClick = onClick,
        icon = icon,
        destructive = destructive,
        testTag = testTag,
    )
}
