package io.github.whywood.deepdeep.data

import android.content.Context
import io.github.whywood.deepdeep.design.DeepDeepColors
import io.github.whywood.deepdeep.theme.ThemeChoice
import io.github.whywood.deepdeep.theme.ThemePaletteCodec

data class ThemeSelection(
    val choice: ThemeChoice,
    val palette: DeepDeepColors?,
    val label: String?,
)

class ThemePreferences(context: Context) {
    private val store = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun read(): ThemeSelection {
        val choice = ThemeChoice.parse(store.getString(CHOICE_KEY, null))
        if (choice !is ThemeChoice.Pack) return ThemeSelection(choice, null, null)
        return ThemeSelection(
            choice = choice,
            palette = ThemePaletteCodec.decode(store.getString(PALETTE_KEY, null)),
            label = store.getString(LABEL_KEY, null),
        )
    }

    fun write(choice: ThemeChoice, palette: DeepDeepColors?, label: String?) {
        store.edit().apply {
            putString(CHOICE_KEY, choice.key)
            if (palette == null) {
                remove(PALETTE_KEY)
                remove(LABEL_KEY)
            } else {
                putString(PALETTE_KEY, ThemePaletteCodec.encode(palette))
                putString(LABEL_KEY, label)
            }
        }.apply()
    }

    private companion object {
        const val NAME = "appearance"
        const val CHOICE_KEY = "theme_choice"
        const val PALETTE_KEY = "theme_palette"
        const val LABEL_KEY = "theme_label"
    }
}
