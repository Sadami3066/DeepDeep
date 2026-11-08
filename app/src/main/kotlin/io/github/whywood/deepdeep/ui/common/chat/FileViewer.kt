package io.github.whywood.deepdeep.ui.common.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.FilePreviewKind
import io.github.whywood.deepdeep.data.TextFileContent
import io.github.whywood.deepdeep.data.codeLanguageFor
import io.github.whywood.deepdeep.data.filePreviewKind
import io.github.whywood.deepdeep.data.fileViewIntent
import io.github.whywood.deepdeep.data.formatAttachmentSize
import io.github.whywood.deepdeep.data.readTextLimited
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdPrimaryButton
import io.github.whywood.deepdeep.design.DdSpacing
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.ui.common.chat.AnimatedImage
import io.github.whywood.deepdeep.ui.common.chat.ChatMarkdown
import io.github.whywood.deepdeep.ui.common.chat.animatedImageFile
import io.github.whywood.deepdeep.ui.common.chat.copyToClipboard
import io.github.whywood.deepdeep.ui.common.chat.highlightedCode
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext








private const val IMAGE_PREVIEW_LIMIT = 2048

private sealed interface TextReadState {
    data object Loading : TextReadState
    data object Failed : TextReadState
    data class Ready(val content: TextFileContent) : TextReadState
}

private sealed interface ImageReadState {
    data object Loading : ImageReadState
    data object Failed : ImageReadState
    data class Ready(val bitmap: Bitmap) : ImageReadState
}

private val codeSourceStyle = DdType.bodySmall.copy(
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp,
    lineHeight = 20.sp,
)

@Composable
internal fun FileViewerDialog(
    file: File,
    name: String,
    mime: String,
    onDismiss: () -> Unit,
    onShare: (() -> Unit)? = null,
) {
    val colors = deepDeepColors()
    val context = LocalContext.current
    val kind = remember(name, mime) { filePreviewKind(name, mime) }
    var showSource by rememberSaveable(file.absolutePath) { mutableStateOf(false) }
    var saveSheet by rememberSaveable(file.absolutePath) { mutableStateOf(false) }
    val openFailed = stringResource(R.string.present_open_failed)
    val openExternal = {
        val intent = fileViewIntent(context, file, mime)
        val opened = intent != null && runCatching { context.startActivity(intent) }.isSuccess
        if (!opened) Toast.makeText(context, openFailed, Toast.LENGTH_SHORT).show()
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        BackHandler(onBack = onDismiss)
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.paper)
                .statusBarsPadding()
                .navigationBarsPadding()
                .testTag("file_viewer"),
        ) {
            Column(Modifier.fillMaxSize()) {
                ViewerTitleBar(
                    name = name,
                    onDismiss = onDismiss,
                    onShare = onShare,
                    onDownload = { saveMediaToDownloads(context, file, mime) },
                    textKind = kind == FilePreviewKind.MARKDOWN || kind == FilePreviewKind.HTML,
                    showSource = showSource,
                    onShowSource = { showSource = it },
                )
                DdHairline()
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (kind) {
                        FilePreviewKind.IMAGE -> ImageFileBody(
                            file = file,
                            name = name,
                            onOpenExternal = openExternal,
                            onLongPress = { saveSheet = true },
                        )

                        FilePreviewKind.MARKDOWN -> TextFileBody(
                            file = file,
                            kind = kind,
                            showSource = showSource,
                            onOpenExternal = openExternal,
                        )

                        FilePreviewKind.HTML -> TextFileBody(
                            file = file,
                            kind = kind,
                            showSource = showSource,
                            onOpenExternal = openExternal,
                        )

                        FilePreviewKind.CODE -> CodeFileBody(
                            file = file,
                            name = name,
                            onOpenExternal = openExternal,
                        )

                        FilePreviewKind.VIDEO -> VideoFileBody(
                            file = file,
                            onOpenExternal = openExternal,
                        )

                        FilePreviewKind.UNSUPPORTED -> MessageFileBody(
                            file = file,
                            name = name,
                            message = stringResource(R.string.files_unsupported),
                            onOpenExternal = openExternal,
                        )
                    }
                }
            }
            if (saveSheet) {
                SaveToDownloadsSheet(
                    name = name,
                    onDismiss = { saveSheet = false },
                    onSave = {
                        saveSheet = false
                        saveMediaToDownloads(context, file, mime)
                    },
                    onShare = {
                        saveSheet = false
                        if (onShare != null) onShare() else shareMediaFile(context, file, mime)
                    },
                )
            }
        }
    }
}

