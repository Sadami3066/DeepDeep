package io.github.whywood.deepdeep.ui.sessions

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdDialog
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdTextField
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors

@Composable
fun RenameSessionDialog(
    initialTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    val focusRequester = remember { FocusRequester() }
    val trimmed = title.trim()
    DdDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.drawer_rename_title),
        content = {
            DdTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = stringResource(R.string.drawer_rename_placeholder),
                focusRequester = focusRequester,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                onDone = { if (trimmed.isNotEmpty()) onConfirm(trimmed) },
            )
        },
        actions = {
            DdTextAction(
                text = stringResource(R.string.drawer_cancel),
                onClick = onDismiss,
                color = deepDeepColors().inkSecondary,
            )
            DdTextAction(
                text = stringResource(R.string.drawer_rename_save),
                onClick = { onConfirm(trimmed) },
                enabled = trimmed.isNotEmpty(),
            )
        },
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
fun DeleteSessionDialog(
    sessionTitle: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = deepDeepColors()
    DdDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.drawer_delete_title),
        content = {
            Text(
                text = stringResource(R.string.drawer_delete_message, sessionTitle),
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
                text = stringResource(R.string.drawer_delete_confirm),
                onClick = onConfirm,
                color = colors.danger,
            )
        },
    )
}
