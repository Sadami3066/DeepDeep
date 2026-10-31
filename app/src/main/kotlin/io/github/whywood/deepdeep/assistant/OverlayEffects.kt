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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.design.deepDeepColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

private const val GLOW_ENTRY_BOOST = 0.8f
private const val GLOW_PULSE_BOOST = 0.9f
private const val GLOW_ALPHA_SWING = 0.24f
private const val GLOW_SCALE_SWING = 0.09f
private const val GLOW_DRIFT_X = 0.03f
private const val GLOW_DRIFT_Y = 0.06f
private const val GLOW_ANGLE_SWING = 6f
private const val GLOW_AMBIENT_DARK = 0.55f
private const val GLOW_AMBIENT_LIGHT = 1.0f
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
private const val HALO_CARD_ALPHA_DARK = 0.90f
private const val HALO_CARD_ALPHA_LIGHT = 0.96f
private const val UPWELL_LAYERS = 3
private const val UPWELL_BASE_Y = 1.12f
private const val UPWELL_STEP_Y = 0.06f
private const val UPWELL_TRAVEL_Y = 0.62f
private const val UPWELL_FADE_POWER = 1.4f
private const val EDGE_ACTIVITY_DARK = 0.07f
private const val EDGE_ACTIVITY_LIGHT = 0.15f
private const val EDGE_REPLY_DARK = 0.08f
private const val EDGE_REPLY_LIGHT = 0.10f
private const val EDGE_START_DARK = 0.10f
private const val EDGE_START_LIGHT = 0.10f
private const val EDGE_ENTRY_DARK = 0.18f
private const val EDGE_ENTRY_LIGHT = 0.28f
private const val STROKE_BASE_DARK = 0.08f
private const val STROKE_BASE_LIGHT = 0.16f
private const val STROKE_REPLY_DARK = 0.08f
private const val STROKE_REPLY_LIGHT = 0.10f
private const val STROKE_START_DARK = 0.10f
private const val STROKE_START_LIGHT = 0.10f
private const val STROKE_ENTRY = 0.18f
private const val HOTSPOT_FROM_Y = 1.06f
private const val HOTSPOT_TO_Y = -0.06f
private const val HOTSPOT_HALF_HEIGHT = 0.13f
private const val HOTSPOT_CORNER_HALF_WIDTH = 0.32f
private const val HOTSPOT_CORNER_HALF_HEIGHT = 0.10f
private const val HOTSPOT_BOTTOM_POWER = 1.6f
private const val HOTSPOT_TOP_POWER = 1.6f
private const val HOTSPOT_PEAK_DARK = 0.55f
private const val HOTSPOT_PEAK_LIGHT = 0.60f
private const val WASH_ALPHA_DARK = 0.18f
private const val WASH_ALPHA_LIGHT = 0.30f
private const val MIN_ALPHA = 0.002f
private val TwoPi = (2.0 * PI).toFloat()
private val HaloBleed = 56.dp
private val HaloShape = RoundedCornerShape(28.dp)
private val EdgeBand = 64.dp
private val ScreenStroke = 1.5.dp
private val ScreenCorner = 28.dp
private val HotspotWidth = 72.dp
private val UpwellX = floatArrayOf(-0.06f, 0.04f, -0.02f)
private val UpwellHalfWidth = floatArrayOf(1.05f, 0.90f, 0.75f)
private val UpwellHalfHeight = floatArrayOf(0.42f, 0.34f, 0.26f)
private val UpwellAlphaDark = floatArrayOf(0.50f, 0.36f, 0.24f)
private val UpwellAlphaLight = floatArrayOf(0.58f, 0.44f, 0.32f)
private val UpwellDark = listOf(Color(0xFF3B82F6), Color(0xFF22D3EE), Color(0xFFA5F3FC))
private val UpwellLight = listOf(Color(0xFF0284C7), Color(0xFF38BDF8), Color(0xFFE0F2FE))
private val WashDark = Color(0xFF1E40AF)
private val WashLight = Color(0xFF7DD3FC)
private val EdgeTopDark = Color(0xFF3B82F6)
private val EdgeTopLight = Color(0xFF0284C7)
private val EdgeBottomDark = Color(0xFF6366F1)
private val EdgeBottomLight = Color(0xFF2563EB)
private val EdgeLeftDark = Color(0xFF22D3EE)
private val EdgeLeftLight = Color(0xFF06B6D4)
private val EdgeRightDark = Color(0xFFA5F3FC)
private val EdgeRightLight = Color(0xFF38BDF8)
private val ScreenStrokeDark = Color(0xFFA5F3FC)
private val ScreenStrokeLight = Color(0xFF2563EB)
private val BaseGlowDark = Color(0xFF3B82F6)
private val BaseGlowLight = Color(0xFF2563EB)
private val HotspotLeftDark = Color(0xFF22D3EE)
private val HotspotLeftLight = Color(0xFF06B6D4)
private val HotspotRightDark = Color(0xFFA5F3FC)
private val HotspotRightLight = Color(0xFF38BDF8)
private val HotspotBottomDark = Color(0xFF3B82F6)
private val HotspotBottomLight = Color(0xFF0284C7)
private val HotspotTopDark = Color(0xFFA5F3FC)
private val HotspotTopLight = Color(0xFFBAE6FD)

