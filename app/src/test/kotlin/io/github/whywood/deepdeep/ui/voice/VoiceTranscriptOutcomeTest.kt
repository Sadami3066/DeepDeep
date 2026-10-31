package io.github.whywood.deepdeep.ui.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceTranscriptOutcomeTest {
    @Test
    fun blankTranscriptProducesNoOutcome() {
        assertNull(voiceTranscriptOutcome("", "   ", sendAfterRecognition = true))
    }

    @Test
    fun sendsRecognizedTextWhenPreferenceOnAndInputEmpty() {
        val outcome = voiceTranscriptOutcome("", " 打开设置 ", sendAfterRecognition = true)
        assertEquals(VoiceTranscriptOutcome(VoiceTranscriptMode.Send, "打开设置"), outcome)
    }

    @Test
    fun fillsInputWhenPreferenceOff() {
        val outcome = voiceTranscriptOutcome("", "打开设置", sendAfterRecognition = false)
        assertEquals(VoiceTranscriptOutcome(VoiceTranscriptMode.Fill, "打开设置"), outcome)
    }

    @Test
    fun keepsExistingInputInsteadOfSending() {
        val outcome = voiceTranscriptOutcome("先看一下", "打开设置", sendAfterRecognition = true)
        assertEquals(VoiceTranscriptOutcome(VoiceTranscriptMode.Fill, "先看一下 打开设置"), outcome)
    }

    @Test
    fun appendsToExistingInputWithSingleSpace() {
        val outcome = voiceTranscriptOutcome("先看一下  ", "打开设置", sendAfterRecognition = false)
        assertEquals(VoiceTranscriptOutcome(VoiceTranscriptMode.Fill, "先看一下 打开设置"), outcome)
    }
}
