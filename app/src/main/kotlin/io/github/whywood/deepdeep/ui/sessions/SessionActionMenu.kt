package io.github.whywood.deepdeep.ui.sessions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.design.DdPressable
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
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
    val colors = deepDeepColors()
    val density = LocalDensity.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        containerColor = colors.raised,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        shape = RoundedCornerShape(14.dp),
        offset = with(density) { DpOffset(anchor.x.toDp(), anchor.y.toDp() - 24.dp) },
    ) {
        Box(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
            androidx.compose.foundation.layout.Column(Modifier.width(144.dp)) {
        SessionActionMenuItem(
            text = stringResource(R.string.drawer_action_rename),
            testTag = "session_action_rename",
            onClick = {
                onDismissRequest()
                onAction(SessionAction.RENAME)
            },
        )
        if (showPin) {
            SessionActionMenuItem(
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
        SessionActionMenuItem(
            text = stringResource(
                if (session.archived) R.string.drawer_action_unarchive else R.string.drawer_action_archive,
            ),
            testTag = "session_action_archive",
            onClick = {
                onDismissRequest()
                onAction(SessionAction.TOGGLE_ARCHIVE)
            },
        )
        SessionActionMenuItem(
            text = stringResource(R.string.drawer_action_delete),
            testTag = "session_action_delete",
            onClick = {
                onDismissRequest()
                onAction(SessionAction.DELETE)
                },
            )
            }
        }
    }
}

@Composable
private fun SessionActionMenuItem(
    text: String,
    testTag: String,
    onClick: () -> Unit,
) {
    val colors = deepDeepColors()
    DdPressable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(44.dp).testTag(testTag),
        shape = RoundedCornerShape(10.dp),
        contentDescription = text,
    ) {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            style = DdType.bodySmall,
            color = colors.ink,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 1,
        )
    }
}