private class HotspotBrushes(
    val left: Brush,
    val right: Brush,
    val bottom: Brush,
    val top: Brush,
    val halfWidth: Float,
)

internal fun replyHotspotCenterFraction(sweep: Float): Float =
    HOTSPOT_FROM_Y + (HOTSPOT_TO_Y - HOTSPOT_FROM_Y) * sweep.coerceIn(0f, 1f)

internal fun replyBottomHotspotWeight(sweep: Float): Float =
    (1f - sweep.coerceIn(0f, 1f)).pow(HOTSPOT_BOTTOM_POWER)

internal fun replyTopHotspotWeight(sweep: Float): Float =
    sweep.coerceIn(0f, 1f).pow(HOTSPOT_TOP_POWER)

private class HaloBlob(
    val darkColor: Color,
    val lightColor: Color,
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
        darkColor = Color(0xFF3B82F6),
        lightColor = Color(0xFF0284C7),
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
        darkColor = Color(0xFF22D3EE),
        lightColor = Color(0xFF06B6D4),
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
        darkColor = Color(0xFF6366F1),
        lightColor = Color(0xFF38BDF8),
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
        darkColor = Color(0xFFA5F3FC),
        lightColor = Color(0xFFBAE6FD),
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
                        elevation = if (dark) 12.dp else 20.dp,
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
internal fun OverlayScreenGlow(
    glow: OverlayGlow,
    modifier: Modifier = Modifier,
) {
    val dark = deepDeepColors().dark
    Box(
        modifier.fillMaxSize().drawWithCache {
            if (size.width <= 0f || size.height <= 0f) {
                onDrawBehind { }
            } else {
                val width = size.width
                val height = size.height
                val radius = max(width, height)
                val upwellBrushes = (if (dark) UpwellDark else UpwellLight)
                    .map { color -> glowBrush(color, radius) }
                val washBrush = screenWashBrush(if (dark) WashDark else WashLight, height)
                val band = EdgeBand.toPx()
                val topBrush = topEdgeBrush(if (dark) EdgeTopDark else EdgeTopLight, band)
                val bottomBrush = bottomEdgeBrush(if (dark) EdgeBottomDark else EdgeBottomLight, band, height)
                val leftBrush = leftEdgeBrush(if (dark) EdgeLeftDark else EdgeLeftLight, band)
                val rightBrush = rightEdgeBrush(if (dark) EdgeRightDark else EdgeRightLight, band, width)
                val stroke = Stroke(width = ScreenStroke.toPx())
                val corner = CornerRadius(ScreenCorner.toPx())
                val strokeInset = stroke.width / 2f
                val hotspots = HotspotBrushes(
                    left = glowBrush(if (dark) HotspotLeftDark else HotspotLeftLight, radius),
                    right = glowBrush(if (dark) HotspotRightDark else HotspotRightLight, radius),
                    bottom = glowBrush(if (dark) HotspotBottomDark else HotspotBottomLight, radius),
                    top = glowBrush(if (dark) HotspotTopDark else HotspotTopLight, radius),
                    halfWidth = HotspotWidth.toPx() * 0.5f,
                )
                onDrawBehind {
                    drawScreenGlow(
                        upwellBrushes = upwellBrushes,
                        washBrush = washBrush,
                        radius = radius,
                        band = band,
                        topBrush = topBrush,
                        bottomBrush = bottomBrush,
                        leftBrush = leftBrush,
                        rightBrush = rightBrush,
                        hotspots = hotspots,
                        strokeColor = if (dark) ScreenStrokeDark else ScreenStrokeLight,
                        stroke = stroke,
                        corner = corner,
                        strokeInset = strokeInset,
                        dark = dark,
                        rise = glow.rise.value,
                        entry = glow.entry.value,
                        activity = glow.activity.value,
                        startPulse = glow.startPulse.value,
                        replyPulse = glow.replyPulse.value,
                        replySweep = glow.replySweep.value,
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
                val localBrushes = HaloBlobs.map { blob ->
                    glowBrush(if (dark) blob.darkColor else blob.lightColor, radius)
                }
                val baseBrush = glowBrush(if (dark) BaseGlowDark else BaseGlowLight, radius)
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

private fun DrawScope.drawScreenGlow(
    upwellBrushes: List<Brush>,
    washBrush: Brush,
    radius: Float,
    band: Float,
    topBrush: Brush,
    bottomBrush: Brush,
    leftBrush: Brush,
    rightBrush: Brush,
    hotspots: HotspotBrushes,
    strokeColor: Color,
    stroke: Stroke,
    corner: CornerRadius,
    strokeInset: Float,
    dark: Boolean,
    rise: Float,
    entry: Float,
    activity: Float,
    startPulse: Float,
    replyPulse: Float,
    replySweep: Float,
) {
    val width = size.width
    val height = size.height
    if (width <= 0f || height <= 0f) return

    val fade = (1f - rise).coerceIn(0f, 1f)
    val upwell = fade.pow(UPWELL_FADE_POWER)
    if (upwell > MIN_ALPHA) {
        val wash = fade.pow(1.2f) * (if (dark) WASH_ALPHA_DARK else WASH_ALPHA_LIGHT)
        if (wash > MIN_ALPHA) {
            drawRect(brush = washBrush, alpha = wash)
        }
        val alphas = if (dark) UpwellAlphaDark else UpwellAlphaLight
        for (index in 0 until UPWELL_LAYERS) {
            drawBlob(
                brush = upwellBrushes[index],
                radius = radius,
                centerX = width * (0.5f + UpwellX[index]),
                centerY = height * (UPWELL_BASE_Y + UPWELL_STEP_Y * index - UPWELL_TRAVEL_Y * rise),
                halfWidth = width * UpwellHalfWidth[index],
                halfHeight = height * UpwellHalfHeight[index],
                angle = 0f,
                alpha = upwell * alphas[index],
            )
        }
    }

    val edgeAlpha = (
        activity * (if (dark) EDGE_ACTIVITY_DARK else EDGE_ACTIVITY_LIGHT) +
            replyPulse * (if (dark) EDGE_REPLY_DARK else EDGE_REPLY_LIGHT) +
            startPulse * (if (dark) EDGE_START_DARK else EDGE_START_LIGHT) +
            entry * (if (dark) EDGE_ENTRY_DARK else EDGE_ENTRY_LIGHT)
        ).coerceIn(0f, 1f)
    if (edgeAlpha > MIN_ALPHA) {
        drawRect(brush = topBrush, size = Size(width, band), alpha = edgeAlpha)
        drawRect(
            brush = bottomBrush,
            topLeft = Offset(0f, height - band),
            size = Size(width, band),
            alpha = edgeAlpha,
        )
        drawRect(brush = leftBrush, size = Size(band, height), alpha = edgeAlpha)
        drawRect(
            brush = rightBrush,
            topLeft = Offset(width - band, 0f),
            size = Size(band, height),
            alpha = edgeAlpha,
        )
    }

    if (replyPulse > MIN_ALPHA) {
        val peak = if (dark) HOTSPOT_PEAK_DARK else HOTSPOT_PEAK_LIGHT
        val sideAlpha = replyPulse * peak
        val centerY = height * replyHotspotCenterFraction(replySweep)
        val halfHeight = height * HOTSPOT_HALF_HEIGHT
        drawBlob(hotspots.left, radius, 0f, centerY, hotspots.halfWidth, halfHeight, 0f, sideAlpha)
        drawBlob(hotspots.right, radius, width, centerY, hotspots.halfWidth, halfHeight, 0f, sideAlpha)
        val bottomWeight = replyBottomHotspotWeight(replySweep)
        if (bottomWeight > MIN_ALPHA) {
            drawBlob(
                brush = hotspots.bottom,
                radius = radius,
                centerX = width * 0.5f,
                centerY = height,
                halfWidth = width * HOTSPOT_CORNER_HALF_WIDTH,
                halfHeight = height * HOTSPOT_CORNER_HALF_HEIGHT,
                angle = 0f,
                alpha = sideAlpha * bottomWeight,
            )
        }
        val topWeight = replyTopHotspotWeight(replySweep)
        if (topWeight > MIN_ALPHA) {
            drawBlob(
                brush = hotspots.top,
                radius = radius,
                centerX = width * 0.5f,
                centerY = 0f,
                halfWidth = width * HOTSPOT_CORNER_HALF_WIDTH,
                halfHeight = height * HOTSPOT_CORNER_HALF_HEIGHT,
                angle = 0f,
                alpha = sideAlpha * topWeight,
            )
        }
    }

    val strokeAlpha = (
        activity * (if (dark) STROKE_BASE_DARK else STROKE_BASE_LIGHT) +
            replyPulse * (if (dark) STROKE_REPLY_DARK else STROKE_REPLY_LIGHT) +
            startPulse * (if (dark) STROKE_START_DARK else STROKE_START_LIGHT) +
            entry * STROKE_ENTRY
        ).coerceIn(0f, 1f)
    if (strokeAlpha > MIN_ALPHA) {
        drawRoundRect(
            color = strokeColor,
            topLeft = Offset(strokeInset, strokeInset),
            size = Size(width - strokeInset * 2f, height - strokeInset * 2f),
            cornerRadius = corner,
            style = stroke,
            alpha = strokeAlpha,
        )
    }
}

private fun DrawScope.drawHalo(
    localBrushes: List<Brush>,
    baseBrush: Brush,
    dark: Boolean,
    time: Float,
    activity: Float,
    entry: Float,
    pulse: Float,
) {    val width = size.width
    val height = size.height
    if (width <= 0f || height <= 0f) return
    val ambient = if (dark) GLOW_AMBIENT_DARK else GLOW_AMBIENT_LIGHT
    val level = (ambient + (1f - ambient) * activity) *
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
        alpha = BASE_ALPHA * level * (1f + GLOW_ALPHA_SWING * sin(basePhaseY + 2.1f)),
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
            alpha = blob.alpha * level * (1f + GLOW_ALPHA_SWING * alphaWave),
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
    if (alpha <= MIN_ALPHA || halfWidth <= 0f || halfHeight <= 0f) return
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

private fun screenWashBrush(color: Color, height: Float): Brush = Brush.verticalGradient(
    *arrayOf(
        0f to Color.Transparent,
        0.45f to color.copy(alpha = 0.12f),
        0.78f to color.copy(alpha = 0.55f),
        1f to color,
    ),
    startY = 0f,
    endY = height,
)

private fun topEdgeBrush(color: Color, band: Float): Brush = Brush.verticalGradient(    *arrayOf(0f to color, 1f to Color.Transparent),
    startY = 0f,
    endY = band,
)

private fun bottomEdgeBrush(color: Color, band: Float, height: Float): Brush = Brush.verticalGradient(
    *arrayOf(0f to Color.Transparent, 1f to color),
    startY = height - band,
    endY = height,
)

private fun leftEdgeBrush(color: Color, band: Float): Brush = Brush.horizontalGradient(
    *arrayOf(0f to color, 1f to Color.Transparent),
    startX = 0f,
    endX = band,
)

private fun rightEdgeBrush(color: Color, band: Float, width: Float): Brush = Brush.horizontalGradient(
    *arrayOf(0f to Color.Transparent, 1f to color),
    startX = width - band,
    endX = width,
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
            Color(0x552563EB),
            Color(0x3806B6D4),
            Color(0x553B82F6),
        ),
    )
}

private fun haloShadowAmbient(dark: Boolean): Color =
    if (dark) Color(0xFF071426) else Color(0x332563EB)

private fun haloShadowSpot(dark: Boolean): Color =
    if (dark) Color(0x803B82F6) else Color(0x6638BDF8)

private fun haloSheenTop(dark: Boolean): Color =
    if (dark) Color(0x3322D3EE) else Color(0x292563EB)

private fun haloSheenBottom(dark: Boolean): Color =
    if (dark) Color(0x294F46E5) else Color(0x1F06B6D4)
