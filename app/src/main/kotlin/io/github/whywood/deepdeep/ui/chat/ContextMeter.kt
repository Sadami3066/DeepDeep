package io.github.whywood.deepdeep.ui.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import kotlin.math.roundToInt

@Composable
fun ContextMeter(
    usedTokens: Long,
    windowTokens: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showPercent: Boolean = true,
) {
    if (windowTokens <= 0L) return
    val colors = deepDeepColors()
    val fraction = (usedTokens.toDouble() / windowTokens.toDouble()).coerceIn(0.0, 1.0).toFloat()
    val percent = (fraction * 100f).roundToInt()
    val trackColor = colors.ink.copy(alpha = 0.12f)
    val progressColor = colors.accent
    val description = stringResource(R.string.usage_meter_description, percent)
    DdPressable(
        onClick = onClick,
        modifier = modifier
            .sizeIn(minWidth = 31.dp, minHeight = 31.dp)
            .testTag("context_meter"),
        shape = RoundedCornerShape(8.dp),
        contentDescription = description,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(Modifier.size(17.dp)) {
                val strokeWidth = 2.5.dp.toPx()
                val inset = strokeWidth / 2f
                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokeWidth),
                )
                if (fraction > 0f) {
                    drawArc(
                        color = progressColor,
                        startAngle = -90f,
                        sweepAngle = 360f * fraction,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                }
            }
            if (showPercent) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.usage_meter_percent, percent),
                    style = DdType.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                    color = colors.inkSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}
