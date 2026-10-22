package io.github.whywood.deepdeep.ui.chat

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.DeepDeepColors
import io.github.whywood.deepdeep.design.deepDeepColors

private val tableCellStyle = DdType.bodySmall.copy(fontSize = 14.sp, lineHeight = 20.sp)
private val tableHeaderStyle = tableCellStyle.copy(fontWeight = FontWeight.SemiBold)

@Composable
internal fun ChatMarkdown(text: String, modifier: Modifier = Modifier) {
    val colors = deepDeepColors()
    val document = remember(text) { parseMarkdownDocument(text) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MarkdownBlocks(document.blocks, document, colors)
        if (document.footnotes.isNotEmpty()) {
            FootnoteList(document.footnotes, document, colors)
        }
    }
}

@Composable
private fun MarkdownBlocks(
    blocks: List<MarkdownBlock>,
    document: MarkdownDocument,
    colors: DeepDeepColors,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        blocks.forEach { block -> MarkdownBlockItem(block, document, colors) }
    }
}

@Composable
private fun MarkdownBlockItem(block: MarkdownBlock, document: MarkdownDocument, colors: DeepDeepColors) {
    when (block) {
        is MarkdownBlock.Heading -> Text(
            text = rememberedInline(block.text, document, colors),
            style = DdType.body.copy(
                fontSize = when (block.level) {
                    1 -> 22.sp
                    2 -> 19.sp
                    else -> 17.sp
                },
                lineHeight = when (block.level) {
                    1 -> 31.sp
                    2 -> 27.sp
                    else -> 24.sp
                },
                fontWeight = FontWeight.SemiBold,
            ),
            color = colors.ink,
        )

        is MarkdownBlock.Paragraph -> Text(
            text = rememberedInline(block.text, document, colors),
            style = DdType.body,
            color = colors.ink,
        )

        is MarkdownBlock.Code -> CodeBlock(block.language, block.code, colors)

        is MarkdownBlock.Quote -> QuoteBlock(block, document, colors)

        is MarkdownBlock.Image -> MarkdownImage(block.alt, block.source, block.title, colors)

        is MarkdownBlock.ListBlock -> ListBlockBody(block.items, document, colors, depth = 0)

        is MarkdownBlock.Table -> TableBlock(block, document, colors)

        is MarkdownBlock.Math -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = block.text,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.wash)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                style = DdType.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                ),
                color = colors.inkSecondary,
                textAlign = TextAlign.Center,
            )
        }

        MarkdownBlock.Rule -> DdHairline(Modifier.padding(vertical = 2.dp))
    }
}

@Composable
private fun QuoteBlock(block: MarkdownBlock.Quote, document: MarkdownDocument, colors: DeepDeepColors) {
    val barColor = colors.hairline
    Box(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset.Zero,
                    size = Size(2.dp.toPx(), size.height),
                    cornerRadius = CornerRadius(1.dp.toPx()),
                )
            },
    ) {
        MarkdownBlocks(block.blocks, document, colors, Modifier.fillMaxWidth().padding(start = 14.dp))
    }
}

@Composable
private fun FootnoteList(
    footnotes: List<MarkdownFootnote>,
    document: MarkdownDocument,
    colors: DeepDeepColors,
) {
    Column(Modifier.fillMaxWidth()) {
        DdHairline()
        Column(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            footnotes.forEach { footnote ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Text(
                        text = "${footnote.number}.",
                        modifier = Modifier.width(22.dp),
                        style = DdType.bodySmall,
                        color = colors.inkTertiary,
                    )
                    Text(
                        text = rememberedInline(footnote.text, document, colors),
                        modifier = Modifier.weight(1f),
                        style = DdType.bodySmall,
                        color = colors.inkTertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun CodeBlock(language: String?, code: String, colors: DeepDeepColors) {
    val context = LocalContext.current
    val copied = stringResource(R.string.assistant_copied)
    val highlighted = remember(code, language, colors) {
        val tokens = highlightCode(code, language)
        if (tokens.isEmpty()) {
            AnnotatedString(code)
        } else {
            buildAnnotatedString {
                append(code)
                tokens.forEach { token ->
                    addStyle(SpanStyle(color = codeColor(token.type, colors)), token.start, token.end)
                }
            }
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.wash)
            .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = language?.uppercase().orEmpty().ifBlank { "CODE" },
                modifier = Modifier.weight(1f),
                style = DdType.micro,
                color = colors.inkTertiary,
            )
            DdIconButton(
                icon = DdIcons.Copy,
                onClick = {
                    if (copyToClipboard(context, code)) {
                        Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                    }
                },
                contentDescription = stringResource(R.string.assistant_tool_copy),
                modifier = Modifier.testTag("markdown_code_copy"),
                size = 30.dp,
                iconSize = 14.dp,
                tint = colors.inkTertiary,
            )
        }
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Text(
                text = highlighted,
                style = DdType.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                ),
                color = colors.inkSecondary,
                softWrap = false,
            )
        }
    }
}

private fun codeColor(type: CodeTokenType, colors: DeepDeepColors): Color = when (type) {
    CodeTokenType.KEYWORD, CodeTokenType.ANNOTATION -> colors.codeKeyword
    CodeTokenType.STRING -> colors.codeString
    CodeTokenType.COMMENT -> colors.codeComment
    CodeTokenType.NUMBER -> colors.codeNumber
    CodeTokenType.FUNCTION -> colors.codeFunction
    CodeTokenType.TYPE -> colors.codeType
}

@Composable
private fun ListBlockBody(
    items: List<MarkdownListItem>,
    document: MarkdownDocument,
    colors: DeepDeepColors,
    depth: Int,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { item -> ListItemBody(item, document, colors, depth) }
    }
}

