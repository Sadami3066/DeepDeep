package io.github.whywood.deepdeep.ui.chat

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.DeepDeepColors
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.ToolTrace
import io.github.whywood.deepdeep.ui.workshop.WorkshopCardApp
import io.github.whywood.deepdeep.ui.workshop.WorkshopChatCard
import java.io.File
import java.net.URI
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

enum class PresentedItemType { FILE, IMAGE, HTML, CREATIVE }

data class PresentedItem(
    val type: PresentedItemType,
    val name: String,
    val path: String? = null,
    val mime: String? = null,
    val title: String? = null,
    val id: String? = null,
    val icon: String? = null,
    val version: String? = null,
)

private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "heic", "heif", "avif")
private val HTML_EXTENSIONS = setOf("html", "htm")

fun parsePresentedItems(trace: ToolTrace): List<PresentedItem>? {
    if (trace.name != "present") return null
    val raw = trace.output?.takeIf { it.isNotBlank() } ?: return null
    return parsePresentedItems(raw)
}

internal fun parsePresentedItems(raw: String): List<PresentedItem>? = try {
    val root = Json.parseToJsonElement(raw).jsonObject
    val items = root["items"] as? JsonArray
    val parsed = if (items != null) {
        items.mapNotNull(::parsePresentedItem).distinctBy { it.key }
    } else {
        parseLegacyFiles(root["files"] as? JsonArray).distinctBy { it.key }
    }
    parsed.takeIf { it.isNotEmpty() }
} catch (_: Exception) {
    null
}

private fun parsePresentedItem(element: JsonElement): PresentedItem? {
    val item = element as? JsonObject ?: return null
    val type = when (item.stringOrNull("type")?.trim()?.lowercase(Locale.US)) {
        "file" -> PresentedItemType.FILE
        "image" -> PresentedItemType.IMAGE
        "html" -> PresentedItemType.HTML
        "creative" -> PresentedItemType.CREATIVE
        else -> return null
    }
    val title = item.stringOrNull("title")?.trim()?.takeIf { it.isNotEmpty() }
    if (type == PresentedItemType.CREATIVE) {
        val id = item.stringOrNull("id")?.trim().orEmpty()
        if (id.isEmpty()) return null
        return PresentedItem(
            type = type,
            name = item.stringOrNull("name")?.trim()?.takeIf { it.isNotEmpty() } ?: id,
            title = title,
            id = id,
            icon = item.stringOrNull("icon")?.trim()?.takeIf { it.isNotEmpty() },
            version = item.stringOrNull("version")?.trim()?.takeIf { it.isNotEmpty() },
        )
    }
    val path = item.stringOrNull("path")?.trim().orEmpty()
    if (path.isEmpty()) return null
    return PresentedItem(
        type = type,
        name = item.stringOrNull("name")?.trim()?.takeIf { it.isNotEmpty() } ?: derivePresentedName(path),
        path = path,
        mime = item.stringOrNull("mime")?.trim()?.takeIf { it.isNotEmpty() },
        title = title,
    )
}

private fun parseLegacyFiles(array: JsonArray?): List<PresentedItem> {
    if (array == null) return emptyList()
    return array.mapNotNull { element ->
        val item = element as? JsonObject ?: return@mapNotNull null
        val path = item.stringOrNull("path")?.trim().orEmpty()
        if (path.isEmpty()) return@mapNotNull null
        PresentedItem(
            type = inferPresentedType(path),
            name = derivePresentedName(path),
            path = path,
        )
    }
}

private fun inferPresentedType(path: String): PresentedItemType = when {
    path.extension() in IMAGE_EXTENSIONS -> PresentedItemType.IMAGE
    path.extension() in HTML_EXTENSIONS -> PresentedItemType.HTML
    else -> PresentedItemType.FILE
}

private fun derivePresentedName(path: String): String =
    path.trimEnd('/').substringAfterLast('/').ifBlank { path }

private fun String.extension(): String = substringAfterLast('.', "").lowercase(Locale.US)

private val PresentedItem.key: String
    get() = if (type == PresentedItemType.CREATIVE) "creative:${id.orEmpty()}" else "${type.name}:${path.orEmpty()}"

