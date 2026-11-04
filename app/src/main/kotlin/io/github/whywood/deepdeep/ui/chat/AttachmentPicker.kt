package io.github.whywood.deepdeep.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSheetHeader
import io.github.whywood.deepdeep.ui.chat.state.SessionDraftStore

internal enum class AttachmentSource { IMAGE, FILE, CAMERA }

@Composable
internal fun AttachmentSourceSheet(
    onDismiss: () -> Unit,
    onPick: (AttachmentSource) -> Unit,
) {
    DdSheet(onDismiss = onDismiss) {
        DdSheetHeader(
            title = stringResource(R.string.assistant_attach_add),
            onClose = onDismiss,
        )
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(8.dp))
            DdListRow(
                title = stringResource(R.string.attach_pick_image),
                icon = DdIcons.Image,
                onClick = {
                    onDismiss()
                    onPick(AttachmentSource.IMAGE)
                },
            )
            DdListRow(
                title = stringResource(R.string.attach_pick_file),
                icon = DdIcons.File,
                onClick = {
                    onDismiss()
                    onPick(AttachmentSource.FILE)
                },
            )
            DdListRow(
                title = stringResource(R.string.attach_take_photo),
                icon = DdIcons.Camera,
                onClick = {
                    onDismiss()
                    onPick(AttachmentSource.CAMERA)
                },
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

internal class AttachmentPickers(
    val pickImages: () -> Unit,
    val pickFiles: () -> Unit,
)

@Composable
internal fun rememberAttachmentPickers(onPicked: (Uri) -> Unit): AttachmentPickers {
    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(SessionDraftStore.MAX_ATTACHMENTS),
    ) { uris -> uris.forEach(onPicked) }
    val pickFiles = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> uris.forEach(onPicked) }
    return AttachmentPickers(
        pickImages = {
            pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        pickFiles = { pickFiles.launch(arrayOf("*/*")) },
    )
}
