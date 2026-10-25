package io.github.whywood.deepdeep.ui.voice

import io.github.whywood.deepdeep.voice.SpeechError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceAssistantStateTest {
    private fun reduce(
        state: VoiceAssistantState,
        event: VoiceEvent,
        sendAfterRecognition: Boolean = true,
        autoSpeak: Boolean = true,
    ): VoiceAssistantState = reduceVoiceState(state, event, sendAfterRecognition, autoSpeak)

    @Test
    fun startsIdle() {
        val state = VoiceAssistantState()
        assertEquals(VoicePhase.IDLE, state.phase)
        assertEquals("", state.transcript)
        assertNull(state.failure)
        assertFalse(state.sent)
    }

    @Test
    fun startListeningResetsPreviousState() {
        val previous = VoiceAssistantState(
            phase = VoicePhase.ERROR,
            transcript = "old",
            failure = VoiceFailure(SpeechError.NO_MATCH),
            sent = true,
        )
        val next = reduce(previous, VoiceEvent.StartListening)
        assertEquals(VoicePhase.LISTENING, next.phase)
        assertEquals("", next.transcript)
        assertNull(next.failure)
        assertFalse(next.sent)
    }

    @Test
    fun partialOnlyUpdatesWhileListening() {
        val listening = VoiceAssistantState(phase = VoicePhase.LISTENING)
        assertEquals("你好", reduce(listening, VoiceEvent.Partial("你好")).transcript)
        val idle = VoiceAssistantState()
        assertEquals(idle, reduce(idle, VoiceEvent.Partial("你好")))
    }

    @Test
    fun finalFillsInputWhenSendAfterRecognitionIsOff() {
        val listening = VoiceAssistantState(phase = VoicePhase.LISTENING)
        val next = reduce(listening, VoiceEvent.Final("  打开设置 "), sendAfterRecognition = false, autoSpeak = false)
        assertEquals(VoicePhase.IDLE, next.phase)
        assertEquals("打开设置", next.transcript)
        assertFalse(next.sent)
    }

    @Test
    fun finalSendsWhenSendAfterRecognitionIsOn() {
        val listening = VoiceAssistantState(phase = VoicePhase.LISTENING)
        val next = reduce(listening, VoiceEvent.Final("打开设置"))
        assertEquals(VoicePhase.SENDING, next.phase)
        assertEquals("打开设置", next.transcript)
        assertTrue(next.sent)
    }

    @Test
    fun finalIgnoredOutsideListening() {
        val error = VoiceAssistantState(phase = VoicePhase.ERROR)
        assertEquals(error, reduce(error, VoiceEvent.Final("text")))
    }

    @Test
    fun speechFailureEntersErrorState() {
        val next = reduce(
            VoiceAssistantState(phase = VoicePhase.LISTENING),
            VoiceEvent.Failed(SpeechError.NETWORK),
        )
        assertEquals(VoicePhase.ERROR, next.phase)
        assertEquals(SpeechError.NETWORK, next.failure?.error)
    }

    @Test
    fun deniedPermissionRemembersPermanence() {
        val permanent = reduce(VoiceAssistantState(), VoiceEvent.PermissionDenied(true))
        assertEquals(VoicePhase.ERROR, permanent.phase)
        assertEquals(SpeechError.INSUFFICIENT_PERMISSIONS, permanent.failure?.error)
        assertTrue(permanent.failure?.permissionPermanentlyDenied == true)

        val retryable = reduce(VoiceAssistantState(), VoiceEvent.PermissionDenied(false))
        assertFalse(retryable.failure?.permissionPermanentlyDenied == true)
    }

    @Test
    fun completedAnswerSpeaksOnlyWhenEnabledAndSpeakable() {
        val sending = VoiceAssistantState(phase = VoicePhase.SENDING, sent = true)
        assertEquals(VoicePhase.SPEAKING, reduce(sending, VoiceEvent.AnswerCompleted(true), autoSpeak = true).phase)
        assertEquals(VoicePhase.IDLE, reduce(sending, VoiceEvent.AnswerCompleted(true), autoSpeak = false).phase)
        assertEquals(VoicePhase.IDLE, reduce(sending, VoiceEvent.AnswerCompleted(false), autoSpeak = true).phase)
    }

    @Test
    fun completedAnswerIgnoredOutsideSending() {
        val idle = VoiceAssistantState()
        assertEquals(idle, reduce(idle, VoiceEvent.AnswerCompleted(true)))
    }

    @Test
    fun speechFinishedReturnsFromSpeakingAndStopSpeakingStops() {
        val speaking = VoiceAssistantState(phase = VoicePhase.SPEAKING, sent = true)
        val finished = reduce(speaking, VoiceEvent.SpeechFinished)
        assertEquals(VoicePhase.IDLE, finished.phase)
        assertTrue(finished.sent)

        val stopped = reduce(speaking, VoiceEvent.StopSpeaking)
        assertEquals(VoicePhase.IDLE, stopped.phase)

        val idle = VoiceAssistantState()
        assertEquals(idle, reduce(idle, VoiceEvent.SpeechFinished))
    }
}
