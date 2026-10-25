package io.github.whywood.deepdeep.voice

import io.github.whywood.deepdeep.data.AppLanguage
import java.util.Locale

internal object VoiceLanguage {
    fun recognitionTag(language: AppLanguage): String = when (language) {
        AppLanguage.CHINESE -> "zh-CN"
        AppLanguage.ENGLISH -> "en-US"
        AppLanguage.SYSTEM -> Locale.getDefault().toLanguageTag()
    }

    fun ttsCandidates(language: AppLanguage): List<Locale> = when (language) {
        AppLanguage.CHINESE -> listOf(Locale.SIMPLIFIED_CHINESE, Locale.CHINESE)
        AppLanguage.ENGLISH -> listOf(Locale.US, Locale.ENGLISH)
        AppLanguage.SYSTEM -> listOf(Locale.getDefault(), Locale.ENGLISH)
    }.distinctBy { it.toLanguageTag() }
}
