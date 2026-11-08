package io.github.whywood.deepdeep.ui.miniapp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdDialog
import io.github.whywood.deepdeep.design.DdSpacing
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.MiniappInvalidReason

fun invalidReasonLabelRes(reason: MiniappInvalidReason): Int = when (reason) {
    MiniappInvalidReason.MANIFEST_MISSING -> R.string.miniapp_reason_manifest_missing
    MiniappInvalidReason.MANIFEST_UNREADABLE -> R.string.miniapp_reason_manifest_unreadable
    MiniappInvalidReason.MANIFEST_INVALID -> R.string.miniapp_reason_manifest_invalid
    MiniappInvalidReason.ENTRY_MISSING -> R.string.miniapp_reason_entry_missing
    MiniappInvalidReason.ID_MISMATCH -> R.string.miniapp_reason_id_mismatch
}

@Composable
fun MiniappInvalidDialog(
    id: String,
    reasonText: String,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = deepDeepColors()
    DdDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("miniapp_invalid_dialog"),
        title = stringResource(R.string.miniapp_invalid_delete_title),
        content = {
            Column {
                Text(
                    text = id,
                    style = DdType.body,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(DdSpacing.Sm))
                Text(
                    text = stringResource(R.string.miniapp_invalid_title),
                    style = DdType.micro,
                    color = colors.inkTertiary,
                )
                Spacer(Modifier.height(DdSpacing.Xxs))
                Text(
                    text = reasonText,
                    style = DdType.bodySmall,
                    color = colors.inkSecondary,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.miniapp_invalid_delete_message, id),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                )
            }
        },
        actions = {
            DdTextAction(
                text = stringResource(R.string.miniapp_invalid_close),
                onClick = onDismiss,
                color = colors.inkSecondary,
                modifier = Modifier.testTag("miniapp_invalid_close"),
            )
            DdTextAction(
                text = stringResource(R.string.miniapp_invalid_delete),
                onClick = onDelete,
                color = colors.danger,
                modifier = Modifier.testTag("miniapp_invalid_delete"),
            )
        },
    )
}
