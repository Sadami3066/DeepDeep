package io.github.whywood.deepdeep.ui.voice

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.DdTypingDots
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.voice.SpeechError

private val LEVEL_FACTORS = listOf(0.34f, 0.6f, 0.86f, 1f, 0.78f, 0.52f, 0.3f)

@Composable
internal fun VoiceIdleBody(onStart: () -> Unit) {
    val colors = deepDeepColors()
    DdPressable(
        onClick = onStart,
        modifier = Modifier.size(88.dp).testTag("voice_mic"),
        shape = CircleShape,
        background = colors.accentSoft,
        contentDescription = stringResource(R.string.voice_start),
    ) {
        DdIcon(icon = DdIcons.Mic, tint = colors.accent, size = 32.dp)
    }
    Spacer(Modifier.height(22.dp))
    Text(
        text = stringResource(R.string.voice_hint),
        style = DdType.body,
        color = colors.inkSecondary,
        textAlign = TextAlign.Center,
    )
}

@Composable
internal fun VoiceListeningBody(level: Float, transcript: String) {
    val colors = deepDeepColors()
    Box(
        Modifier
            .size(88.dp)
            .clip(CircleShape)
            .background(colors.accentSoft),
        contentAlignment = Alignment.Center,
    ) {
        DdIcon(icon = DdIcons.AudioLines, tint = colors.accent, size = 32.dp)
    }
    Spacer(Modifier.height(20.dp))
    Text(
        text = transcript.ifEmpty { stringResource(R.string.voice_listening) },
        modifier = Modifier.heightIn(min = 52.dp).testTag("voice_transcript"),
        style = DdType.body,
        color = if (transcript.isEmpty()) colors.inkTertiary else colors.ink,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(18.dp))
    VoiceLevelBars(level = level)
}

@Composable
internal fun VoiceAnswerBody(
    answer: String,
    speaking: Boolean,
    onStopSpeaking: () -> Unit,
) {
    val colors = deepDeepColors()
    val scroll = rememberScrollState()
    LaunchedEffect(answer) {
        scroll.scrollTo(scroll.maxValue)
    }
    if (answer.isBlank()) {
        DdTypingDots(color = colors.inkTertiary)
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.voice_thinking),
            style = DdType.bodySmall,
            color = colors.inkTertiary,
        )
    } else {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp)
                .verticalScroll(scroll),
        ) {
            Text(
                text = answer,
                modifier = Modifier.testTag("voice_answer"),
                style = DdType.body,
                color = colors.ink,
            )
        }
    }
    if (speaking) {
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            DdIcon(icon = DdIcons.AudioLines, tint = colors.accent, size = 15.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.voice_speaking),
                style = DdType.bodySmall,
                color = colors.inkSecondary,
            )
            Spacer(Modifier.width(14.dp))
            DdTextAction(
                text = stringResource(R.string.voice_stop_speaking),
                onClick = onStopSpeaking,
                modifier = Modifier.testTag("voice_stop_speaking"),
            )
        }
    }
}

@Composable
internal fun VoiceErrorBody(failure: VoiceFailure) {
    val colors = deepDeepColors()
    Text(
        text = speechErrorMessage(failure.error),
        modifier = Modifier.testTag("voice_error"),
        style = DdType.body,
        color = colors.inkSecondary,
        textAlign = TextAlign.Center,
    )
    if (failure.permissionPermanentlyDenied) {
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.voice_permission_settings_hint),
            style = DdType.bodySmall,
            color = colors.inkTertiary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun VoiceLevelBars(level: Float) {
    val colors = deepDeepColors()
    Row(
        modifier = Modifier.height(30.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LEVEL_FACTORS.forEachIndexed { index, factor ->
            val barHeight by animateDpAsState(
                targetValue = (3f + 23f * level * factor).dp,
                animationSpec = tween(DdMotion.FAST, easing = DdMotion.EaseOut),
                label = "voice-level-$index",
            )
            Box(
                Modifier
                    .width(3.dp)
                    .height(barHeight)
                    .clip(CircleShape)
                    .background(colors.ink.copy(alpha = 0.2f + 0.55f * level)),
            )
        }
    }
}

@Composable
@ReadOnlyComposable
internal fun speechErrorMessage(error: SpeechError): String = when (error) {
    SpeechError.NO_MATCH -> stringResource(R.string.voice_error_no_match)
    SpeechError.SPEECH_TIMEOUT -> stringResource(R.string.voice_error_timeout)
    SpeechError.INSUFFICIENT_PERMISSIONS -> stringResource(R.string.voice_error_permission)
    SpeechError.NETWORK, SpeechError.NETWORK_TIMEOUT -> stringResource(R.string.voice_error_network)
    SpeechError.RECOGNIZER_BUSY -> stringResource(R.string.voice_error_busy)
    SpeechError.LANGUAGE_NOT_SUPPORTED, SpeechError.LANGUAGE_UNAVAILABLE ->
        stringResource(R.string.voice_error_language)

    SpeechError.UNAVAILABLE -> stringResource(R.string.voice_error_unavailable)
    else -> stringResource(R.string.voice_error_generic)
}
