package io.github.whywood.deepdeep.ui.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import java.util.Locale
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
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, attachment.id) {
        val file = attachmentFile(context, attachment, root)
        value = withContext(Dispatchers.IO) { decodeThumbnailBitmap(file) }
    }
    DdPressable(
        onClick = { onOpen(attachment) },
        modifier = Modifier
            .size(140.dp)
            .testTag("attachment_${attachment.id}"),
        shape = RoundedCornerShape(12.dp),
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
    val shape = RoundedCornerShape(12.dp)
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
                .border(1.dp, colors.hairline.copy(alpha = 0.7f), shape)
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
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = extension.ifEmpty { "FILE" },
                        style = DdType.micro.copy(fontSize = 9.sp),
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
                        style = DdType.bodySmall.copy(fontSize = 10.sp, lineHeight = 13.sp),
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
    val context = LocalContext.current
    val cardWidth = 150.dp
    Box(
        modifier = Modifier
            .width(cardWidth)
            .height(104.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .clickable(onClick = onOpen)
            .testTag("attachment_${attachment.id}"),
    ) {
        when (attachment.kind) {
            AttachmentKind.IMAGE -> {
                val bitmap by produceState<Bitmap?>(initialValue = null, attachment.id) {
                    val file = attachmentFile(context, attachment, root)
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
                    val file = attachmentFile(context, attachment, root)
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
                    modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
                    style = DdType.bodySmall.copy(fontSize = 9.sp, lineHeight = 12.sp),
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
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = formatAttachmentSize(attachment.size),
                        style = DdType.bodySmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
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
                .background(colors.ink.copy(alpha = 0.55f))
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

private fun attachmentFile(context: Context, attachment: AttachmentRef, root: File): File =
    File(root, attachment.path)

private fun isMarkdownAttachment(attachment: AttachmentRef): Boolean {
    val name = attachment.name.lowercase()
    return attachment.mime == "text/markdown" || name.endsWith(".md") || name.endsWith(".markdown")
}

private fun decodePreviewBitmap(file: File): Bitmap? = decodeBitmap(file, PreviewBitmapLimit)

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
