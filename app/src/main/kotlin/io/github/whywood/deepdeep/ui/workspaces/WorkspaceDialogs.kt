package io.github.whywood.deepdeep.ui.workspaces

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors

private const val MAX_WORKSPACE_NAME_LENGTH = 40

enum class WorkspaceNameMode { CREATE, RENAME }

@Composable
fun WorkspaceNameDialog(
    mode: WorkspaceNameMode,
    initial: String = "",
    existingNames: List<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = deepDeepColors()
    var name by remember(initial) { mutableStateOf(initial) }
    var submitted by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val trimmed = name.trim()
    val preset = if (mode == WorkspaceNameMode.RENAME) initial.trim() else null
    val error = when {
        trimmed.isEmpty() -> R.string.workspace_name_error_empty
        trimmed.length > MAX_WORKSPACE_NAME_LENGTH -> R.string.workspace_name_error_too_long
        existingNames.any { existing ->
            existing.equals(trimmed, ignoreCase = true) &&
                (preset == null || !existing.equals(preset, ignoreCase = true))
        } -> R.string.workspace_name_error_duplicate

        else -> null
    }
    val submit: () -> Unit = {
        submitted = true
        if (error == null) onConfirm(trimmed)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("workspace_name_dialog"),
        containerColor = colors.raised,
        titleContentColor = colors.ink,
        textContentColor = colors.inkSecondary,
        title = {
            Text(
                text = stringResource(
                    if (mode == WorkspaceNameMode.CREATE) R.string.workspace_create else R.string.workspace_rename_title,
                ),
                style = DdType.heading,
            )
        },
        text = {
            Column {
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.paper)
                        .border(1.dp, colors.hairline, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("workspace_name_input"),
                    textStyle = DdType.body.copy(color = colors.ink),
                    cursorBrush = SolidColor(colors.accent),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    decorationBox = { inner ->
                        if (name.isEmpty()) {
                            Text(
                                text = stringResource(R.string.workspace_name_placeholder),
                                style = DdType.body,
                                color = colors.inkTertiary,
                            )
                        }
                        inner()
                    },
                )
                if (submitted && error != null) {
                    Text(
                        text = stringResource(error),
                        modifier = Modifier
                            .padding(start = 2.dp, top = 10.dp)
                            .testTag("workspace_name_error"),
                        style = DdType.bodySmall,
                        color = colors.danger,
                    )
                }
            }
        },
        confirmButton = {
            DdTextAction(
                text = stringResource(
                    if (mode == WorkspaceNameMode.CREATE) R.string.workspace_create_confirm else R.string.workspace_name_confirm,
                ),
                onClick = { submit() },
                modifier = Modifier.testTag("workspace_name_confirm"),
            )
        },
        dismissButton = {
            DdTextAction(
                text = stringResource(R.string.drawer_cancel),
                onClick = onDismiss,
                color = colors.inkSecondary,
            )
        },
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
fun DeleteWorkspaceDialog(
    name: String,
    sessionCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = deepDeepColors()
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("workspace_delete_dialog"),
        containerColor = colors.raised,
        titleContentColor = colors.ink,
        textContentColor = colors.inkSecondary,
        title = {
            Text(
                text = stringResource(R.string.workspace_delete_title),
                style = DdType.heading,
            )
        },
        text = {
            Text(
                text = if (sessionCount > 0) {
                    stringResource(R.string.workspace_delete_message, name, sessionCount)
                } else {
                    stringResource(R.string.workspace_delete_message_empty, name)
                },
                style = DdType.bodySmall,
            )
        },
        confirmButton = {
            DdTextAction(
                text = stringResource(R.string.workspace_delete_confirm),
                onClick = onConfirm,
                modifier = Modifier.testTag("workspace_delete_confirm"),
                color = colors.danger,
            )
        },
        dismissButton = {
            DdTextAction(
                text = stringResource(R.string.drawer_cancel),
                onClick = onDismiss,
                color = colors.inkSecondary,
            )
        },
    )
}
