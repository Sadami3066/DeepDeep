package io.github.whywood.deepdeep.ui.common.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.AttachmentKind
import io.github.whywood.deepdeep.data.AttachmentRef
import io.github.whywood.deepdeep.data.attachmentFile
import io.github.whywood.deepdeep.data.formatAttachmentSize
import io.github.whywood.deepdeep.design.DdAlpha
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdShapes
import io.github.whywood.deepdeep.design.DdSpacing
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext



private const val ThumbnailBitmapLimit = 720

@Composable
internal fun AttachmentCards(
    attachments: List<AttachmentRef>,
    root: File,
    onOpen: (AttachmentRef) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        attachments.forEach { attachment ->
            if (attachment.kind == AttachmentKind.IMAGE) {
                ImageAttachmentCard(attachment = attachment, root = root, onOpen = onOpen)
            } else {
                FileAttachmentCard(attachment = attachment, root = root, onOpen = onOpen)
            }
        }
    }
}

@Composable
private fun ImageAttachmentCard(
    attachment: AttachmentRef,
    root: File,
    onOpen: (AttachmentRef) -> Unit,
) {
    val colors = deepDeepColors()
    val bitmap by produceState<Bitmap?>(initialValue = null, attachment.id) {
        val file = attachmentFile(attachment, root)
        value = withContext(Dispatchers.IO) { decodeThumbnailBitmap(file) }
    }
    DdPressable(
        onClick = { onOpen(attachment) },
        modifier = Modifier
            .size(140.dp)
            .testTag("attachment_${attachment.id}"),
        shape = DdShapes.Medium,
        background = colors.wash,
        contentDescription = stringResource(R.string.assistant_attach_open),
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
}

@Composable
private fun FileAttachmentCard(
    attachment: AttachmentRef,
    root: File,
    onOpen: (AttachmentRef) -> Unit,
) {
    val colors = deepDeepColors()
    val shape = DdShapes.Medium
    val extension = attachment.name.substringAfterLast('.', "").uppercase(Locale.US).take(4)
    DdPressable(
        onClick = { onOpen(attachment) },
        modifier = Modifier
            .size(140.dp)
            .testTag("attachment_${attachment.id}"),
        shape = shape,
        background = colors.surface,
        contentDescription = stringResource(R.string.assistant_attach_open),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .border(1.dp, colors.hairline.copy(alpha = DdAlpha.Secondary), shape)
                .padding(10.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, colors.hairline, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = DdSpacing.Xxs),
                ) {
                    Text(
                        text = extension.ifEmpty { "FILE" },
                        style = DdType.micro.copy(fontSize = 11.sp),
                        color = colors.inkSecondary,
                    )
                }
                Column {
                    Text(
                        text = attachment.name,
                        style = DdType.bodySmall,
                        color = colors.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = formatAttachmentSize(attachment.size),
                        style = DdType.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                        color = colors.inkTertiary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
internal fun AttachmentChips(
    attachments: List<AttachmentRef>,
    root: File,
    onRemove: (String) -> Unit,
    onOpen: (AttachmentRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(DdSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        attachments.forEach { attachment ->
            AttachmentPreviewCard(
                attachment = attachment,
                root = root,
                onRemove = { onRemove(attachment.id) },
                onOpen = { onOpen(attachment) },
            )
        }
    }
}

@Composable
private fun AttachmentPreviewCard(
    attachment: AttachmentRef,
    root: File,
    onRemove: () -> Unit,
    onOpen: () -> Unit,
) {
    val colors = deepDeepColors()
    val cardWidth = 150.dp
    Box(
        modifier = Modifier
            .width(cardWidth)
            .height(104.dp)
            .clip(DdShapes.Medium)
            .background(colors.surface)
            .clickable(onClick = onOpen)
            .testTag("attachment_${attachment.id}"),
    ) {
        when (attachment.kind) {
            AttachmentKind.IMAGE -> {
                val bitmap by produceState<Bitmap?>(initialValue = null, attachment.id) {
                    val file = attachmentFile(attachment, root)
                    value = withContext(Dispatchers.IO) { decodeThumbnailBitmap(file) }
                }
                val image = bitmap
                if (image == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        DdIcon(icon = DdIcons.Image, tint = colors.inkTertiary, size = 20.dp)
                    }
                } else {
                    Image(
                        bitmap = image.asImageBitmap(),
                        contentDescription = attachment.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }

            AttachmentKind.TEXT -> {
                val snippet by produceState(initialValue = "", attachment.id) {
                    val file = attachmentFile(attachment, root)
                    value = withContext(Dispatchers.IO) {
                        runCatching {
                            file.bufferedReader().use { reader ->
                                val buffer = CharArray(900)
                                val count = reader.read(buffer)
                                if (count <= 0) "" else String(buffer, 0, count)
                            }
                        }.getOrDefault("")
                    }
                }
                Text(
                    text = snippet.ifBlank { attachment.name },
                    modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = DdSpacing.Sm),
                    style = DdType.bodySmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
                    color = colors.inkTertiary,
                    maxLines = 7,
                    overflow = TextOverflow.Clip,
                )
            }

            AttachmentKind.OTHER -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    DdIcon(icon = DdIcons.File, tint = colors.inkSecondary, size = 20.dp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = attachment.name,
                        style = DdType.bodySmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
                        color = colors.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(DdSpacing.Xxs))
                    Text(
                        text = formatAttachmentSize(attachment.size),
                        style = DdType.bodySmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
                        color = colors.inkTertiary,
                        maxLines = 1,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(5.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(colors.ink.copy(alpha = DdAlpha.Muted))
                .clickable(onClick = onRemove)
                .testTag("attachment_remove_${attachment.id}"),
            contentAlignment = Alignment.Center,
        ) {
            DdIcon(icon = DdIcons.Close, tint = colors.surface, size = 13.dp)
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
    FileViewerDialog(
        file = file,
        name = attachment.name,
        mime = attachment.mime,
        onDismiss = onDismiss,
        onShare = onShare,
    )
}

private fun decodeThumbnailBitmap(file: File): Bitmap? {
    AttachmentThumbnails.get(file)?.let { return it }
    val bitmap = decodeBitmap(file, ThumbnailBitmapLimit) ?: return null
    AttachmentThumbnails.put(file, bitmap)
    return bitmap
}

private const val ThumbnailCacheBytes = 8 * 1024 * 1024

private object AttachmentThumbnails {
    private val memory = object : LruCache<String, Bitmap>(ThumbnailCacheBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(file: File): Bitmap? = memory.get(key(file))

    fun put(file: File, bitmap: Bitmap) {
        memory.put(key(file), bitmap)
    }

    private fun key(file: File): String =
        "${file.absolutePath}:${file.length()}:${file.lastModified()}"
}

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
