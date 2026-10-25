package io.github.whywood.deepdeep.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.voiceStore by preferencesDataStore(name = "voice")

class VoicePreferences(context: Context) {
    private val store = context.applicationContext.voiceStore
    private val autoSpeakKey = booleanPreferencesKey("auto_speak")
    private val sendAfterRecognitionKey = booleanPreferencesKey("send_after_recognition")

    val autoSpeak: Flow<Boolean> = store.data
        .map { values -> values[autoSpeakKey] ?: false }
        .catch { error -> if (error is IOException) emit(false) else throw error }

    val sendAfterRecognition: Flow<Boolean> = store.data
        .map { values -> values[sendAfterRecognitionKey] ?: false }
        .catch { error -> if (error is IOException) emit(false) else throw error }

    suspend fun setAutoSpeak(enabled: Boolean) {
        store.edit { it[autoSpeakKey] = enabled }
    }

    suspend fun setSendAfterRecognition(enabled: Boolean) {
        store.edit { it[sendAfterRecognitionKey] = enabled }
    }
}
