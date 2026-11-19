package io.github.whywood.superpi.ui.sessions

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import io.github.whywood.superpi.R
import io.github.whywood.superpi.ui.common.rememberHapticFeedback
import io.github.whywood.superpi.design.DdDialog
import io.github.whywood.superpi.design.DdTextAction
import io.github.whywood.superpi.design.DdTextField
import io.github.whywood.superpi.design.DdType
import io.github.whywood.superpi.design.superPiColors

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
                color = superPiColors().inkSecondary,
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
    val colors = superPiColors()
    val haptic = rememberHapticFeedback()
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
                onClick = {
                    haptic(HapticFeedbackType.Reject)
                    onConfirm()
                },
                color = colors.danger,
            )
        },
    )
}
