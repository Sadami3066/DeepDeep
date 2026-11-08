package io.github.whywood.deepdeep.ui.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdDialog
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdTextField
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors

@Composable
internal fun RemoteRenameDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    val focusRequester = remember { FocusRequester() }
    val trimmed = name.trim()
    DdDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.remote_rename_title),
        content = {
            DdTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = stringResource(R.string.remote_rename_placeholder),
                focusRequester = focusRequester,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                onDone = { if (trimmed.isNotEmpty()) onConfirm(trimmed) },
            )
        },
        actions = {
            DdTextAction(
                text = stringResource(R.string.remote_cancel),
                onClick = onDismiss,
                color = deepDeepColors().inkSecondary,
            )
            DdTextAction(
                text = stringResource(R.string.remote_save),
                onClick = { onConfirm(trimmed) },
                enabled = trimmed.isNotEmpty(),
            )
        },
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
internal fun RemoteDeleteDialog(
    computerName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = deepDeepColors()
    DdDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.remote_delete_title),
        content = {
            Text(
                text = stringResource(R.string.remote_delete_message, computerName),
                style = DdType.bodySmall,
                color = colors.inkSecondary,
            )
        },
        actions = {
            DdTextAction(
                text = stringResource(R.string.remote_cancel),
                onClick = onDismiss,
                color = colors.inkSecondary,
            )
            DdTextAction(
                text = stringResource(R.string.remote_delete_confirm),
                onClick = onConfirm,
                color = colors.danger,
            )
        },
    )
}

@Composable
internal fun RemotePairingDialog() {
    val colors = deepDeepColors()
    DdDialog(
        onDismissRequest = {},
        title = stringResource(R.string.remote_pairing),
        content = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DdSpinner(size = 18.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.remote_connect_loading),
                    style = DdType.bodySmall,
                    color = colors.inkSecondary,
                )
            }
        },
        actions = {},
    )
}

@Composable
internal fun RemotePairFailureDialog(
    message: String,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = deepDeepColors()
    DdDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.remote_pair_failed),
        content = {
            Text(
                text = message,
                style = DdType.bodySmall,
                color = colors.inkSecondary,
            )
        },
        actions = {
            DdTextAction(
                text = stringResource(R.string.remote_cancel),
                onClick = onDismiss,
                color = colors.inkSecondary,
            )
            if (canRetry) {
                DdTextAction(
                    text = stringResource(R.string.remote_connect_retry),
                    onClick = onRetry,
                )
            }
        },
    )
}

@Composable
internal fun RemoteGuideDialog(onDismiss: () -> Unit) {
    val colors = deepDeepColors()
    val steps = listOf(
        R.string.remote_guide_step1,
        R.string.remote_guide_step2,
        R.string.remote_guide_step3,
        R.string.remote_guide_step4,
        R.string.remote_guide_step5,
        R.string.remote_guide_step6,
    )
    DdDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.remote_guide_title),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                steps.forEachIndexed { index, step ->
                    Row {
                        Text(
                            text = "${index + 1}",
                            modifier = Modifier.width(20.dp),
                            style = DdType.bodySmall,
                            color = colors.accent,
                        )
                        Text(
                            text = stringResource(step),
                            modifier = Modifier.weight(1f),
                            style = DdType.bodySmall,
                            color = colors.inkSecondary,
                        )
                    }
                }
            }
        },
        actions = {
            DdTextAction(
                text = stringResource(R.string.remote_guide_close),
                onClick = onDismiss,
            )
        },
    )
}

@Composable
internal fun RemotePasteDialog(
    invalid: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val colors = deepDeepColors()
    val focusRequester = remember { FocusRequester() }
    var draft by remember { mutableStateOf("") }
    DdDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.remote_paste_title),
        content = {
            DdTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = stringResource(R.string.remote_paste_placeholder),
                focusRequester = focusRequester,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                onDone = { onConfirm(draft) },
            )
            if (invalid) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.remote_paste_invalid),
                    style = DdType.bodySmall,
                    color = colors.danger,
                )
            }
        },
        actions = {
            DdTextAction(
                text = stringResource(R.string.remote_cancel),
                onClick = onDismiss,
                color = colors.inkSecondary,
            )
            DdTextAction(
                text = stringResource(R.string.remote_paste_confirm),
                onClick = { onConfirm(draft) },
                enabled = draft.isNotBlank(),
            )
        },
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}
