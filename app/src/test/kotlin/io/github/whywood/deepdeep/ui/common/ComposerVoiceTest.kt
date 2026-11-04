package io.github.whywood.deepdeep.ui.common

import android.speech.SpeechRecognizer
import io.github.whywood.deepdeep.voice.MAX_VOICE_RETRIES
import io.github.whywood.deepdeep.voice.SpeechError
import io.github.whywood.deepdeep.voice.VoiceErrorDisposition
import io.github.whywood.deepdeep.voice.speechErrorForCode
import io.github.whywood.deepdeep.voice.voiceErrorDisposition
import io.github.whywood.deepdeep.voice.voiceShouldRetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerVoiceTest {
    @Test
    fun appendsFirstSegmentAsIs() {
        assertEquals("打开设置", appendVoiceSegment("", "  打开设置 "))
    }

    @Test
    fun appendsSegmentsWithSingleSpace() {
        assertEquals("打开设置 然后返回", appendVoiceSegment("打开设置", " 然后返回 "))
    }

    @Test
    fun appendsToBufferThatAlreadyEndsWithSpace() {
        assertEquals("先看一下 打开设置", appendVoiceSegment("先看一下   ", "打开设置"))
    }

    @Test
    fun blankSegmentKeepsBufferUntouched() {
        assertEquals("打开设置", appendVoiceSegment("打开设置", "   "))
        assertEquals("", appendVoiceSegment("", ""))
    }

    @Test
    fun commitTextIsTrimmed() {
        assertEquals("打开设置 然后返回", takeVoiceCommitText("  打开设置 然后返回  "))
    }

    @Test
    fun blankBufferCommitsNothing() {
        assertNull(takeVoiceCommitText(""))
        assertNull(takeVoiceCommitText("   "))
    }

    @Test
    fun stoppingCommitsAllBufferedSegmentsTrimmed() {
        val buffer = appendVoiceSegment(appendVoiceSegment("", " 打开设置 "), " 然后返回 ")
        assertEquals("打开设置 然后返回", takeVoiceCommitText(buffer))
    }

    @Test
    fun stoppingWithBlankBufferCommitsNothing() {
        assertNull(takeVoiceCommitText(appendVoiceSegment("", "   ")))
    }

    @Test
    fun silenceErrorsRestartSession() {
        assertEquals(VoiceErrorDisposition.RESTART, voiceErrorDisposition(SpeechError.NO_MATCH))
        assertEquals(VoiceErrorDisposition.RESTART, voiceErrorDisposition(SpeechError.SPEECH_TIMEOUT))
    }

    @Test
    fun networkErrorsAreRetried() {
        assertEquals(VoiceErrorDisposition.RETRY, voiceErrorDisposition(SpeechError.NETWORK))
        assertEquals(VoiceErrorDisposition.RETRY, voiceErrorDisposition(SpeechError.NETWORK_TIMEOUT))
        assertTrue(voiceShouldRetry(SpeechError.NETWORK, attempt = 0))
        assertTrue(voiceShouldRetry(SpeechError.NETWORK, attempt = MAX_VOICE_RETRIES - 1))
        assertFalse(voiceShouldRetry(SpeechError.NETWORK, attempt = MAX_VOICE_RETRIES))
    }

    @Test
    fun hardErrorsFailSession() {
        val hard = listOf(
            SpeechError.INSUFFICIENT_PERMISSIONS,
            SpeechError.UNAVAILABLE,
            SpeechError.RECOGNIZER_BUSY,
            SpeechError.AUDIO,
            SpeechError.CLIENT,
            SpeechError.LANGUAGE_NOT_SUPPORTED,
            SpeechError.LANGUAGE_UNAVAILABLE,
        )
        hard.forEach { error ->
            assertEquals(VoiceErrorDisposition.FAIL, voiceErrorDisposition(error))
            assertFalse(voiceShouldRetry(error, attempt = 0))
        }
    }

    @Test
    fun mapsRecognizerCodesToSpeechErrors() {
        assertEquals(SpeechError.NO_MATCH, speechErrorForCode(SpeechRecognizer.ERROR_NO_MATCH))
        assertEquals(SpeechError.SPEECH_TIMEOUT, speechErrorForCode(SpeechRecognizer.ERROR_SPEECH_TIMEOUT))
        assertEquals(SpeechError.NETWORK, speechErrorForCode(SpeechRecognizer.ERROR_NETWORK))
        assertEquals(SpeechError.CLIENT, speechErrorForCode(SpeechRecognizer.ERROR_CLIENT))
        assertEquals(SpeechError.UNKNOWN, speechErrorForCode(99))
    }
}
