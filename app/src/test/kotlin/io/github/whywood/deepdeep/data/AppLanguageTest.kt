package io.github.whywood.deepdeep.data

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLanguageTest {
    @Test
    fun tagsRoundTrip() {
        AppLanguage.entries.forEach { language ->
            assertEquals(language, AppLanguage.fromTag(language.tag))
        }
    }

    @Test
    fun knownLanguageTagsMapToLanguages() {
        assertEquals(AppLanguage.CHINESE, AppLanguage.fromTag("zh"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag("system"))
    }

    @Test
    fun undecodableTagsFallBackToSystem() {
        assertNull(AppLanguage.fromTag(null).locale)
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(""))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag("fr"))
    }

    @Test
    fun explicitLanguagesResolveToConcreteLocales() {
        val chinese = AppLanguage.CHINESE.locale
        assertEquals(Locale.forLanguageTag("zh-CN"), chinese)
        assertEquals("zh", chinese?.language)

        val english = AppLanguage.ENGLISH.locale
        assertEquals(Locale.forLanguageTag("en"), english)
        assertEquals("en", english?.language)

        assertNull(AppLanguage.SYSTEM.locale)
    }
}