@Composable
private fun ViewerTitleBar(
    name: String,
    onDismiss: () -> Unit,
    onShare: (() -> Unit)?,
    onDownload: () -> Unit,
    textKind: Boolean,
    showSource: Boolean,
    onShowSource: (Boolean) -> Unit,
) {
    val colors = deepDeepColors()
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = DdSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DdIconButton(
            icon = DdIcons.Close,
            onClick = onDismiss,
            contentDescription = stringResource(R.string.assistant_close),
            modifier = Modifier.testTag("file_viewer_close"),
            size = 44.dp,
            iconSize = 18.dp,
            tint = colors.inkSecondary,
        )
        Text(
            text = name,
            modifier = Modifier.weight(1f),
            style = DdType.label,
            color = colors.ink,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (textKind) {
            DdIconButton(
                icon = DdIcons.Eye,
                onClick = { onShowSource(false) },
                contentDescription = stringResource(R.string.files_preview),
                modifier = Modifier.testTag("file_viewer_mode_preview"),
                size = 40.dp,
                iconSize = 17.dp,
                tint = if (showSource) colors.inkTertiary else colors.ink,
            )
            DdIconButton(
                icon = DdIcons.Raw,
                onClick = { onShowSource(true) },
                contentDescription = stringResource(R.string.files_source),
                modifier = Modifier.testTag("file_viewer_mode_source"),
                size = 40.dp,
                iconSize = 17.dp,
                tint = if (showSource) colors.ink else colors.inkTertiary,
            )
        }
        DdIconButton(
            icon = DdIcons.Download,
            onClick = onDownload,
            contentDescription = stringResource(R.string.files_download),
            modifier = Modifier.testTag("file_viewer_download"),
            size = 44.dp,
            iconSize = 17.dp,
            tint = colors.inkSecondary,
        )
        if (onShare != null) {
            DdIconButton(
                icon = DdIcons.Share,
                onClick = onShare,
                contentDescription = stringResource(R.string.assistant_attach_share),
                modifier = Modifier.testTag("file_viewer_share"),
                size = 44.dp,
                iconSize = 17.dp,
                tint = colors.inkSecondary,
            )
        }
    }
}

@Composable
private fun rememberTextReadState(file: File): State<TextReadState> =
    produceState<TextReadState>(initialValue = TextReadState.Loading, file.absolutePath) {
        value = withContext(Dispatchers.IO) {
            readTextLimited(file)?.let(TextReadState::Ready) ?: TextReadState.Failed
        }
    }

