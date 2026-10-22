package io.github.whywood.deepdeep.ui.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors

@Composable
fun RenameSessionDialog(
    initialTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val colors = deepDeepColors()
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    val focusRequester = remember { FocusRequester() }
    val trimmed = title.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.raised,
        titleContentColor = colors.ink,
        textContentColor = colors.inkSecondary,
        title = {
            Text(
                text = stringResource(R.string.drawer_rename_title),
                style = DdType.heading,
            )
        },
        text = {
            BasicTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.paper)
                    .border(1.dp, colors.hairline, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                textStyle = DdType.body.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.accent),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { if (trimmed.isNotEmpty()) onConfirm(trimmed) },
                ),
                decorationBox = { inner ->
                    if (title.isEmpty()) {
                        Text(
                            text = stringResource(R.string.drawer_rename_placeholder),
                            style = DdType.body,
                            color = colors.inkTertiary,
                        )
                    }
                    inner()
                },
            )
        },
        confirmButton = {
            DdTextAction(
                text = stringResource(R.string.drawer_rename_save),
                onClick = { onConfirm(trimmed) },
                enabled = trimmed.isNotEmpty(),
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
fun DeleteSessionDialog(
    sessionTitle: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = deepDeepColors()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.raised,
        titleContentColor = colors.ink,
        textContentColor = colors.inkSecondary,
        title = {
            Text(
                text = stringResource(R.string.drawer_delete_title),
                style = DdType.heading,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.drawer_delete_message, sessionTitle),
                style = DdType.bodySmall,
            )
        },
        confirmButton = {
            DdTextAction(
                text = stringResource(R.string.drawer_delete_confirm),
                onClick = onConfirm,
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
