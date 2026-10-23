package io.github.whywood.deepdeep.ui.chat

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.takeOrElse
import io.github.whywood.deepdeep.design.deepDeepColors

internal data class RenderedInline(
    val text: AnnotatedString,
    val codeRanges: List<IntRange>,
)

@Composable
internal fun InlineCodeText(
    rendered: RenderedInline,
    modifier: Modifier = Modifier,
    style: TextStyle,
    color: Color,
) {
    val colors = deepDeepColors()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = rendered.text,
        modifier = modifier.drawBehind {
            val textLayout = layout ?: return@drawBehind
            if (rendered.codeRanges.isEmpty()) return@drawBehind
            val horizontalPadding = 3.dp.toPx()
            val fontSize = style.fontSize.takeOrElse { 15.sp }.toPx()
            val ascent = fontSize * 0.84f
            val descent = fontSize * 0.32f
            val cornerRadius = CornerRadius(4.dp.toPx())
            rendered.codeRanges.forEach { range ->
                val firstLine = textLayout.getLineForOffset(range.first)
                val lastLine = textLayout.getLineForOffset(range.last)
                for (line in firstLine..lastLine) {
                    val start = maxOf(range.first, textLayout.getLineStart(line))
                    val end = minOf(range.last + 1, textLayout.getLineEnd(line, visibleEnd = true))
                    if (start >= end) continue
                    val startX = textLayout.getHorizontalPosition(start, usePrimaryDirection = true)
                    val endX = textLayout.getHorizontalPosition(end, usePrimaryDirection = true)
                    val baseline = textLayout.getLineBaseline(line)
                    val top = baseline - ascent
                    val bottom = baseline + descent
                    drawRoundRect(
                        color = colors.accentSoft,
                        topLeft = Offset(startX - horizontalPadding, top),
                        size = Size(endX - startX + horizontalPadding * 2, bottom - top),
                        cornerRadius = cornerRadius,
                    )
                }
            }
        },
        style = style,
        color = color,
        onTextLayout = { layout = it },
    )
}
