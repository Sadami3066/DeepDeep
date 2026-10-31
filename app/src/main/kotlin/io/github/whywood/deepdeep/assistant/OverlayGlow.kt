package io.github.whywood.deepdeep.assistant

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.MotionDurationScale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.AssistantStep
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.runtime.PendingQuestion
import kotlinx.coroutines.delay

internal enum class OverlayRunPhase { Idle, Thinking, Streaming, Waiting, Done, Stopped, Failed }

internal data class OverlayRunSignal(
    val loaded: Boolean = false,
    val sessionId: String? = null,
    val active: Boolean = false,
    val phase: OverlayRunPhase = OverlayRunPhase.Idle,
    val messageId: Long? = null,
    val runId: String = "",
)

internal fun overlayRunSignal(
    state: AssistantOverlayState,
    pendingQuestions: List<PendingQuestion>,
): OverlayRunSignal {
    val conversation = state.conversation
    val sessionId = state.sessionId
    if (!conversation.loaded) {
        return OverlayRunSignal(sessionId = sessionId)
    }
    val lastAssistant = conversation.messages.lastOrNull { it.role == AssistantRole.ASSISTANT }
    val activeMessage = conversation.messages.lastOrNull {
        it.role == AssistantRole.ASSISTANT && (it.streaming || it.status == MessageStatus.RUNNING)
    }
    val waiting = sessionId != null && pendingQuestions.any { it.sessionId == sessionId }
    val active = waiting || conversation.sending || activeMessage != null
    val phase = when {
        waiting -> OverlayRunPhase.Waiting
        activeMessage != null && activeMessage.hasGeneratedText() -> OverlayRunPhase.Streaming
        active -> OverlayRunPhase.Thinking
        else -> lastAssistant.idlePhase()
    }
    return OverlayRunSignal(
        loaded = true,
        sessionId = sessionId,
        active = active,
        phase = phase,
        messageId = activeMessage?.id,
        runId = activeMessage?.runId.orEmpty(),
    )
}

private fun AssistantMessage.hasGeneratedText(): Boolean =
    steps.any { step -> step is AssistantStep.Text && step.text.isNotBlank() }

private fun AssistantMessage?.idlePhase(): OverlayRunPhase = when (this?.status) {
    MessageStatus.DONE -> OverlayRunPhase.Done
    MessageStatus.STOPPED -> OverlayRunPhase.Stopped
    MessageStatus.FAILED -> OverlayRunPhase.Failed
    else -> OverlayRunPhase.Idle
}

internal class OverlayStartTracker {
    private var seenLoad = false
    private var sessionId: String? = null
    private var messageId: Long? = null
    private var runId: String = ""
    private var active = false

    fun update(signal: OverlayRunSignal): Boolean {
        val currentSession = signal.sessionId
        if (!signal.loaded || currentSession == null) return false
        if (!seenLoad) {
            seenLoad = true
            record(signal)
            return false
        }
        if (currentSession != sessionId) {
            record(signal)
            return false
        }
        val started = signal.active && (
            !active ||
                (signal.messageId != null && messageId != null && signal.messageId != messageId) ||
                (signal.runId.isNotBlank() && runId.isNotBlank() && signal.runId != runId)
            )
        record(signal)
        return started
    }

    private fun record(signal: OverlayRunSignal) {
        sessionId = signal.sessionId
        messageId = signal.messageId
        runId = signal.runId
        active = signal.active
    }
}

@Stable
internal class OverlayGlow internal constructor(
    val phase: OverlayRunPhase,
    val time: State<Float>,
    val entry: Animatable<Float, AnimationVector1D>,
    val activity: Animatable<Float, AnimationVector1D>,
    val startPulse: Animatable<Float, AnimationVector1D>,
) {
    val active: Boolean
        get() = phase == OverlayRunPhase.Thinking ||
            phase == OverlayRunPhase.Streaming ||
            phase == OverlayRunPhase.Waiting
}

