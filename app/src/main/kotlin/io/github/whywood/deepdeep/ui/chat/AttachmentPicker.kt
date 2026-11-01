package io.github.whywood.deepdeep.ui.chat

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSheetHeader
import io.github.whywood.deepdeep.ui.chat.state.SessionDraftStore
import java.io.File
import java.util.UUID

internal enum class AttachmentSource { IMAGE, FILE, CAMERA }

private const val CAMERA_CACHE_TTL_MS = 60 * 60 * 1000L

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
    val takePhoto: () -> Unit,
)

@Composable
internal fun rememberAttachmentPickers(onPicked: (Uri) -> Unit): AttachmentPickers {
    val context = LocalContext.current
    val cameraUnavailable = stringResource(R.string.attach_camera_unavailable)
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(SessionDraftStore.MAX_ATTACHMENTS),
    ) { uris -> uris.forEach(onPicked) }
    val pickFiles = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> uris.forEach(onPicked) }
    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { saved ->
        val file = pendingPath?.let(::File)
        pendingPath = null
        if (saved && file != null) {
            onPicked(FileProvider.getUriForFile(context, "${context.packageName}.files", file))
        } else {
            runCatching { file?.delete() }
        }
    }
    val takePhoto: () -> Unit = {
        val directory = File(context.cacheDir, "camera")
        val file = File(directory, "${UUID.randomUUID()}.jpg")
        runCatching {
            val staleBefore = System.currentTimeMillis() - CAMERA_CACHE_TTL_MS
            directory.listFiles()?.filter { it.lastModified() < staleBefore }?.forEach { stale -> stale.delete() }
            directory.mkdirs()
            file.createNewFile()
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            pendingPath = file.absolutePath
            takePicture.launch(uri)
        }.onFailure {
            pendingPath = null
            runCatching { file.delete() }
            Toast.makeText(context, cameraUnavailable, Toast.LENGTH_SHORT).show()
        }
    }
    return AttachmentPickers(
        pickImages = {
            pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        pickFiles = { pickFiles.launch(arrayOf("*/*")) },
        takePhoto = takePhoto,
    )
}
