package io.github.whywood.deepdeep.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.design.deepDeepColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private const val GLOW_AMBIENT = 0.55f
private const val GLOW_ENTRY_BOOST = 0.8f
private const val GLOW_PULSE_BOOST = 0.9f
private const val GLOW_ALPHA_SWING = 0.24f
private const val GLOW_SCALE_SWING = 0.09f
private const val GLOW_DRIFT_X = 0.03f
private const val GLOW_DRIFT_Y = 0.06f
private const val GLOW_ANGLE_SWING = 6f
private const val PERIOD_X = 4.6f
private const val PERIOD_Y = 5.9f
private const val PERIOD_SCALE = 6.8f
private const val PERIOD_BASE_X = 13.2f
private const val PERIOD_BASE_Y = 9.7f
private const val BASE_CENTER_X = 0.50f
private const val BASE_CENTER_Y = 0.68f
private const val BASE_HALF_WIDTH = 0.46f
private const val BASE_HALF_HEIGHT = 0.30f
private const val BASE_ALPHA = 0.45f
private const val HALO_LIGHT_INTENSITY = 0.70f
private const val HALO_CARD_ALPHA_DARK = 0.90f
private const val HALO_CARD_ALPHA_LIGHT = 0.96f
private const val EDGE_SIDE_PEAK_DARK = 0.20f
private const val EDGE_SIDE_PEAK_LIGHT = 0.14f
private const val EDGE_BOTTOM_PEAK_DARK = 0.22f
private const val EDGE_BOTTOM_PEAK_LIGHT = 0.16f
private const val EDGE_MIN_FLASH = 0.002f
private const val EDGE_SIDE_WIDTH = 0.26f
private const val EDGE_SIDE_HEIGHT = 0.38f
private const val EDGE_BOTTOM_WIDTH = 0.62f
private const val EDGE_BOTTOM_HEIGHT = 0.30f
private val TwoPi = (2.0 * PI).toFloat()
private val HaloBleed = 56.dp
private val HaloShape = RoundedCornerShape(28.dp)
private val BaseGlowColor = Color(0xFF3B82F6)
private val EdgeLeftColor = Color(0xFF3B82F6)
private val EdgeRightColor = Color(0xFF22D3EE)
private val EdgeBottomColor = Color(0xFF6366F1)

private class HaloBlob(
    val color: Color,
    val centerX: Float,
    val centerY: Float,
    val halfWidth: Float,
    val halfHeight: Float,
    val angle: Float,
    val alpha: Float,
    val phase: Float,
    val periodX: Float,
    val periodY: Float,
    val periodWidth: Float,
    val periodHeight: Float,
    val periodAngle: Float,
    val periodAlpha: Float,
)

private val HaloBlobs = listOf(
    HaloBlob(
        color = Color(0xFF3B82F6),
        centerX = 0.50f,
        centerY = 0.50f,
        halfWidth = 0.46f,
        halfHeight = 0.42f,
        angle = -4f,
        alpha = 0.55f,
        phase = 0f,
        periodX = PERIOD_X,
        periodY = PERIOD_Y,
        periodWidth = PERIOD_SCALE,
        periodHeight = PERIOD_Y,
        periodAngle = PERIOD_X,
        periodAlpha = PERIOD_SCALE,
    ),
    HaloBlob(
        color = Color(0xFF22D3EE),
        centerX = 0.60f,
        centerY = 0.44f,
        halfWidth = 0.36f,
        halfHeight = 0.36f,
        angle = 12f,
        alpha = 0.50f,
        phase = 0.31f,
        periodX = PERIOD_Y,
        periodY = PERIOD_SCALE,
        periodWidth = PERIOD_X,
        periodHeight = PERIOD_SCALE,
        periodAngle = PERIOD_Y,
        periodAlpha = PERIOD_X,
    ),
    HaloBlob(
        color = Color(0xFF6366F1),
        centerX = 0.40f,
        centerY = 0.60f,
        halfWidth = 0.42f,
        halfHeight = 0.36f,
        angle = -8f,
        alpha = 0.42f,
        phase = 0.63f,
        periodX = PERIOD_SCALE,
        periodY = PERIOD_X,
        periodWidth = PERIOD_Y,
        periodHeight = PERIOD_X,
        periodAngle = PERIOD_SCALE,
        periodAlpha = PERIOD_Y,
    ),
    HaloBlob(
        color = Color(0xFFA5F3FC),
        centerX = 0.34f,
        centerY = 0.50f,
        halfWidth = 0.30f,
        halfHeight = 0.32f,
        angle = 26f,
        alpha = 0.55f,
        phase = 0.47f,
        periodX = PERIOD_Y,
        periodY = PERIOD_X,
        periodWidth = PERIOD_SCALE,
        periodHeight = PERIOD_Y,
        periodAngle = PERIOD_X,
        periodAlpha = PERIOD_Y,
    ),
)