private const val GLOW_ENTRY_IN_MS = 260
private const val GLOW_ENTRY_OUT_MS = 560
private const val GLOW_PULSE_IN_MS = 180
private const val GLOW_PULSE_OUT_MS = 500
private const val GLOW_ACTIVITY_MS = 320
private const val GLOW_DONE_MS = 900
private const val GLOW_STOP_MS = 480
private const val FRAME_STEP_NANOS = 33_000_000L
private const val MAX_FRAME_STEP_NANOS = 100_000_000L
private const val NANOS_PER_SECOND = 1_000_000_000f

internal fun OverlayRunPhase.glowActivityTarget(): Float = when (this) {
    OverlayRunPhase.Thinking -> 0.55f
    OverlayRunPhase.Streaming -> 0.70f
    OverlayRunPhase.Waiting -> 0.20f
    OverlayRunPhase.Idle, OverlayRunPhase.Done, OverlayRunPhase.Stopped, OverlayRunPhase.Failed -> 0f
}

internal fun OverlayRunPhase.glowActivityDurationMs(): Int = when (this) {
    OverlayRunPhase.Done -> GLOW_DONE_MS
    OverlayRunPhase.Stopped, OverlayRunPhase.Failed -> GLOW_STOP_MS
    else -> GLOW_ACTIVITY_MS
}

@Composable
internal fun rememberOverlayGlow(
    signal: OverlayRunSignal,
    entryPlayed: Boolean = true,
    entryStart: Boolean = true,
): OverlayGlow {
    val time = rememberOverlayFrameTime()
    val entry = remember { Animatable(0f) }
    val activity = remember { Animatable(0f) }
    val startPulse = remember { Animatable(0f) }
    val tracker = remember { OverlayStartTracker() }
    val playsEntry = remember { !entryPlayed }
    var entryStarted by remember { mutableStateOf(false) }
    var pulseToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(signal) {
        if (tracker.update(signal)) pulseToken += 1
    }
    LaunchedEffect(playsEntry, entryStart) {
        if (entryStarted || !playsEntry || !entryStart) return@LaunchedEffect
        entryStarted = true
        delay(180)
        entry.animateTo(1f, tween(GLOW_ENTRY_IN_MS, easing = DdMotion.EaseOut))
        entry.animateTo(0f, tween(GLOW_ENTRY_OUT_MS, easing = DdMotion.EaseInOut))
    }
    LaunchedEffect(signal.phase) {
        val target = signal.phase.glowActivityTarget()
        activity.animateTo(
            targetValue = target,
            animationSpec = tween(
                durationMillis = signal.phase.glowActivityDurationMs(),
                easing = DdMotion.EaseInOut,
            ),
        )
    }
    LaunchedEffect(pulseToken) {
        if (pulseToken == 0) return@LaunchedEffect
        startPulse.snapTo(0f)
        startPulse.animateTo(1f, tween(GLOW_PULSE_IN_MS, easing = DdMotion.EaseOut))
        startPulse.animateTo(0f, tween(GLOW_PULSE_OUT_MS, easing = DdMotion.EaseInOut))
    }

    return remember(time, entry, activity, startPulse, signal.phase) {
        OverlayGlow(
            phase = signal.phase,
            time = time,
            entry = entry,
            activity = activity,
            startPulse = startPulse,
        )
    }
}

@Composable
private fun rememberOverlayFrameTime(): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val resumed = lifecycleState == Lifecycle.State.RESUMED
    LaunchedEffect(lifecycleOwner, resumed) {
        if (!resumed) return@LaunchedEffect
        if ((coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) == 0f) return@LaunchedEffect
        var lastFrame = Long.MIN_VALUE
        while (true) {
            withFrameNanos { now ->
                if (lastFrame == Long.MIN_VALUE) {
                    lastFrame = now
                } else {
                    val delta = (now - lastFrame).coerceAtMost(MAX_FRAME_STEP_NANOS)
                    if (delta >= FRAME_STEP_NANOS) {
                        lastFrame = now
                        time.floatValue += delta / NANOS_PER_SECOND
                    }
                }
            }
        }
    }
    return time
}

