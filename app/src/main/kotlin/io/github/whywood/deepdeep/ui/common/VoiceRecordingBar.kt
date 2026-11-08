package io.github.whywood.deepdeep.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdAlpha
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.deepDeepColors
import kotlin.math.pow
import kotlinx.coroutines.isActive

internal const val VOICE_WAVE_SAMPLE_INTERVAL_MS = 80L
internal const val VOICE_WAVE_MAX_FRAME_MS = 200L

private const val WAVE_EDGE_STEPS = 2f
private const val WAVE_LEVEL_GAMMA = 0.65f
private const val WAVE_CAPACITY = 240
private const val WAVE_FADE_DURATION_MS = 700

private val WaveStep = 4.6.dp
private val WaveDotSize = 2.8.dp
private val WaveMaxHeight = 22.dp

internal fun voiceWaveLevel(volume: Float): Float {
    val normalized = ((volume - VOICE_MIN_VOLUME) / (1f - VOICE_MIN_VOLUME)).coerceIn(0f, 1f)
    return normalized.pow(WAVE_LEVEL_GAMMA)
}

internal fun voiceWaveOffset(index: Int, fraction: Float): Float = index + fraction

internal class VoiceWaveHistory(
    capacity: Int,
    private val intervalMs: Long = VOICE_WAVE_SAMPLE_INTERVAL_MS,
) {
    private val capacity = capacity.coerceAtLeast(1)
    private val samples = ArrayDeque<Float>(this.capacity)
    private var elapsedMs = 0L

    init {
        reset()
    }

    val fraction: Float
        get() = if (intervalMs <= 0L) {
            0f
        } else {
            (elapsedMs.toFloat() / intervalMs).coerceIn(0f, 1f)
        }

    fun reset() {
        samples.clear()
        repeat(capacity) { samples.addLast(0f) }
        elapsedMs = 0L
    }

    fun advance(deltaMs: Long, volume: Float): Boolean {
        if (deltaMs <= 0L) return false
        elapsedMs += deltaMs
        val interval = intervalMs.coerceAtLeast(1L)
        val level = volume.coerceIn(0f, 1f)
        var changed = false
        while (elapsedMs >= interval) {
            elapsedMs -= interval
            samples.addFirst(level)
            if (samples.size > capacity) samples.removeLast()
            changed = true
        }
        return changed
    }

    fun snapshot(): List<Float> = samples.toList()
}

@Composable
internal fun VoiceRecordingBar(
    volume: Float,
    stopping: Boolean,
    canSend: Boolean,
    onCancel: () -> Unit,
    onStop: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .shadow(
                elevation = 8.dp,
                shape = shape,
                clip = false,
                ambientColor = Color(0xFF0B1220),
                spotColor = Color(0x3D0B1220),
            )
            .clip(shape)
            .background(colors.composer)
            .then(
                if (colors.paper.luminance() < 0.5f) {
                    Modifier.border(1.dp, colors.ink.copy(alpha = 0.08f), shape)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 7.dp)
            .testTag("composer_voice_bar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DdPressable(
            onClick = onCancel,
            modifier = Modifier.size(38.dp).testTag("composer_voice_cancel"),
            background = colors.wash,
            contentDescription = stringResource(R.string.voice_bar_cancel),
        ) {
            DdIcon(icon = DdIcons.Close, tint = colors.inkSecondary, size = 16.dp)
        }
        VoiceWaveform(
            volume = volume,
            stopping = stopping,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 10.dp),
        )
        DdPressable(
            onClick = onStop,
            modifier = Modifier.size(38.dp).testTag("composer_voice_stop"),
            background = colors.wash,
            contentDescription = stringResource(R.string.voice_bar_stop),
        ) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors.ink),
            )
        }
        Spacer(Modifier.width(6.dp))
        DdPressable(
            onClick = onSend,
            modifier = Modifier.size(38.dp).testTag("composer_voice_send"),
            background = if (canSend) colors.accent else colors.wash,
            enabled = canSend,
            contentDescription = stringResource(R.string.voice_bar_send),
        ) {
            DdIcon(
                icon = DdIcons.ArrowUp,
                tint = if (canSend) colors.onAccent else colors.inkTertiary,
                size = 18.dp,
            )
        }
    }
}

@Composable
private fun VoiceWaveform(
    volume: Float,
    stopping: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val density = LocalDensity.current
    val stepPx = with(density) { WaveStep.toPx() }
    val dotPx = with(density) { WaveDotSize.toPx() }
    val maxHeightPx = with(density) { WaveMaxHeight.toPx() }
    val history = remember { VoiceWaveHistory(WAVE_CAPACITY) }
    val samples = remember { mutableStateOf(history.snapshot()) }
    val scrollFraction = remember { mutableFloatStateOf(0f) }
    val currentVolume by rememberUpdatedState(volume)
    val fade = animateFloatAsState(
        targetValue = if (stopping) 0f else 1f,
        animationSpec = tween(WAVE_FADE_DURATION_MS, easing = DdMotion.EaseOut),
        label = "voice-wave-fade",
    )
    LaunchedEffect(history) {
        var lastFrame = 0L
        while (isActive) {
            withFrameNanos { frameTime ->
                if (lastFrame != 0L) {
                    val deltaMs = ((frameTime - lastFrame) / 1_000_000L)
                        .coerceIn(0L, VOICE_WAVE_MAX_FRAME_MS)
                    if (history.advance(deltaMs, currentVolume)) {
                        samples.value = history.snapshot()
                    }
                    scrollFraction.floatValue = history.fraction
                }
                lastFrame = frameTime
            }
        }
    }
    val faintColor = colors.inkTertiary.copy(alpha = DdAlpha.SpotFocused)
    val loudColor = colors.ink
    Canvas(modifier) {
        val fraction = scrollFraction.floatValue
        val levelFade = fade.value
        val data = samples.value
        for (index in data.indices) {
            val x = voiceWaveOffset(index, fraction) * stepPx
            if (x >= size.width) break
            val edgeAlpha = (x / (stepPx * WAVE_EDGE_STEPS)).coerceIn(0f, 1f) *
                ((size.width - x) / (stepPx * WAVE_EDGE_STEPS)).coerceIn(0f, 1f)
            val level = voiceWaveLevel(data[index]) * levelFade
            val height = dotPx + (maxHeightPx - dotPx) * level
            val base = lerp(faintColor, loudColor, level)
            drawRoundRect(
                color = base.copy(alpha = base.alpha * edgeAlpha),
                topLeft = Offset(x, (size.height - height) / 2f),
                size = Size(dotPx, height),
                cornerRadius = CornerRadius(dotPx / 2f, dotPx / 2f),
            )
        }
    }
}
