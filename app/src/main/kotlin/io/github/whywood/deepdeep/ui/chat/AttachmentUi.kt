package io.github.whywood.deepdeep.ui.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.AttachmentKind
import io.github.whywood.deepdeep.data.AttachmentRef
import io.github.whywood.deepdeep.data.formatAttachmentSize
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PreviewTextByteLimit = 256 * 1024
private const val PreviewBitmapLimit = 2048
private const val ThumbnailBitmapLimit = 720

private sealed interface FilePreview {
    data object Loading : FilePreview
    data class ImageData(val bitmap: Bitmap) : FilePreview
    data class TextData(val text: String) : FilePreview
    data object Failed : FilePreview
}

@Composable
internal fun AttachmentCards(
    attachments: List<AttachmentRef>,
    onOpen: (AttachmentRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        attachments.forEach { attachment ->
            if (attachment.kind == AttachmentKind.IMAGE) {
                ImageAttachmentCard(attachment = attachment, onOpen = onOpen)
            } else {
                FileAttachmentCard(attachment = attachment, onOpen = onOpen)
            }
        }
    }
}

@Composable
private fun ImageAttachmentCard(
    attachment: AttachmentRef,
    onOpen: (AttachmentRef) -> Unit,
) {
    val colors = deepDeepColors()
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, attachment.id) {
        val file = attachmentFile(context, attachment)
        value = withContext(Dispatchers.IO) { decodeThumbnailBitmap(file) }
    }
    DdPressable(
        onClick = { onOpen(attachment) },
        modifier = Modifier
            .widthIn(max = 220.dp)
            .fillMaxWidth()
            .testTag("attachment_${attachment.id}"),
        shape = RoundedCornerShape(12.dp),
        contentDescription = stringResource(R.string.assistant_attach_open),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.wash),
                contentAlignment = Alignment.Center,
            ) {
                val image = bitmap
                if (image == null) {
                    DdIcon(icon = DdIcons.Image, tint = colors.inkTertiary, size = 22.dp)
                } else {
                    Image(
                        bitmap = image.asImageBitmap(),
                        contentDescription = attachment.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 2.dp, end = 2.dp, top = 6.dp, bottom = 2.dp),
            ) {
                Text(
                    text = attachment.name,
                    style = DdType.bodySmall,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatAttachmentSize(attachment.size),
                    style = DdType.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                    color = colors.inkTertiary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun FileAttachmentCard(
    attachment: AttachmentRef,
    onOpen: (AttachmentRef) -> Unit,
) {
    val colors = deepDeepColors()
    DdPressable(
        onClick = { onOpen(attachment) },
        modifier = Modifier
            .widthIn(min = 160.dp, max = 280.dp)
            .heightIn(min = 52.dp)
            .testTag("attachment_${attachment.id}"),
        shape = RoundedCornerShape(12.dp),
        background = colors.wash,
        contentDescription = stringResource(R.string.assistant_attach_open),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surface),
                contentAlignment = Alignment.Center,
            ) {
                DdIcon(
                    icon = attachmentIcon(attachment.kind),
                    tint = colors.inkSecondary,
                    size = 16.dp,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.widthIn(max = 220.dp)) {
                Text(
                    text = attachment.name,
                    style = DdType.bodySmall,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatAttachmentSize(attachment.size),
                    style = DdType.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                    color = colors.inkTertiary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun AttachmentChips(
    attachments: List<AttachmentRef>,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        attachments.forEach { attachment ->
            Row(
                modifier = Modifier
                    .height(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.wash)
                    .padding(start = 8.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DdIcon(
                    icon = attachmentIcon(attachment.kind),
                    tint = colors.inkSecondary,
                    size = 14.dp,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = attachment.name,
                    modifier = Modifier.widthIn(max = 150.dp),
                    style = DdType.bodySmall,
                    color = colors.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                DdIconButton(
                    icon = DdIcons.Close,
                    onClick = { onRemove(attachment.id) },
                    contentDescription = stringResource(R.string.assistant_attach_remove),
                    modifier = Modifier.testTag("attachment_remove_${attachment.id}"),
                    size = 18.dp,
                    iconSize = 11.dp,
                    tint = colors.inkTertiary,
                )
            }
        }
    }
}

@Composable
internal fun AttachmentPreviewDialog(
    attachment: AttachmentRef,
    file: File,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
) {
    val colors = deepDeepColors()
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(attachment.id) { shown = true }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BackHandler(onBack = onDismiss)
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.paper)
                .testTag("attachment_preview"),
        ) {
            AnimatedVisibility(
                visible = shown,
                enter = fadeIn(tween(DdMotion.FAST)),
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DdIconButton(
                            icon = DdIcons.Close,
                            onClick = onDismiss,
                            contentDescription = stringResource(R.string.assistant_close),
                            size = 44.dp,
                            iconSize = 18.dp,
                            tint = colors.inkSecondary,
                        )
                        Text(
                            text = attachment.name,
                            modifier = Modifier.weight(1f),
                            style = DdType.label,
                            color = colors.ink,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        DdIconButton(
                            icon = DdIcons.Share,
                            onClick = onShare,
                            contentDescription = stringResource(R.string.assistant_attach_share),
                            modifier = Modifier.testTag("attachment_share"),
                            size = 44.dp,
                            iconSize = 17.dp,
                            tint = colors.inkSecondary,
                        )
                    }
                    DdHairline()
                    AttachmentPreviewBody(attachment = attachment, file = file)
                }
            }
        }
    }
}

@Composable
private fun AttachmentPreviewBody(attachment: AttachmentRef, file: File) {
    val colors = deepDeepColors()
    val preview by produceState<FilePreview>(initialValue = FilePreview.Loading, attachment.id) {
        value = withContext(Dispatchers.IO) {
            when (attachment.kind) {
                AttachmentKind.IMAGE -> decodePreviewBitmap(file)
                    ?.let { FilePreview.ImageData(it) }
                    ?: FilePreview.Failed

                AttachmentKind.TEXT -> readPreviewText(file)
                    ?.let { FilePreview.TextData(it) }
                    ?: FilePreview.Failed

                AttachmentKind.OTHER -> FilePreview.Failed
            }
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val current = preview) {
            FilePreview.Loading -> Box(Modifier.fillMaxSize().background(colors.wash))
            is FilePreview.ImageData -> Image(
                bitmap = current.bitmap.asImageBitmap(),
                contentDescription = attachment.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )

            is FilePreview.TextData -> {
                if (isMarkdownAttachment(attachment)) {
                    MarkdownPreviewBody(text = current.text)
                } else {
                    TextPreviewBody(text = current.text)
                }
            }

            FilePreview.Failed -> UnpreviewableContent(attachment)
        }
    }
}

@Composable
private fun MarkdownPreviewBody(text: String) {
    val colors = deepDeepColors()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        if (text.isBlank()) {
            Text(
                text = stringResource(R.string.assistant_attach_empty),
                style = DdType.bodySmall,
                color = colors.inkTertiary,
            )
        } else {
            ChatMarkdown(text)
        }
    }
}

@Composable
private fun TextPreviewBody(text: String) {
    val colors = deepDeepColors()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Text(
            text = text.ifEmpty { stringResource(R.string.assistant_attach_empty) },
            style = DdType.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            ),
            color = colors.inkSecondary,
        )
    }
}

