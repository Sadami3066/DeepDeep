package io.github.whywood.deepdeep.ui.common.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSheetHeader
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.ui.common.chat.state.MAX_ATTACHMENTS



internal enum class AttachmentSource { IMAGE, FILE, CAMERA }

@Composable
internal fun AttachmentSourceSheet(
    onDismiss: () -> Unit,
    onPick: (AttachmentSource) -> Unit,
) {
    val colors = deepDeepColors()
    DdSheet(onDismiss = onDismiss, containerColor = colors.paper) {
        DdSheetHeader(
            title = stringResource(R.string.assistant_attach_add),
            onClose = onDismiss,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AttachmentSourceItem(
                icon = DdIcons.Camera,
                label = stringResource(R.string.attach_take_photo),
                onClick = {
                    onDismiss()
                    onPick(AttachmentSource.CAMERA)
                },
                modifier = Modifier.weight(1f),
            )
            AttachmentSourceItem(
                icon = DdIcons.Image,
                label = stringResource(R.string.attach_pick_image),
                onClick = {
                    onDismiss()
                    onPick(AttachmentSource.IMAGE)
                },
                modifier = Modifier.weight(1f),
            )
            AttachmentSourceItem(
                icon = DdIcons.File,
                label = stringResource(R.string.attach_pick_file),
                onClick = {
                    onDismiss()
                    onPick(AttachmentSource.FILE)
                },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AttachmentSourceItem(
    icon: DdIcon,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    DdPressable(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        background = colors.raised,
        contentDescription = label,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(colors.paper),
                contentAlignment = Alignment.Center,
            ) {
                DdIcon(icon = icon, tint = colors.inkSecondary, size = 20.dp)
            }
            Spacer(Modifier.height(9.dp))
            Text(
                text = label,
                style = DdType.label.copy(fontSize = 13.sp, lineHeight = 17.sp),
                color = colors.ink,
            )
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
        ActivityResultContracts.PickMultipleVisualMedia(MAX_ATTACHMENTS),
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
