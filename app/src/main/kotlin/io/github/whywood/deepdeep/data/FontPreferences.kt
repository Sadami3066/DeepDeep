package io.github.whywood.deepdeep.data

import android.content.Context
import io.github.whywood.deepdeep.design.DdFontStyle

class FontPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun read(): DdFontStyle = DdFontStyle.fromId(preferences.getString(KEY_FONT_STYLE, null))

    fun save(style: DdFontStyle) {
        preferences.edit().putString(KEY_FONT_STYLE, style.id).apply()
    }

    companion object {
        private const val FILE_NAME = "font_settings"
        private const val KEY_FONT_STYLE = "font_style"
    }
}