@Composable
private fun UnpreviewableContent(attachment: AttachmentRef) {
    val colors = deepDeepColors()
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        DdIcon(icon = DdIcons.File, tint = colors.inkTertiary, size = 48.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            text = attachment.name,
            style = DdType.label,
            color = colors.ink,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.assistant_attach_unpreviewable),
            style = DdType.bodySmall,
            color = colors.inkTertiary,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = formatAttachmentSize(attachment.size),
            style = DdType.bodySmall,
            color = colors.inkTertiary,
        )
    }
}

private fun attachmentIcon(kind: AttachmentKind) = when (kind) {
    AttachmentKind.IMAGE -> DdIcons.Image
    else -> DdIcons.File
}

private fun attachmentFile(context: Context, attachment: AttachmentRef): File =
    File(File(context.filesDir, "workspace"), attachment.path)

private fun isMarkdownAttachment(attachment: AttachmentRef): Boolean {
    val name = attachment.name.lowercase()
    return attachment.mime == "text/markdown" || name.endsWith(".md") || name.endsWith(".markdown")
}

private fun decodePreviewBitmap(file: File): Bitmap? = decodeBitmap(file, PreviewBitmapLimit)

private fun decodeThumbnailBitmap(file: File): Bitmap? = decodeBitmap(file, ThumbnailBitmapLimit)

private fun decodeBitmap(file: File, limit: Int): Bitmap? {
    if (!file.isFile) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > limit || bounds.outHeight / sample > limit) {
        sample *= 2
    }
    return runCatching {
        BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}

private fun readPreviewText(file: File): String? {
    if (!file.isFile) return null
    return runCatching {
        val buffer = ByteArray(PreviewTextByteLimit)
        var total = 0
        file.inputStream().use { input ->
            while (total < PreviewTextByteLimit) {
                val read = input.read(buffer, total, PreviewTextByteLimit - total)
                if (read < 0) break
                total += read
            }
        }
        String(buffer, 0, total, Charsets.UTF_8)
    }.getOrNull()
}
