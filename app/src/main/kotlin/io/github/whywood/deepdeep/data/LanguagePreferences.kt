package io.github.whywood.deepdeep.data

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

enum class AppLanguage(val tag: String) {
    SYSTEM("system"),
    CHINESE("zh"),
    ENGLISH("en");

    val locale: Locale?
        get() = when (this) {
            SYSTEM -> null
            CHINESE -> Locale.forLanguageTag("zh-CN")
            ENGLISH -> Locale.forLanguageTag("en")
        }

    companion object {
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: SYSTEM
    }
}

class LanguagePreferences(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun read(): AppLanguage = AppLanguage.fromTag(preferences.getString(KEY_LANGUAGE, null))

    fun save(language: AppLanguage) {
        preferences.edit().putString(KEY_LANGUAGE, language.tag).apply()
    }

    companion object {
        private const val FILE_NAME = "language_settings"
        private const val KEY_LANGUAGE = "language"

        fun wrap(context: Context): Context {
            val locale = LanguagePreferences(context).read().locale ?: return context
            val configuration = Configuration(context.resources.configuration)
            configuration.setLocale(locale)
            configuration.setLayoutDirection(locale)
            return context.createConfigurationContext(configuration)
        }
    }
}
