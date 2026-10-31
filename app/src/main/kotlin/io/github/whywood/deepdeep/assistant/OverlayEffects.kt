package io.github.whywood.deepdeep.assistant

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
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
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.deepDeepColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private const val HALO_DRIFT_MS = 18000
private const val HALO_SWAY_MS = 16000
private const val HALO_PULSE_MS = 13000
private const val HALO_BLUR_DP = 28
private const val HALO_ALPHA_FLOOR = 0.78f
private const val HALO_ALPHA_SWING = 0.22f
private const val HALO_SWAY_ALPHA = 0.1f
private const val HALO_SWAY_ANGLE = 9f
private const val HALO_SWAY_SCALE = 0.06f
private const val HALO_DRIFT_X = 0.02f
private const val HALO_DRIFT_Y = 0.045f
private const val HALO_LIGHT_INTENSITY = 0.65f
private const val HALO_CARD_ALPHA_DARK = 0.90f
private const val HALO_CARD_ALPHA_LIGHT = 0.96f
private val HaloFullTurn = (2.0 * PI).toFloat()
private val HaloBleed = 56.dp
private val HaloShape = RoundedCornerShape(28.dp)

private class HaloOrb(
    val color: Color,
    val centerX: Float,
    val centerY: Float,
    val halfWidth: Float,
    val halfHeight: Float,
    val angle: Float,
    val alpha: Float,
    val phase: Float,
)

private val HaloOrbs = listOf(
    HaloOrb(Color(0xFF3B82F6), 0.50f, 0.50f, 0.62f, 0.60f, -4f, 0.55f, 0f),
    HaloOrb(Color(0xFF22D3EE), 0.60f, 0.44f, 0.56f, 0.50f, 12f, 0.50f, 0.31f),
    HaloOrb(Color(0xFF6366F1), 0.40f, 0.60f, 0.54f, 0.52f, -8f, 0.42f, 0.63f),
    HaloOrb(Color(0xFFA5F3FC), 0.34f, 0.50f, 0.38f, 0.42f, 26f, 0.55f, 0.47f),
)

@Composable
internal fun OverlayComposerCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = deepDeepColors()
    val dark = colors.dark
    val transition = rememberInfiniteTransition(label = "overlay-halo")
    val drift = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(HALO_DRIFT_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "overlay-halo-drift",
    )
    val sway = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(HALO_SWAY_MS, easing = DdMotion.EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "overlay-halo-sway",
    )
    val pulse = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(HALO_PULSE_MS, easing = DdMotion.EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "overlay-halo-pulse",
    )
    val borderBrush = remember(dark) { haloBorderBrush(dark) }
    val cardColor = colors.composer.copy(
        alpha = if (dark) HALO_CARD_ALPHA_DARK else HALO_CARD_ALPHA_LIGHT,
    )

    Layout(
        modifier = modifier,
        content = {
            HaloLayer(dark = dark, drift = drift, sway = sway, pulse = pulse)
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
private fun HaloLayer(
    dark: Boolean,
    drift: State<Float>,
    sway: State<Float>,
    pulse: State<Float>,
) {
    Box(
        Modifier
            .then(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Modifier.blur(HALO_BLUR_DP.dp, BlurredEdgeTreatment.Unbounded)
                } else {
                    Modifier
                },
            )
            .drawWithCache {
                val radius = max(size.width, size.height)
                val brushes = HaloOrbs.map { orb ->
                    Brush.radialGradient(
                        colors = listOf(orb.color, orb.color.copy(alpha = 0f)),
                        center = Offset.Zero,
                        radius = radius,
                    )
                }
                onDrawBehind {
                    drawHalo(
                        brushes = brushes,
                        dark = dark,
                        drift = drift.value,
                        sway = sway.value,
                        pulse = pulse.value,
                    )
                }
            },
    )
}

private fun DrawScope.drawHalo(
    brushes: List<Brush>,
    dark: Boolean,
    drift: Float,
    sway: Float,
    pulse: Float,
) {
    val width = size.width
    val height = size.height
    if (width <= 0f || height <= 0f) return
    val intensity = if (dark) 1f else HALO_LIGHT_INTENSITY
    val breath = HALO_ALPHA_FLOOR + HALO_ALPHA_SWING * pulse
    val radius = max(width, height)
    HaloOrbs.forEachIndexed { index, orb ->
        val phase = drift * HaloFullTurn + orb.phase * HaloFullTurn
        val centerX = width * orb.centerX + sin(phase) * width * HALO_DRIFT_X
        val centerY = height * orb.centerY + cos(phase * 0.8f + index) * height * HALO_DRIFT_Y
        val angle = orb.angle + (sway - 0.5f) * HALO_SWAY_ANGLE
        val widthScale = 1f - HALO_SWAY_SCALE + HALO_SWAY_SCALE * sway
        val heightScale = 1f - HALO_SWAY_SCALE + HALO_SWAY_SCALE * pulse
        val halfWidth = width * orb.halfWidth * widthScale
        val halfHeight = height * orb.halfHeight * heightScale
        val alpha = (
            orb.alpha * intensity * breath *
                (1f - HALO_SWAY_ALPHA + HALO_SWAY_ALPHA * sway)
            ).coerceIn(0f, 1f)
        withTransform({
            translate(centerX, centerY)
            rotate(angle, Offset.Zero)
            scale(halfWidth / radius, halfHeight / radius, Offset.Zero)
        }) {
            drawCircle(
                brush = brushes[index],
                radius = radius,
                center = Offset.Zero,
                alpha = alpha,
            )
        }
    }
}

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
