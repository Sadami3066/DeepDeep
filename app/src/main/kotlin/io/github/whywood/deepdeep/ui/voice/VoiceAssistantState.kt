package io.github.whywood.deepdeep.ui.voice

import io.github.whywood.deepdeep.voice.SpeechError

internal enum class VoicePhase {
    IDLE,
    LISTENING,
    SENDING,
    SPEAKING,
    ERROR,
}

internal data class VoiceFailure(
    val error: SpeechError,
    val permissionPermanentlyDenied: Boolean = false,
)

internal sealed interface VoiceEvent {
    data object StartListening : VoiceEvent
    data class Partial(val text: String) : VoiceEvent
    data class Final(val text: String) : VoiceEvent
    data class Failed(val error: SpeechError) : VoiceEvent
    data class PermissionDenied(val permanently: Boolean) : VoiceEvent
    data class AnswerCompleted(val speakable: Boolean) : VoiceEvent
    data object SpeechFinished : VoiceEvent
    data object StopSpeaking : VoiceEvent
}

internal data class VoiceAssistantState(
    val phase: VoicePhase = VoicePhase.IDLE,
    val transcript: String = "",
    val failure: VoiceFailure? = null,
    val sent: Boolean = false,
)

internal fun reduceVoiceState(
    state: VoiceAssistantState,
    event: VoiceEvent,
    sendAfterRecognition: Boolean,
    autoSpeak: Boolean,
): VoiceAssistantState = when (event) {
    VoiceEvent.StartListening -> VoiceAssistantState(phase = VoicePhase.LISTENING)

    is VoiceEvent.Partial ->
        if (state.phase == VoicePhase.LISTENING) state.copy(transcript = event.text) else state

    is VoiceEvent.Final -> {
        val text = event.text.trim()
        if (state.phase != VoicePhase.LISTENING || text.isEmpty()) {
            state
        } else {
            state.copy(
                transcript = text,
                phase = if (sendAfterRecognition) VoicePhase.SENDING else VoicePhase.IDLE,
                sent = sendAfterRecognition,
            )
        }
    }

    is VoiceEvent.Failed -> state.copy(
        phase = VoicePhase.ERROR,
        failure = VoiceFailure(event.error),
    )

    is VoiceEvent.PermissionDenied -> state.copy(
        phase = VoicePhase.ERROR,
        failure = VoiceFailure(SpeechError.INSUFFICIENT_PERMISSIONS, event.permanently),
    )

    is VoiceEvent.AnswerCompleted -> if (state.phase == VoicePhase.SENDING) {
        state.copy(phase = if (autoSpeak && event.speakable) VoicePhase.SPEAKING else VoicePhase.IDLE)
    } else {
        state
    }

    VoiceEvent.SpeechFinished ->
        if (state.phase == VoicePhase.SPEAKING) state.copy(phase = VoicePhase.IDLE) else state

    VoiceEvent.StopSpeaking ->
        if (state.phase == VoicePhase.SPEAKING) state.copy(phase = VoicePhase.IDLE) else state
}