internal fun resolvePresentedPath(raw: String, root: File): String {
    val trimmed = raw.trim().trim('<', '>', '"', '\'')
    if (trimmed.isEmpty()) return raw
    val file = when {
        trimmed.startsWith("file://") -> File(runCatching { URI(trimmed).path }.getOrNull() ?: return raw)
        trimmed.startsWith("/workspace/") -> File(root, trimmed.removePrefix("/workspace/"))
        trimmed.startsWith("/") -> File(trimmed)
        else -> File(root, trimmed.removePrefix("workspace/"))
    }
    return file.absolutePath
}

@Composable
fun PresentCard(
    items: List<PresentedItem>,
    attachmentRoot: File,
    onOpenFile: (String) -> Unit,
    onShareFile: (String) -> Unit,
    onOpenWorkshop: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    Column(
        modifier = modifier.fillMaxWidth().testTag("present_card"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items.forEach { item ->
            when (item.type) {
                PresentedItemType.IMAGE -> PresentedImageItem(item, attachmentRoot, colors)
                PresentedItemType.HTML -> PresentedHtmlItem(item, attachmentRoot, colors)
                PresentedItemType.FILE -> PresentedFileItem(
                    item = item,
                    colors = colors,
                    onOpen = { onOpenFile(item.path.orEmpty()) },
                    onShare = { onShareFile(item.path.orEmpty()) },
                )

                PresentedItemType.CREATIVE -> PresentedCreativeItem(item, onOpenWorkshop)
            }
        }
    }
}

@Composable
private fun PresentedImageItem(item: PresentedItem, root: File, colors: DeepDeepColors) {
    val path = remember(item.path, root) { resolvePresentedPath(item.path.orEmpty(), root) }
    MarkdownImage(
        alt = item.title ?: item.name,
        source = path,
        title = item.name,
        colors = colors,
    )
}

@Composable
private fun PresentedHtmlItem(item: PresentedItem, root: File, colors: DeepDeepColors) {
    val path = remember(item.path, root) { resolvePresentedPath(item.path.orEmpty(), root) }
    val label = item.title ?: item.name
    val shape = RoundedCornerShape(12.dp)
    var fullscreen by rememberSaveable(path) { mutableStateOf(false) }
    val available = remember(path) { File(path).isFile }
    if (!available) {
        PresentedFailureRow(name = label, colors = colors)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HtmlSurface(
            path = path,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(shape)
                .border(1.dp, colors.hairline.copy(alpha = 0.7f), shape)
                .testTag("present_html"),
            onActivate = { fullscreen = true },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            DdIcon(icon = DdIcons.Globe, tint = colors.inkTertiary, size = 12.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = DdType.bodySmall,
                color = colors.inkTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            DdTextAction(
                text = stringResource(R.string.present_fullscreen),
                onClick = { fullscreen = true },
                color = colors.inkSecondary,
            )
        }
    }
    if (fullscreen) {
        PresentedHtmlDialog(path = path, onDismiss = { fullscreen = false })
    }
}

@Composable
private fun PresentedFileItem(
    item: PresentedItem,
    colors: DeepDeepColors,
    onOpen: () -> Unit,
    onShare: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    DdPressable(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().testTag("present_file"),
        shape = shape,
        background = colors.surface,
        contentDescription = stringResource(R.string.present_open),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.hairline.copy(alpha = 0.7f), shape)
                .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIcon(icon = item.type.icon, tint = colors.inkTertiary, size = 16.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = DdType.body,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = item.title?.takeIf { it != item.name }
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = DdType.bodySmall,
                        color = colors.inkTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            DdIconButton(
                icon = DdIcons.Share,
                onClick = onShare,
                contentDescription = stringResource(R.string.present_share),
                modifier = Modifier.testTag("present_share"),
                size = 36.dp,
                iconSize = 15.dp,
                tint = colors.inkTertiary,
            )
            DdIcon(icon = DdIcons.ChevronRight, tint = colors.inkTertiary, size = 14.dp)
            Spacer(Modifier.width(6.dp))
        }
    }
}

@Composable
private fun PresentedCreativeItem(item: PresentedItem, onOpenWorkshop: (String) -> Unit) {
    WorkshopChatCard(
        app = WorkshopCardApp(
            id = item.id.orEmpty(),
            name = item.title ?: item.name,
            icon = item.icon.orEmpty().ifBlank { "✦" },
            version = item.version.orEmpty(),
        ),
        onOpen = onOpenWorkshop,
        subtitle = stringResource(R.string.present_creative),
    )
}

@Composable
private fun PresentedFailureRow(name: String, colors: DeepDeepColors) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.wash)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DdIcon(icon = DdIcons.Globe, tint = colors.inkTertiary, size = 15.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            text = name,
            modifier = Modifier.weight(1f),
            style = DdType.bodySmall,
            color = colors.inkSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(R.string.present_failed),
            style = DdType.bodySmall,
            color = colors.inkTertiary,
        )
    }
}

