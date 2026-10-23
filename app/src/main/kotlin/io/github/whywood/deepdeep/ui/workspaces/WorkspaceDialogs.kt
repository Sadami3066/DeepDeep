package io.github.whywood.deepdeep.ui.workspaces

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdDialog
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdTextField
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
    DdDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("workspace_name_dialog"),
        title = stringResource(
            if (mode == WorkspaceNameMode.CREATE) R.string.workspace_create else R.string.workspace_rename_title,
        ),
        content = {
            Column {
                DdTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = stringResource(R.string.workspace_name_placeholder),
                    focusRequester = focusRequester,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    onDone = { submit() },
                    testTag = "workspace_name_input",
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
        actions = {
            DdTextAction(
                text = stringResource(R.string.drawer_cancel),
                onClick = onDismiss,
                color = colors.inkSecondary,
            )
            DdTextAction(
                text = stringResource(
                    if (mode == WorkspaceNameMode.CREATE) {
                        R.string.workspace_create_confirm
                    } else {
                        R.string.workspace_name_confirm
                    },
                ),
                onClick = { submit() },
                modifier = Modifier.testTag("workspace_name_confirm"),
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
    DdDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("workspace_delete_dialog"),
        title = stringResource(R.string.workspace_delete_title),
        content = {
            Text(
                text = if (sessionCount > 0) {
                    stringResource(R.string.workspace_delete_message, name, sessionCount)
                } else {
                    stringResource(R.string.workspace_delete_message_empty, name)
                },
                style = DdType.bodySmall,
                color = colors.inkSecondary,
            )
        },
        actions = {
            DdTextAction(
                text = stringResource(R.string.drawer_cancel),
                onClick = onDismiss,
                color = colors.inkSecondary,
            )
            DdTextAction(
                text = stringResource(R.string.workspace_delete_confirm),
                onClick = onConfirm,
                modifier = Modifier.testTag("workspace_delete_confirm"),
                color = colors.danger,
            )
        },
    )
}
