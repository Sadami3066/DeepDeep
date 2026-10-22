package io.github.whywood.deepdeep.ui.chat

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
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

@Composable
internal fun ChatMarkdown(text: String, modifier: Modifier = Modifier) {
    val colors = deepDeepColors()
    val blocks = remember(text) { parseMarkdown(text) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> Text(
                    text = inline(block.text, colors),
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
                    text = inline(block.text, colors),
                    style = DdType.body,
                    color = colors.ink,
                )

                is MarkdownBlock.Code -> CodeBlock(block.language, block.code, colors)

                is MarkdownBlock.Quote -> Row(Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .width(2.dp)
                            .heightIn(min = 22.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(colors.hairline),
                    )
                    Text(
                        text = inline(block.text, colors),
                        modifier = Modifier.padding(start = 12.dp),
                        style = DdType.body,
                        color = colors.inkSecondary,
                    )
                }

                is MarkdownBlock.Bullets -> BulletBlock(block, colors)

                is MarkdownBlock.Tasks -> TaskBlock(block, colors)

                is MarkdownBlock.Table -> TableBlock(block, colors)

                MarkdownBlock.Rule -> DdHairline(Modifier.padding(vertical = 2.dp))
            }
        }
    }
}

@Composable
private fun CodeBlock(language: String?, code: String, colors: DeepDeepColors) {
    val context = LocalContext.current
    val copied = stringResource(R.string.assistant_copied)
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
                text = code,
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

@Composable
private fun BulletBlock(block: MarkdownBlock.Bullets, colors: DeepDeepColors) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        block.items.forEachIndexed { index, item ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    text = if (block.ordered) "${block.start + index}." else "•",
                    modifier = Modifier.width(22.dp),
                    style = DdType.body,
                    color = colors.inkTertiary,
                )
                Text(
                    text = inline(item, colors),
                    modifier = Modifier.weight(1f),
                    style = DdType.body,
                    color = colors.ink,
                )
            }
        }
    }
}

@Composable
private fun TaskBlock(block: MarkdownBlock.Tasks, colors: DeepDeepColors) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        block.items.forEach { item ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .padding(top = 3.dp)
                        .size(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (item.checked) colors.accent else colors.wash)
                        .border(1.dp, if (item.checked) colors.accent else colors.hairline, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (item.checked) {
                        DdIcon(icon = DdIcons.Check, tint = colors.onAccent, size = 10.dp)
                    }
                }
                Text(
                    text = inline(item.text, colors),
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                    style = DdType.body,
                    color = if (item.checked) colors.inkTertiary else colors.ink,
                )
            }
        }
    }
}

@Composable
private fun TableBlock(block: MarkdownBlock.Table, colors: DeepDeepColors) {
    val columns = maxOf(block.header.size, block.rows.maxOfOrNull { it.size } ?: 0)
    if (columns == 0) return
    val weights = List(columns) { column ->
        val longest = (block.header.getOrNull(column).orEmpty().length) +
            (block.rows.maxOfOrNull { it.getOrNull(column).orEmpty().length } ?: 0)
        longest.coerceIn(8, 48).toFloat()
    }
    Column(Modifier.fillMaxWidth()) {
        TableRow(block.header, weights, colors, header = true)
        DdHairline()
        block.rows.forEachIndexed { index, row ->
            TableRow(row, weights, colors, header = false)
            if (index != block.rows.lastIndex) DdHairline()
        }
    }
}

@Composable
private fun TableRow(
    cells: List<String>,
    weights: List<Float>,
    colors: DeepDeepColors,
    header: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        weights.forEachIndexed { index, weight ->
            Text(
                text = inline(cells.getOrNull(index).orEmpty(), colors),
                modifier = Modifier.weight(weight).padding(end = 8.dp),
                style = DdType.bodySmall.copy(
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = if (header) colors.ink else colors.inkSecondary,
            )
        }
    }
}

private fun inline(text: String, colors: DeepDeepColors): AnnotatedString = buildAnnotatedString {
    appendInline(text, colors)
}

private fun AnnotatedString.Builder.appendInline(text: String, colors: DeepDeepColors) {
    var index = 0
    while (index < text.length) {
        when {
            text.startsWith("**", index) -> {
                val end = text.indexOf("**", index + 2)
                if (end < 0) {
                    append("**")
                    index += 2
                } else {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        appendInline(text.substring(index + 2, end), colors)
                    }
                    index = end + 2
                }
            }

            text.startsWith("~~", index) -> {
                val end = text.indexOf("~~", index + 2)
                if (end < 0) {
                    append("~~")
                    index += 2
                } else {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        appendInline(text.substring(index + 2, end), colors)
                    }
                    index = end + 2
                }
            }

            text[index] == '`' -> {
                val end = text.indexOf('`', index + 1)
                if (end < 0) {
                    append('`')
                    index++
                } else {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = colors.wash,
                        ),
                    ) {
                        append(text.substring(index + 1, end))
                    }
                    index = end + 1
                }
            }

            text[index] == '[' -> {
                val close = text.indexOf(']', index + 1)
                val open = if (close >= 0) text.indexOf('(', close + 1) else -1
                val end = if (open >= 0) text.indexOf(')', open + 1) else -1
                if (close > index && open == close + 1 && end > open) {
                    withLink(LinkAnnotation.Url(text.substring(open + 1, end))) {
                        withStyle(
                            SpanStyle(
                                color = colors.accent,
                                textDecoration = TextDecoration.Underline,
                            ),
                        ) {
                            append(text.substring(index + 1, close))
                        }
                    }
                    index = end + 1
                } else {
                    append('[')
                    index++
                }
            }

            text[index] == '*' -> {
                val end = text.indexOf('*', index + 1)
                if (end > index + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        appendInline(text.substring(index + 1, end), colors)
                    }
                    index = end + 1
                } else {
                    append('*')
                    index++
                }
            }

            else -> {
                append(text[index])
                index++
            }
        }
    }
}
