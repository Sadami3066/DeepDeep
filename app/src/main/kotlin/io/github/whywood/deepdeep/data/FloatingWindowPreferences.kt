package io.github.whywood.deepdeep.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.floatingWindowDataStore by preferencesDataStore(name = "floating_window")

const val FLOATING_POSITION_UNSET = -1

data class FloatingWindowSettings(
    val enabled: Boolean = false,
    val bubbleX: Int = FLOATING_POSITION_UNSET,
    val bubbleY: Int = FLOATING_POSITION_UNSET,
) {
    val hasBubblePosition: Boolean
        get() = bubbleX != FLOATING_POSITION_UNSET && bubbleY != FLOATING_POSITION_UNSET
}

class FloatingWindowPreferences(context: Context) {
    private val store = context.applicationContext.floatingWindowDataStore
    private val enabledKey = booleanPreferencesKey("enabled")
    private val xKey = intPreferencesKey("x")
    private val yKey = intPreferencesKey("y")

    val settings: Flow<FloatingWindowSettings> = store.data
        .map { values ->
            FloatingWindowSettings(
                enabled = values[enabledKey] ?: false,
                bubbleX = values[xKey] ?: FLOATING_POSITION_UNSET,
                bubbleY = values[yKey] ?: FLOATING_POSITION_UNSET,
            )
        }
        .catch { error ->
            if (error is IOException) emit(FloatingWindowSettings()) else throw error
        }

    suspend fun read(): FloatingWindowSettings = settings.first()

    suspend fun setEnabled(enabled: Boolean) {
        store.edit { it[enabledKey] = enabled }
    }

    suspend fun setBubblePosition(x: Int, y: Int) {
        store.edit { values ->
            values[xKey] = x
            values[yKey] = y
        }
    }
}