@Composable
private fun ImageFileBody(
    file: File,
    name: String,
    onOpenExternal: () -> Unit,
    onLongPress: () -> Unit,
) {
    if (animatedImageFile(file.absolutePath) != null) {
        AnimatedImage(
            file = file,
            modifier = Modifier.fillMaxSize(),
            onLongPress = onLongPress,
        )
        return
    }
    val state by produceState<ImageReadState>(initialValue = ImageReadState.Loading, file.absolutePath) {
        value = withContext(Dispatchers.IO) {
            decodePreviewBitmap(file)?.let(ImageReadState::Ready) ?: ImageReadState.Failed
        }
    }
    when (val current = state) {
        ImageReadState.Loading -> LoadingBody()
        ImageReadState.Failed -> MessageFileBody(
            file = file,
            name = name,
            message = stringResource(R.string.files_read_failed),
            onOpenExternal = onOpenExternal,
        )

        is ImageReadState.Ready -> Image(
            bitmap = current.bitmap.asImageBitmap(),
            contentDescription = name,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(file.absolutePath) { detectTapGestures(onLongPress = { onLongPress() }) },
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun TextFileBody(
    file: File,
    kind: FilePreviewKind,
    showSource: Boolean,
    onOpenExternal: () -> Unit,
) {
    val state by rememberTextReadState(file)
    when {
        showSource -> when (val current = state) {
            TextReadState.Loading -> LoadingBody()
            TextReadState.Failed -> MessageFileBody(
                file = file,
                name = file.name,
                message = stringResource(R.string.files_read_failed),
                onOpenExternal = onOpenExternal,
            )

            is TextReadState.Ready -> CodeSourceView(
                text = current.content.text,
                language = if (kind == FilePreviewKind.HTML) "html" else null,
                truncated = current.content.truncated,
            )
        }

        kind == FilePreviewKind.HTML -> HtmlFileView(
            path = file.absolutePath,
            modifier = Modifier.fillMaxSize(),
        )

        else -> when (val current = state) {
            TextReadState.Loading -> LoadingBody()
            TextReadState.Failed -> MessageFileBody(
                file = file,
                name = file.name,
                message = stringResource(R.string.files_read_failed),
                onOpenExternal = onOpenExternal,
            )

            is TextReadState.Ready -> MarkdownBody(current.content.text)
        }
    }
}

@Composable
private fun CodeFileBody(file: File, name: String, onOpenExternal: () -> Unit) {
    val state by rememberTextReadState(file)
    when (val current = state) {
        TextReadState.Loading -> LoadingBody()
        TextReadState.Failed -> MessageFileBody(
            file = file,
            name = name,
            message = stringResource(R.string.files_read_failed),
            onOpenExternal = onOpenExternal,
        )

        is TextReadState.Ready -> CodeSourceView(
            text = current.content.text,
            language = codeLanguageFor(name),
            truncated = current.content.truncated,
        )
    }
}

@Composable
private fun MarkdownBody(text: String) {
    val colors = deepDeepColors()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = DdSpacing.Card, vertical = DdSpacing.Lg),
    ) {
        if (text.isEmpty()) {
            Text(
                text = stringResource(R.string.files_empty_file),
                style = DdType.bodySmall,
                color = colors.inkTertiary,
            )
        } else {
            ChatMarkdown(text)
        }
    }
}

@Composable
internal fun CodeSourceView(text: String, language: String?, truncated: Boolean = false) {
    val colors = deepDeepColors()
    val context = LocalContext.current
    val copied = stringResource(R.string.files_copied)
    val highlighted = remember(text, language, colors) { highlightedCode(text, language, colors) }
    val lineNumbers = remember(text) {
        if (text.isEmpty()) "" else (1..(text.count { it == '\n' } + 1)).joinToString("\n")
    }
    val gutterWidth = remember(lineNumbers) {
        val digits = (lineNumbers.count { it == '\n' } + 1).toString().length
        (digits * 8 + 14).dp
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = DdSpacing.Xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (truncated) {
                Text(
                    text = stringResource(R.string.files_truncated),
                    modifier = Modifier.weight(1f),
                    style = DdType.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                    color = colors.inkTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            DdIconButton(
                icon = DdIcons.Copy,
                onClick = {
                    if (copyToClipboard(context, text)) {
                        Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                    }
                },
                contentDescription = stringResource(R.string.files_copy_source),
                modifier = Modifier.testTag("file_viewer_copy"),
                size = 40.dp,
                iconSize = 15.dp,
                tint = colors.inkTertiary,
            )
        }
        if (text.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.files_empty_file),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                )
            }
        } else {
            Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Text(
                    text = lineNumbers,
                    modifier = Modifier.width(gutterWidth).padding(start = DdSpacing.Sm, top = DdSpacing.Sm, bottom = DdSpacing.Sm),
                    style = codeSourceStyle,
                    color = colors.inkTertiary,
                    textAlign = TextAlign.End,
                    softWrap = false,
                )
                Box(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                    Text(
                        text = highlighted,
                        modifier = Modifier.padding(start = 10.dp, end = DdSpacing.Lg, top = DdSpacing.Sm, bottom = DdSpacing.Sm),
                        style = codeSourceStyle,
                        color = colors.inkSecondary,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoFileBody(file: File, onOpenExternal: () -> Unit) {
    var failed by remember(file.absolutePath) { mutableStateOf(false) }
    var videoView by remember(file.absolutePath) { mutableStateOf<VideoView?>(null) }
    DisposableEffect(file.absolutePath) {
        onDispose {
            videoView?.let(::stopVideoView)
            videoView = null
        }
    }
    if (failed) {
        MessageFileBody(
            file = file,
            name = file.name,
            message = stringResource(R.string.files_unplayable),
            onOpenExternal = onOpenExternal,
            icon = DdIcons.Play,
        )
    } else {
        AndroidView(
            factory = { context ->
                FrameLayout(context).apply {
                    val view = VideoView(context)
                    view.setVideoPath(file.absolutePath)
                    view.setMediaController(
                        MediaController(context).also { controller -> controller.setAnchorView(view) },
                    )
                    view.setOnPreparedListener { player -> player.start() }
                    view.setOnErrorListener { _, _, _ ->
                        failed = true
                        true
                    }
                    addView(
                        view,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        ),
                    )
                    videoView = view
                }
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = { frame ->
                videoView?.let(::stopVideoView)
                videoView = null
                frame.removeAllViews()
            },
        )
    }
}

private fun stopVideoView(view: VideoView) {
    runCatching { view.stopPlayback() }
    runCatching { view.setMediaController(null) }
}

@Composable
private fun LoadingBody() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        DdSpinner(size = 22.dp, strokeWidth = 2.dp)
    }
}

@Composable
private fun MessageFileBody(
    file: File,
    name: String,
    message: String,
    onOpenExternal: () -> Unit,
    icon: DdIcon = DdIcons.File,
) {
    val colors = deepDeepColors()
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = DdSpacing.Xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        DdIcon(icon = icon, tint = colors.inkTertiary, size = 48.dp)
        Spacer(Modifier.height(DdSpacing.Lg))
        Text(
            text = name,
            style = DdType.label,
            color = colors.ink,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = formatAttachmentSize(file.length()),
            style = DdType.bodySmall,
            color = colors.inkTertiary,
        )
        Spacer(Modifier.height(DdSpacing.Xs))
        Text(
            text = message,
            style = DdType.bodySmall,
            color = colors.inkTertiary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(22.dp))
        DdPrimaryButton(
            text = stringResource(R.string.files_open_external),
            onClick = onOpenExternal,
            modifier = Modifier.widthIn(max = 260.dp).fillMaxWidth(),
        )
    }
}

private fun decodePreviewBitmap(file: File): Bitmap? {
    if (!file.isFile) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > IMAGE_PREVIEW_LIMIT || bounds.outHeight / sample > IMAGE_PREVIEW_LIMIT) {
        sample *= 2
    }
    return runCatching {
        BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    }.getOrNull()
}
