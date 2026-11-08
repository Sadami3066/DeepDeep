package io.github.whywood.deepdeep.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.whywood.deepdeep.design.ThemePreset
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.settings by preferencesDataStore(name = "appearance")

class ThemePreferences(context: Context) {
    private val store = context.applicationContext.settings
    private val presetKey = stringPreferencesKey("theme_preset")

    val preset = store.data.map { values ->
        ThemePreset.entries.firstOrNull { it.name == values[presetKey] } ?: ThemePreset.SYSTEM
    }.catch { error ->
        if (error is IOException) emit(ThemePreset.SYSTEM) else throw error
    }

    suspend fun setPreset(preset: ThemePreset) {
        store.edit { it[presetKey] = preset.name }
    }
}
