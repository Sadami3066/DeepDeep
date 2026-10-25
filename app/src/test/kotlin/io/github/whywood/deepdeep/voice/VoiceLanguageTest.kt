package io.github.whywood.deepdeep.voice

import io.github.whywood.deepdeep.data.AppLanguage
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceLanguageTest {
    @Test
    fun recognitionTagFollowsAppLanguage() {
        assertEquals("zh-CN", VoiceLanguage.recognitionTag(AppLanguage.CHINESE))
        assertEquals("en-US", VoiceLanguage.recognitionTag(AppLanguage.ENGLISH))
        assertEquals(Locale.getDefault().toLanguageTag(), VoiceLanguage.recognitionTag(AppLanguage.SYSTEM))
    }

    @Test
    fun chineseCandidatesFallBackFromSimplified() {
        val candidates = VoiceLanguage.ttsCandidates(AppLanguage.CHINESE)
        assertTrue(candidates.size >= 2)
        assertEquals(Locale.SIMPLIFIED_CHINESE, candidates.first())
        assertEquals(Locale.CHINESE, candidates.last())
    }

    @Test
    fun englishCandidatesFallBackToGenericLocale() {
        val candidates = VoiceLanguage.ttsCandidates(AppLanguage.ENGLISH)
        assertTrue(candidates.size >= 2)
        assertEquals(Locale.US, candidates.first())
        assertEquals(Locale.ENGLISH, candidates.last())
    }

    @Test
    fun systemCandidatesStartWithDefaultLocale() {
        val candidates = VoiceLanguage.ttsCandidates(AppLanguage.SYSTEM)
        assertTrue(candidates.isNotEmpty())
        assertEquals(Locale.getDefault(), candidates.first())
    }
}
