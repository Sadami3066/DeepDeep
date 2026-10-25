package io.github.whywood.deepdeep.voice

import android.speech.SpeechRecognizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSpeechEventsTest {
    private fun failure(code: Int): SpeechEvent.Failed = speechEventForError(code) as SpeechEvent.Failed

    @Test
    fun mapsNoMatch() {
        assertEquals(SpeechError.NO_MATCH, failure(SpeechRecognizer.ERROR_NO_MATCH).error)
    }

    @Test
    fun mapsSpeechTimeoutToTimeoutEvent() {
        assertTrue(speechEventForError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT) is SpeechEvent.Timeout)
    }

    @Test
    fun mapsPermissionNetworkAndBusy() {
        assertEquals(
            SpeechError.INSUFFICIENT_PERMISSIONS,
            failure(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS).error,
        )
        assertEquals(SpeechError.NETWORK, failure(SpeechRecognizer.ERROR_NETWORK).error)
        assertEquals(SpeechError.NETWORK_TIMEOUT, failure(SpeechRecognizer.ERROR_NETWORK_TIMEOUT).error)
        assertEquals(SpeechError.RECOGNIZER_BUSY, failure(SpeechRecognizer.ERROR_RECOGNIZER_BUSY).error)
    }

    @Test
    fun mapsLanguageErrors() {
        assertEquals(
            SpeechError.LANGUAGE_NOT_SUPPORTED,
            failure(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED).error,
        )
        assertEquals(
            SpeechError.LANGUAGE_UNAVAILABLE,
            failure(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE).error,
        )
    }

    @Test
    fun mapsClientServerAndAudio() {
        assertEquals(SpeechError.CLIENT, failure(SpeechRecognizer.ERROR_CLIENT).error)
        assertEquals(SpeechError.SERVER, failure(SpeechRecognizer.ERROR_SERVER).error)
        assertEquals(SpeechError.AUDIO, failure(SpeechRecognizer.ERROR_AUDIO).error)
    }

    @Test
    fun unknownCodeBecomesUnknownFailure() {
        val event = failure(99)
        assertEquals(SpeechError.UNKNOWN, event.error)
        assertEquals(99, event.code)
    }

    @Test
    fun rmsLevelStaysWithinBounds() {
        assertEquals(0.08f, rmsLevel(-100f), 0.0001f)
        assertEquals(1f, rmsLevel(100f), 0.0001f)
        assertEquals(0.5f, rmsLevel(4f), 0.0001f)
    }
}