@Composable
internal fun OverlayComposerCard(
    glow: OverlayGlow,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = deepDeepColors()
    val dark = colors.dark
    val borderBrush = remember(dark) { haloBorderBrush(dark) }
    val cardColor = colors.composer.copy(
        alpha = if (dark) HALO_CARD_ALPHA_DARK else HALO_CARD_ALPHA_LIGHT,
    )

    Layout(
        modifier = modifier,
        content = {
            HaloLayer(dark = dark, glow = glow)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 12.dp,
                        shape = HaloShape,
                        clip = false,
                        ambientColor = haloShadowAmbient(dark),
                        spotColor = haloShadowSpot(dark),
                    )
                    .clip(HaloShape)
                    .background(cardColor)
                    .drawBehind {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    haloSheenTop(dark),
                                    Color.Transparent,
                                    haloSheenBottom(dark),
                                ),
                            ),
                        )
                    }
                    .border(1.dp, borderBrush, HaloShape),
                content = content,
            )
        },
    ) { measurables, constraints ->
        val card = measurables[1].measure(constraints)
        val bleed = HaloBleed.roundToPx()
        val halo = measurables[0].measure(
            Constraints.fixed(card.width + bleed * 2, card.height + bleed * 2),
        )
        layout(card.width, card.height) {
            halo.placeRelative(-bleed, -bleed)
            card.placeRelative(0, 0)
        }
    }
}



@Composable
internal fun OverlayEdgeGlow(
    glow: OverlayGlow,
    modifier: Modifier = Modifier,
) {
    val dark = deepDeepColors().dark
    Box(
        modifier.fillMaxSize().drawWithCache {
            if (size.width <= 0f || size.height <= 0f) {
                onDrawBehind { }
            } else {
                val radius = max(size.width, size.height)
                val leftBrush = glowBrush(EdgeLeftColor, radius)
                val rightBrush = glowBrush(EdgeRightColor, radius)
                val bottomBrush = glowBrush(EdgeBottomColor, radius)
                onDrawBehind {
                    val entry = glow.entry.value
                    val pulse = glow.startPulse.value
                    val flash = max(entry, pulse)
                    if (flash <= EDGE_MIN_FLASH) return@onDrawBehind
                    val width = size.width
                    val height = size.height
                    val sideAlpha = (if (dark) EDGE_SIDE_PEAK_DARK else EDGE_SIDE_PEAK_LIGHT) * flash
                    val sideWidth = width * EDGE_SIDE_WIDTH
                    val sideHeight = height * EDGE_SIDE_HEIGHT
                    drawBlob(leftBrush, radius, 0f, height * 0.30f, sideWidth, sideHeight, 0f, sideAlpha)
                    drawBlob(rightBrush, radius, width, height * 0.30f, sideWidth, sideHeight, 0f, sideAlpha)
                    val bottomAlpha = (if (dark) EDGE_BOTTOM_PEAK_DARK else EDGE_BOTTOM_PEAK_LIGHT) *
                        max(entry, pulse * 0.85f)
                    drawBlob(
                        brush = bottomBrush,
                        radius = radius,
                        centerX = width * 0.5f,
                        centerY = height * 0.94f,
                        halfWidth = width * EDGE_BOTTOM_WIDTH,
                        halfHeight = height * 0.34f,
                        angle = 0f,
                        alpha = bottomAlpha,
                    )
                }
            }
        },
    )
}

@Composable
private fun HaloLayer(
    dark: Boolean,
    glow: OverlayGlow,
) {
    Box(
        Modifier.drawWithCache {
            if (size.width <= 0f || size.height <= 0f) {
                onDrawBehind { }
            } else {
                val radius = max(size.width, size.height)
                val localBrushes = HaloBlobs.map { blob -> glowBrush(blob.color, radius) }
                val baseBrush = glowBrush(BaseGlowColor, radius)
                onDrawBehind {
                    drawHalo(
                        localBrushes = localBrushes,
                        baseBrush = baseBrush,
                        dark = dark,
                        time = glow.time.value,
                        activity = glow.activity.value,
                        entry = glow.entry.value,
                        pulse = glow.startPulse.value,
                    )
                }
            }
        },
    )
}