@Composable
private fun ListItemBody(
    item: MarkdownListItem,
    document: MarkdownDocument,
    colors: DeepDeepColors,
    depth: Int,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            ListMarker(item.marker, colors, depth)
            Text(
                text = rememberedInline(item.text, document, colors),
                modifier = Modifier.weight(1f).padding(start = 4.dp),
                style = DdType.body,
                color = if (item.marker is MarkdownMarker.Task && item.marker.checked) {
                    colors.inkTertiary
                } else {
                    colors.ink
                },
            )
        }
        if (item.children.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, top = 2.dp)) {
                ListBlockBody(item.children, document, colors, depth + 1)
            }
        }
    }
}

@Composable
private fun ListMarker(marker: MarkdownMarker, colors: DeepDeepColors, depth: Int) {
    when (marker) {
        MarkdownMarker.Bullet -> Text(
            text = if (depth == 0) "•" else "◦",
            modifier = Modifier.width(24.dp),
            style = DdType.body,
            color = colors.inkTertiary.copy(alpha = if (depth == 0) 1f else 0.72f),
        )

        is MarkdownMarker.Ordered -> Text(
            text = "${marker.number}.",
            modifier = Modifier.width(24.dp),
            style = DdType.body,
            color = colors.inkTertiary,
        )

        is MarkdownMarker.Task -> Box(Modifier.width(24.dp)) {
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .size(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (marker.checked) colors.accent else colors.wash)
                    .border(
                        width = 1.dp,
                        color = if (marker.checked) colors.accent else colors.hairline,
                        shape = RoundedCornerShape(4.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (marker.checked) DdIcon(icon = DdIcons.Check, tint = colors.onAccent, size = 10.dp)
            }
        }
    }
}

@Composable
private fun TableBlock(block: MarkdownBlock.Table, document: MarkdownDocument, colors: DeepDeepColors) {
    val columns = maxOf(block.header.size, block.rows.maxOfOrNull { it.size } ?: 0)
    if (columns == 0) return
    val textMeasurer = rememberTextMeasurer()
    val naturalWidths = remember(block, document.links, document.footnoteNumbers, colors, textMeasurer) {
        List(columns) { column ->
            var widest = 0
            fun measure(cell: String, style: TextStyle) {
                if (cell.isEmpty()) return
                val measured = textMeasurer.measure(
                    text = renderInline(cell, document, colors),
                    style = style,
                    overflow = TextOverflow.Clip,
                    softWrap = false,
                    maxLines = 1,
                )
                if (measured.size.width > widest) widest = measured.size.width
            }
            measure(block.header.getOrNull(column).orEmpty(), tableHeaderStyle)
            block.rows.forEach { row -> measure(row.getOrNull(column).orEmpty(), tableCellStyle) }
            widest
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val horizontalPadding = 12.dp
        val verticalPadding = 10.dp
        val paddingPx = with(density) { (horizontalPadding * 2).roundToPx() }
        val minWidthPx = with(density) { 64.dp.roundToPx() }
        val maxWidthPx = with(density) { 240.dp.roundToPx() }
        val availablePx = constraints.maxWidth
        val clamped = naturalWidths.map { (it + paddingPx).coerceIn(minWidthPx, maxWidthPx) }
        val widths = distributeRemaining(clamped, naturalWidths, availablePx, maxWidthPx)
        val totalPx = widths.sum()
        val widthDps = widths.map { with(density) { it.toDp() } }
        val scrollState = rememberScrollState()
        Box(Modifier.fillMaxWidth().horizontalScroll(scrollState, enabled = totalPx > availablePx)) {
            Column(
                Modifier
                    .width(with(density) { totalPx.toDp() })
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, colors.hairline, RoundedCornerShape(12.dp)),
            ) {
                TableRow(block.header, widthDps, document, colors, header = true, horizontalPadding, verticalPadding)
                DdHairline()
                block.rows.forEachIndexed { index, row ->
                    TableRow(row, widthDps, document, colors, header = false, horizontalPadding, verticalPadding)
                    if (index != block.rows.lastIndex) DdHairline()
                }
            }
        }
    }
}

@Composable
private fun TableRow(
    cells: List<String>,
    widths: List<Dp>,
    document: MarkdownDocument,
    colors: DeepDeepColors,
    header: Boolean,
    horizontalPadding: Dp,
    verticalPadding: Dp,
) {
    val style = if (header) tableHeaderStyle else tableCellStyle
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (header) Modifier.background(colors.wash) else Modifier),
        verticalAlignment = Alignment.Top,
    ) {
        widths.forEachIndexed { index, width ->
            Box(Modifier.width(width).padding(horizontal = horizontalPadding, vertical = verticalPadding)) {
                Text(
                    text = rememberedInline(cells.getOrNull(index).orEmpty(), document, colors),
                    style = style,
                    color = if (header) colors.ink else colors.inkSecondary,
                )
            }
        }
    }
}

