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
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class OverlayRunPhase { Idle, Thinking, Streaming, Waiting, Done, Stopped, Failed }

internal data class OverlayRunSignal(
    val loaded: Boolean = false,
    val sessionId: String? = null,
    val workspaceId: String = "",
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
        return OverlayRunSignal(sessionId = sessionId, workspaceId = state.workspaceId)
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
        workspaceId = state.workspaceId,
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

internal class OverlayReplyTracker {
    private var seenLoad = false
    private var sessionId: String? = null
    private var workspaceId: String = ""
    private var lastKey: String? = null

    fun update(signal: OverlayRunSignal): Boolean {
        val currentSession = signal.sessionId
        if (!signal.loaded || currentSession == null) return false
        if (!seenLoad) {
            seenLoad = true
            lastKey = streamingKey(signal)
            record(signal)
            return false
        }
        val scopeChanged = currentSession != sessionId || signal.workspaceId != workspaceId
        if (scopeChanged) {
            lastKey = streamingKey(signal)
            record(signal)
            return false
        }
        val key = streamingKey(signal)
        val replyStarted = signal.phase == OverlayRunPhase.Streaming && key != null && key != lastKey
        if (replyStarted) lastKey = key
        record(signal)
        return replyStarted
    }

    private fun streamingKey(signal: OverlayRunSignal): String? {
        val messageId = signal.messageId
        if (messageId != null) return "message:$messageId"
        val runId = signal.runId
        return if (runId.isNotBlank()) "run:$runId" else null
    }

    private fun record(signal: OverlayRunSignal) {
        sessionId = signal.sessionId
        workspaceId = signal.workspaceId
    }
}

@Stable
internal class OverlayGlow internal constructor(
    val phase: OverlayRunPhase,
    val time: State<Float>,
    val entry: Animatable<Float, AnimationVector1D>,
    val activity: Animatable<Float, AnimationVector1D>,
    val startPulse: Animatable<Float, AnimationVector1D>,
    val rise: Animatable<Float, AnimationVector1D>,
    val replyPulse: Animatable<Float, AnimationVector1D>,
    val replySweep: Animatable<Float, AnimationVector1D>,
) {
    val active: Boolean
        get() = phase == OverlayRunPhase.Thinking ||
            phase == OverlayRunPhase.Streaming ||
            phase == OverlayRunPhase.Waiting
}

private const val GLOW_ENTRY_DELAY_MS = 180
private const val GLOW_ENTRY_IN_MS = 260
private const val GLOW_ENTRY_OUT_MS = 560
private const val GLOW_PULSE_IN_MS = 180
private const val GLOW_PULSE_OUT_MS = 500
private const val GLOW_RISE_DELAY_MS = 80
private const val GLOW_RISE_MS = 900
private const val GLOW_REPLY_IN_MS = 120
private const val GLOW_REPLY_HOLD_MS = 350
private const val GLOW_REPLY_OUT_MS = 400
private const val GLOW_SWEEP_MS = 900
private const val GLOW_ACTIVITY_MS = 320
private const val GLOW_DONE_MS = 900
private const val GLOW_STOP_MS = 480
private const val FRAME_STEP_NANOS = 16_666_667L
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
    val rise = remember { Animatable(0f) }
    val replyPulse = remember { Animatable(0f) }
    val replySweep = remember { Animatable(0f) }
    val startTracker = remember { OverlayStartTracker() }
    val replyTracker = remember { OverlayReplyTracker() }
    val playsEntry = remember { !entryPlayed }
    var entryStarted by remember { mutableStateOf(false) }
    var riseStarted by remember { mutableStateOf(false) }
    var pulseToken by remember { mutableIntStateOf(0) }
    var replyToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(signal) {
        if (startTracker.update(signal)) pulseToken += 1
        if (replyTracker.update(signal)) replyToken += 1
    }
    LaunchedEffect(playsEntry, entryStart) {
        if (entryStarted || !playsEntry || !entryStart) return@LaunchedEffect
        entryStarted = true
        if (motionScale() == 0f) return@LaunchedEffect
        delay(GLOW_ENTRY_DELAY_MS.toLong())
        entry.animateTo(1f, tween(GLOW_ENTRY_IN_MS, easing = DdMotion.EaseOut))
        entry.animateTo(0f, tween(GLOW_ENTRY_OUT_MS, easing = DdMotion.EaseInOut))
    }
    LaunchedEffect(entryStart) {
        if (riseStarted || !entryStart) return@LaunchedEffect
        riseStarted = true
        if (motionScale() == 0f) {
            rise.snapTo(1f)
            return@LaunchedEffect
        }
        delay(GLOW_RISE_DELAY_MS.toLong())
        rise.animateTo(1f, tween(GLOW_RISE_MS, easing = DdMotion.EaseOut))
    }
    LaunchedEffect(signal.phase) {
        val target = signal.phase.glowActivityTarget()
        if (motionScale() == 0f) {
            activity.snapTo(target)
            return@LaunchedEffect
        }
        activity.animateTo(
            targetValue = target,
            animationSpec = tween(
                durationMillis = signal.phase.glowActivityDurationMs(),
                easing = DdMotion.EaseInOut,
            ),
        )
    }
    LaunchedEffect(pulseToken) {
        if (pulseToken == 0 || motionScale() == 0f) return@LaunchedEffect
        startPulse.snapTo(0f)
        startPulse.animateTo(1f, tween(GLOW_PULSE_IN_MS, easing = DdMotion.EaseOut))
        startPulse.animateTo(0f, tween(GLOW_PULSE_OUT_MS, easing = DdMotion.EaseInOut))
    }
    LaunchedEffect(replyToken) {
        if (replyToken == 0 || motionScale() == 0f) return@LaunchedEffect
        replySweep.snapTo(0f)
        replyPulse.snapTo(0f)
        launch {
            replyPulse.animateTo(1f, tween(GLOW_REPLY_IN_MS, easing = DdMotion.EaseOut))
            delay(GLOW_REPLY_HOLD_MS.toLong())
            replyPulse.animateTo(0f, tween(GLOW_REPLY_OUT_MS, easing = DdMotion.EaseInOut))
        }
        replySweep.animateTo(1f, tween(GLOW_SWEEP_MS, easing = DdMotion.EaseInOut))
    }

    return remember(time, entry, activity, startPulse, rise, replyPulse, replySweep, signal.phase) {
        OverlayGlow(
            phase = signal.phase,
            time = time,
            entry = entry,
            activity = activity,
            startPulse = startPulse,
            rise = rise,
            replyPulse = replyPulse,
            replySweep = replySweep,
        )
    }
}

private suspend fun motionScale(): Float =
    (coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f)

@Composable
private fun rememberOverlayFrameTime(): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val resumed = lifecycleState == Lifecycle.State.RESUMED
    LaunchedEffect(lifecycleOwner, resumed) {
        if (!resumed) return@LaunchedEffect
        if (motionScale() == 0f) return@LaunchedEffect
        var lastFrame = Long.MIN_VALUE
        var carry = 0L
        while (true) {
            withFrameNanos { now ->
                if (lastFrame != Long.MIN_VALUE) {
                    carry += (now - lastFrame).coerceAtMost(MAX_FRAME_STEP_NANOS)
                    while (carry >= FRAME_STEP_NANOS) {
                        carry -= FRAME_STEP_NANOS
                        time.floatValue += FRAME_STEP_NANOS / NANOS_PER_SECOND
                    }
                }
                lastFrame = now
            }
        }
    }
    return time
}
