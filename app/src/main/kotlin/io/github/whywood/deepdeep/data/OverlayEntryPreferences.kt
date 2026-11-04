package io.github.whywood.deepdeep.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.overlayEntryDataStore by preferencesDataStore(name = "overlay_entry")

enum class OverlayEntryMode {
    VOICE,
    CAMERA,
    NONE,
}

internal fun parseOverlayEntryMode(value: String?): OverlayEntryMode = when (value) {
    "camera" -> OverlayEntryMode.CAMERA
    "none" -> OverlayEntryMode.NONE
    else -> OverlayEntryMode.VOICE
}

internal fun OverlayEntryMode.storageValue(): String = when (this) {
    OverlayEntryMode.VOICE -> "voice"
    OverlayEntryMode.CAMERA -> "camera"
    OverlayEntryMode.NONE -> "none"
}

class OverlayEntryPreferences(context: Context) {
    private val store = context.applicationContext.overlayEntryDataStore
    private val entryModeKey = stringPreferencesKey("entry_mode")

    val mode: Flow<OverlayEntryMode> = store.data
        .map { values -> parseOverlayEntryMode(values[entryModeKey]) }
        .catch { error -> if (error is IOException) emit(OverlayEntryMode.VOICE) else throw error }

    suspend fun read(): OverlayEntryMode = mode.first()

    suspend fun setMode(mode: OverlayEntryMode) {
        store.edit { it[entryModeKey] = mode.storageValue() }
    }
}