private fun distributeRemaining(
    base: List<Int>,
    natural: List<Int>,
    available: Int,
    maxWidth: Int,
): List<Int> {
    if (available <= 0 || available == Int.MAX_VALUE) return base
    val widths = base.toMutableList()
    var remaining = available - widths.sum()
    var passes = 0
    while (remaining > 0 && passes < 4) {
        val open = widths.indices.filter { widths[it] < maxWidth }
        if (open.isEmpty()) break
        val weight = open.sumOf { (natural[it] + 1).toDouble() }
        var used = 0
        open.forEach { index ->
            val share = ((natural[index] + 1) / weight * remaining).toInt()
            val add = minOf(share, maxWidth - widths[index])
            widths[index] += add
            used += add
        }
        if (used <= 0) break
        remaining -= used
        passes++
    }
    return widths
}

@Composable
private fun rememberedInline(
    text: String,
    document: MarkdownDocument,
    colors: DeepDeepColors,
): AnnotatedString = remember(text, document.links, document.footnoteNumbers, colors) {
    renderInline(text, document, colors)
}

private fun renderInline(text: String, document: MarkdownDocument, colors: DeepDeepColors): AnnotatedString =
    buildAnnotatedString {
        appendInlineNodes(inlineNodes(text, document.links, document.footnoteNumbers), colors)
    }

private fun AnnotatedString.Builder.appendInlineNodes(nodes: List<InlineNode>, colors: DeepDeepColors) {
    nodes.forEach { node ->
        when (node) {
            is InlineNode.Text -> append(node.text)

            is InlineNode.Code -> withStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = colors.wash),
            ) {
                append(node.text)
            }

            is InlineNode.Kbd -> withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    background = colors.wash,
                    color = colors.inkSecondary,
                ),
            ) {
                append(node.text)
            }

            is InlineNode.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                appendInlineNodes(node.children, colors)
            }

            is InlineNode.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                appendInlineNodes(node.children, colors)
            }

            is InlineNode.Strike -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                appendInlineNodes(node.children, colors)
            }

            is InlineNode.Link -> withLink(LinkAnnotation.Url(node.destination)) {
                withStyle(SpanStyle(color = colors.accent, textDecoration = TextDecoration.Underline)) {
                    appendInlineNodes(node.children, colors)
                }
            }

            is InlineNode.Colored -> withStyle(SpanStyle(color = Color(node.color))) {
                appendInlineNodes(node.children, colors)
            }

            is InlineNode.Superscript -> withStyle(SpanStyle(baselineShift = BaselineShift.Superscript)) {
                appendInlineNodes(node.children, colors)
            }

            is InlineNode.Subscript -> withStyle(SpanStyle(baselineShift = BaselineShift.Subscript)) {
                appendInlineNodes(node.children, colors)
            }

            is InlineNode.Math -> withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    fontStyle = FontStyle.Italic,
                    color = colors.inkTertiary,
                ),
            ) {
                append(node.text)
            }

            is InlineNode.FootnoteRef -> withStyle(
                SpanStyle(color = colors.accent, baselineShift = BaselineShift.Superscript),
            ) {
                append("[${node.number}]")
            }
        }
    }
}