private fun DrawScope.drawHalo(
    localBrushes: List<Brush>,
    baseBrush: Brush,
    dark: Boolean,
    time: Float,
    activity: Float,
    entry: Float,
    pulse: Float,
) {
    val width = size.width
    val height = size.height
    if (width <= 0f || height <= 0f) return
    val intensity = if (dark) 1f else HALO_LIGHT_INTENSITY
    val level = (GLOW_AMBIENT + (1f - GLOW_AMBIENT) * activity) *
        (1f + GLOW_ENTRY_BOOST * entry) *
        (1f + GLOW_PULSE_BOOST * pulse)
    val radius = max(width, height)

    val basePhaseX = time * TwoPi / PERIOD_BASE_X
    val basePhaseY = time * TwoPi / PERIOD_BASE_Y
    drawBlob(
        brush = baseBrush,
        radius = radius,
        centerX = width * BASE_CENTER_X + sin(basePhaseX) * width * GLOW_DRIFT_X,
        centerY = height * BASE_CENTER_Y + cos(basePhaseY) * height * GLOW_DRIFT_Y,
        halfWidth = width * BASE_HALF_WIDTH *
            (1f + GLOW_SCALE_SWING * sin(basePhaseY + 0.9f)) * (1f - 0.04f * pulse),
        halfHeight = height * BASE_HALF_HEIGHT *
            (1f + GLOW_SCALE_SWING * cos(basePhaseX + 1.5f)) * (1f - 0.04f * pulse),
        angle = 0f,
        alpha = BASE_ALPHA * intensity * level * (1f + GLOW_ALPHA_SWING * sin(basePhaseY + 2.1f)),
    )

    HaloBlobs.forEachIndexed { index, blob ->
        val phase = blob.phase * TwoPi
        val alphaWave = sin(time * TwoPi / blob.periodAlpha + phase + 3.1f)
        drawBlob(
            brush = localBrushes[index],
            radius = radius,
            centerX = width * blob.centerX +
                sin(time * TwoPi / blob.periodX + phase) * width * GLOW_DRIFT_X,
            centerY = height * blob.centerY +
                cos(time * TwoPi / blob.periodY + phase + 1.3f + index) * height * GLOW_DRIFT_Y,
            halfWidth = width * blob.halfWidth *
                (1f + GLOW_SCALE_SWING * sin(time * TwoPi / blob.periodWidth + phase + 0.6f)) *
                (1f - 0.04f * pulse),
            halfHeight = height * blob.halfHeight *
                (1f + GLOW_SCALE_SWING * cos(time * TwoPi / blob.periodHeight + phase + 1.9f)) *
                (1f - 0.04f * pulse),
            angle = blob.angle +
                GLOW_ANGLE_SWING * sin(time * TwoPi / blob.periodAngle + phase + 2.4f),
            alpha = blob.alpha * intensity * level * (1f + GLOW_ALPHA_SWING * alphaWave),
        )
    }
}

private fun DrawScope.drawBlob(
    brush: Brush,
    radius: Float,
    centerX: Float,
    centerY: Float,
    halfWidth: Float,
    halfHeight: Float,
    angle: Float,
    alpha: Float,
) {
    if (alpha <= 0.001f || halfWidth <= 0f || halfHeight <= 0f) return
    withTransform({
        translate(centerX, centerY)
        rotate(angle, Offset.Zero)
        scale(halfWidth / radius, halfHeight / radius, Offset.Zero)
    }) {
        drawCircle(
            brush = brush,
            radius = radius,
            center = Offset.Zero,
            alpha = alpha.coerceIn(0f, 1f),
        )
    }
}

private fun glowBrush(color: Color, radius: Float): Brush = Brush.radialGradient(
    *arrayOf(
        0f to color,
        0.34f to color.copy(alpha = 0.58f),
        0.62f to color.copy(alpha = 0.22f),
        0.85f to color.copy(alpha = 0.05f),
        1f to Color.Transparent,
    ),
    center = Offset.Zero,
    radius = radius,
)



private fun haloBorderBrush(dark: Boolean): Brush = if (dark) {
    Brush.linearGradient(
        colors = listOf(
            Color(0x99A5F3FC),
            Color(0x667DE9F5),
            Color(0x993B82F6),
        ),
    )
} else {
    Brush.linearGradient(
        colors = listOf(
            Color(0x332563EB),
            Color(0x1A06B6D4),
            Color(0x332563EB),
        ),
    )
}

private fun haloShadowAmbient(dark: Boolean): Color =
    if (dark) Color(0xFF071426) else Color(0xFF0B1220)

private fun haloShadowSpot(dark: Boolean): Color =
    if (dark) Color(0x803B82F6) else Color(0x382563EB)

private fun haloSheenTop(dark: Boolean): Color =
    if (dark) Color(0x3322D3EE) else Color(0x142563EB)

private fun haloSheenBottom(dark: Boolean): Color =
    if (dark) Color(0x294F46E5) else Color(0x0F06B6D4)