private val PresentedItemType.icon: DdIcon
    get() = when (this) {
        PresentedItemType.IMAGE -> DdIcons.Image
        PresentedItemType.HTML -> DdIcons.Globe
        PresentedItemType.FILE, PresentedItemType.CREATIVE -> DdIcons.File
    }

private class HtmlWebViewHolder {
    private var view: WebView? = null

    fun attach(webView: WebView) {
        view = webView
    }

    fun release() {
        val webView = view ?: return
        view = null
        runCatching { webView.stopLoading() }
        runCatching { webView.destroy() }
    }
}

@Composable
private fun HtmlSurface(
    path: String,
    modifier: Modifier,
    onActivate: (() -> Unit)? = null,
) {
    val colors = deepDeepColors()
    val holder = remember(path) { HtmlWebViewHolder() }
    var crashKey by remember(path) { mutableIntStateOf(0) }
    var renderFailed by remember(path) { mutableStateOf(false) }
    DisposableEffect(path) {
        onDispose { holder.release() }
    }
    Box(modifier) {
        if (renderFailed) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.present_failed),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                )
            }
        } else {
            key(crashKey) {
                AndroidView(
                    factory = { context ->
                        createHtmlWebView(context, path) {
                            holder.release()
                            if (crashKey == 0) crashKey++ else renderFailed = true
                        }.also(holder::attach)
                    },
                    modifier = Modifier.fillMaxSize(),
                    onRelease = { holder.release() },
                )
            }
        }
        if (onActivate != null && !renderFailed) {
            Box(Modifier.matchParentSize().clickable(onClick = onActivate))
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createHtmlWebView(context: Context, path: String, onRenderCrash: () -> Unit): WebView = WebView(context).apply {
    setBackgroundColor(AndroidColor.TRANSPARENT)
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        allowFileAccess = true
        allowContentAccess = false
        @Suppress("DEPRECATION")
        allowFileAccessFromFileURLs = false
        @Suppress("DEPRECATION")
        allowUniversalAccessFromFileURLs = false
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        mediaPlaybackRequiresUserGesture = false
        setSupportZoom(false)
        builtInZoomControls = false
        displayZoomControls = false
    }
    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            if (uri.scheme == "file") {
                val folder = File(path).parentFile?.absolutePath ?: return true
                val target = uri.path ?: return true
                return !target.startsWith(folder)
            }
            val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
            return true
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail?): Boolean {
            onRenderCrash()
            return true
        }
    }
    webChromeClient = object : WebChromeClient() {
        override fun onConsoleMessage(message: ConsoleMessage): Boolean = true
    }
    loadUrl(Uri.fromFile(File(path)).toString())
}

@Composable
private fun PresentedHtmlDialog(path: String, onDismiss: () -> Unit) {
    val colors = deepDeepColors()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(colors.paper)) {
            HtmlSurface(path = path, modifier = Modifier.fillMaxSize())
            DdIconButton(
                icon = DdIcons.Close,
                onClick = onDismiss,
                contentDescription = stringResource(R.string.assistant_close),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 8.dp, end = 10.dp)
                    .testTag("present_html_close"),
                tint = colors.ink,
            )
        }
    }
}

private fun JsonObject.stringOrNull(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull
