package io.github.whywood.deepdeep.ui.workshop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdDialog
import io.github.whywood.deepdeep.design.DdSectionLabel
import io.github.whywood.deepdeep.design.DdSegmentedControl
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdTextField
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.WorkshopInvalidReason
import io.github.whywood.deepdeep.runtime.WorkshopType

enum class WorkshopNameError { EMPTY, TOO_LONG }

private const val MAX_WORKSHOP_NAME_LENGTH = 40

fun workshopNameError(name: String): WorkshopNameError? {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() -> WorkshopNameError.EMPTY
        trimmed.length > MAX_WORKSHOP_NAME_LENGTH -> WorkshopNameError.TOO_LONG
        else -> null
    }
}

fun invalidReasonLabelRes(reason: WorkshopInvalidReason): Int = when (reason) {
    WorkshopInvalidReason.MANIFEST_MISSING -> R.string.workshop_reason_manifest_missing
    WorkshopInvalidReason.MANIFEST_UNREADABLE -> R.string.workshop_reason_manifest_unreadable
    WorkshopInvalidReason.MANIFEST_INVALID -> R.string.workshop_reason_manifest_invalid
    WorkshopInvalidReason.ENTRY_MISSING -> R.string.workshop_reason_entry_missing
    WorkshopInvalidReason.ID_MISMATCH -> R.string.workshop_reason_id_mismatch
}

internal fun workshopTypeLabelRes(type: WorkshopType): Int = when (type) {
    WorkshopType.BOARD -> R.string.workshop_type_board
    WorkshopType.WORKSTATION -> R.string.workshop_type_workstation
    WorkshopType.WORKFLOW -> R.string.workshop_type_workflow
}

private fun WorkshopNameError.messageRes(): Int = when (this) {
    WorkshopNameError.EMPTY -> R.string.workshop_name_empty
    WorkshopNameError.TOO_LONG -> R.string.workshop_name_too_long
}

@Composable
fun WorkshopCreateDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, type: WorkshopType) -> Unit,
    creating: Boolean,
    error: String?,
) {
    val colors = deepDeepColors()
    val focusRequester = remember { FocusRequester() }
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(WorkshopType.BOARD) }
    var submitted by remember { mutableStateOf(false) }
    val nameError = workshopNameError(name)

    fun submit() {
        submitted = true
        if (creating || nameError != null) return
        onCreate(name.trim(), type)
    }

    DdDialog(
        onDismissRequest = { if (!creating) onDismiss() },
        modifier = Modifier.testTag("workshop_create_dialog"),
        title = stringResource(R.string.workshop_create_title),
        content = {
            Column {
                DdSectionLabel(stringResource(R.string.workshop_name_label))
                Spacer(Modifier.height(8.dp))
                DdTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = stringResource(R.string.workshop_name_hint),
                    focusRequester = focusRequester,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    onDone = { submit() },
                    testTag = "workshop_name",
                )
                if (submitted && nameError != null) {
                    Text(
                        text = stringResource(nameError.messageRes()),
                        modifier = Modifier
                            .padding(start = 2.dp, top = 10.dp)
                            .testTag("workshop_name_error"),
                        style = DdType.bodySmall,
                        color = colors.danger,
                    )
                }
                Spacer(Modifier.height(20.dp))
                DdSectionLabel(stringResource(R.string.workshop_type_label))
                Spacer(Modifier.height(8.dp))
                DdSegmentedControl(
                    options = WorkshopType.entries,
                    selected = type,
                    onSelect = { type = it },
                    modifier = Modifier.fillMaxWidth().testTag("workshop_type"),
                    enabled = !creating,
                    label = { stringResource(workshopTypeLabelRes(it)) },
                )
                if (error != null) {
                    Text(
                        text = error,
                        modifier = Modifier
                            .padding(start = 2.dp, top = 12.dp)
                            .testTag("workshop_create_error"),
                        style = DdType.bodySmall,
                        color = colors.danger,
                    )
                }
            }
        },
        actions = {
            DdTextAction(
                text = stringResource(R.string.workshop_cancel),
                onClick = onDismiss,
                color = colors.inkSecondary,
                enabled = !creating,
            )
            DdTextAction(
                text = stringResource(
                    if (creating) R.string.workshop_creating else R.string.workshop_create,
                ),
                onClick = { submit() },
                modifier = Modifier.testTag("workshop_create_confirm"),
                enabled = !creating,
            )
        },
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
fun WorkshopInvalidDialog(
    id: String,
    reasonText: String,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = deepDeepColors()
    DdDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("workshop_invalid_dialog"),
        title = stringResource(R.string.workshop_invalid_delete_title),
        content = {
            Column {
                Text(
                    text = id,
                    style = DdType.body,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.workshop_invalid_title),
                    style = DdType.micro,
                    color = colors.inkTertiary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = reasonText,
                    style = DdType.bodySmall,
                    color = colors.inkSecondary,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.workshop_invalid_delete_message, id),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                )
            }
        },
        actions = {
            DdTextAction(
                text = stringResource(R.string.workshop_invalid_close),
                onClick = onDismiss,
                color = colors.inkSecondary,
                modifier = Modifier.testTag("workshop_invalid_close"),
            )
            DdTextAction(
                text = stringResource(R.string.workshop_invalid_delete),
                onClick = onDelete,
                color = colors.danger,
                modifier = Modifier.testTag("workshop_invalid_delete"),
            )
        },
    )
}
