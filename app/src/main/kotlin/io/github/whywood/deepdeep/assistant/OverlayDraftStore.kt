package io.github.whywood.deepdeep.assistant

import android.content.Context

internal interface OverlayDraftPersistence {
    fun read(): String?

    fun write(value: String?)
}

internal class OverlayDraftStore(private val persistence: OverlayDraftPersistence) {
    fun read(): String = persistence.read().orEmpty()

    fun save(text: String) {
        persistence.write(text.ifEmpty { null })
    }

    fun clear() {
        persistence.write(null)
    }
}

internal class PreferencesOverlayDraftPersistence(context: Context) : OverlayDraftPersistence {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun read(): String? = preferences.getString(KEY_DRAFT, null)

    override fun write(value: String?) {
        preferences.edit().apply {
            if (value == null) remove(KEY_DRAFT) else putString(KEY_DRAFT, value)
        }.apply()
    }

    private companion object {
        const val FILE_NAME = "overlay_draft"
        const val KEY_DRAFT = "input"
    }
}
