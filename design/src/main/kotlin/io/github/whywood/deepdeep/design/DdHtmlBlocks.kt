package io.github.whywood.deepdeep.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

fun interface DdHtmlImageLoader {
    suspend fun load(source: String): ImageBitmap?
}

private val HtmlImageTagRegex = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE)
private val HtmlImageMaxHeight = 360.dp
private val HtmlImagePlaceholderHeight = 160.dp

internal sealed interface DdHtmlSegment {
    data class Text(val html: String) : DdHtmlSegment

    data class Image(val source: String) : DdHtmlSegment
}

internal fun htmlSegments(html: String): List<DdHtmlSegment> {
    if (html.isBlank()) return emptyList()
    val segments = mutableListOf<DdHtmlSegment>()
    var cursor = 0
    HtmlImageTagRegex.findAll(html).forEach { match ->
        if (match.range.first > cursor) {
            val text = html.substring(cursor, match.range.first)
            if (text.isNotBlank()) segments += DdHtmlSegment.Text(text)
        }
        val source = attribute(match.value, "src")
            ?.let(::decodeEntities)
            ?.trim()
            .orEmpty()
        if (source.isNotEmpty()) segments += DdHtmlSegment.Image(source)
        cursor = match.range.last + 1
    }
    if (cursor < html.length) {
        val text = html.substring(cursor)
        if (text.isNotBlank()) segments += DdHtmlSegment.Text(text)
    }
    return segments
}

private sealed interface DdHtmlImageState {
    data object Loading : DdHtmlImageState
    data object Failed : DdHtmlImageState
    data class Ready(val bitmap: ImageBitmap) : DdHtmlImageState
}

@Composable
fun DdHtmlBlocks(
    html: String,
    modifier: Modifier = Modifier,
    loader: DdHtmlImageLoader? = null,
    onLinkClick: ((String) -> Unit)? = null,
    onImageClick: ((String) -> Unit)? = null,
    imageLabel: String? = null,
    style: TextStyle = DdType.body,
    color: Color = deepDeepColors().ink,
    linkColor: Color = deepDeepColors().accent,
) {
    val colors = deepDeepColors()
    val segments = remember(html) { htmlSegments(html) }
    val listener = remember(onLinkClick) {
        onLinkClick?.let { callback ->
            object : LinkInteractionListener {
                override fun onClick(link: LinkAnnotation) {
                    if (link is LinkAnnotation.Url) callback(link.url)
                }
            }
        }
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DdSpacing.Md)) {
        segments.forEach { segment ->
            when (segment) {
                is DdHtmlSegment.Text -> {
                    val formatted = remember(segment.html, colors, listener, linkColor) {
                        DdHtmlWriter(colors, listener, linkColor, imageLabel = null).write(segment.html)
                    }
                    if (formatted.isNotEmpty()) {
                        DdText(text = formatted, style = style, color = color)
                    }
                }

                is DdHtmlSegment.Image -> HtmlImageBlock(
                    source = segment.source,
                    loader = loader,
                    onImageClick = onImageClick,
                    imageLabel = imageLabel,
                    colors = colors,
                )
            }
        }
    }
}

@Composable
private fun HtmlImageBlock(
    source: String,
    loader: DdHtmlImageLoader?,
    onImageClick: ((String) -> Unit)?,
    imageLabel: String?,
    colors: DeepDeepColors,
) {
    val state by produceState<DdHtmlImageState>(initialValue = DdHtmlImageState.Loading, source, loader) {
        value = if (loader == null) {
            DdHtmlImageState.Failed
        } else {
            val bitmap = runCatching { loader.load(source) }.getOrNull()
            if (bitmap == null) DdHtmlImageState.Failed else DdHtmlImageState.Ready(bitmap)
        }
    }
    when (val current = state) {
        DdHtmlImageState.Loading -> Box(
            Modifier
                .fillMaxWidth()
                .height(HtmlImagePlaceholderHeight)
                .clip(DdShapes.Medium)
                .background(colors.wash),
        )

        DdHtmlImageState.Failed -> Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp)
                .clip(DdShapes.Medium)
                .background(colors.wash)
                .padding(DdSpacing.Lg),
            contentAlignment = Alignment.Center,
        ) {
            if (imageLabel != null) {
                DdText(
                    text = imageLabel,
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                )
            } else {
                DdIcon(icon = DdIcons.Image, tint = colors.inkTertiary, size = 22.dp)
            }
        }

        is DdHtmlImageState.Ready -> BoxWithConstraints(Modifier.fillMaxWidth()) {
            val aspect = current.bitmap.width.toFloat() / current.bitmap.height.coerceAtLeast(1).toFloat()
            val height = minOf(maxWidth / aspect, HtmlImageMaxHeight).coerceAtLeast(1.dp)
            val click = onImageClick
            Image(
                bitmap = current.bitmap,
                contentDescription = imageLabel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .clip(DdShapes.Medium)
                    .then(
                        if (click == null) {
                            Modifier
                        } else {
                            Modifier
                                .semantics { role = Role.Image }
                                .clickable { click(source) }
                        },
                    ),
                contentScale = ContentScale.Crop,
            )
        }
    }
}
