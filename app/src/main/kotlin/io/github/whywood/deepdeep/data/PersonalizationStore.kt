package io.github.whywood.deepdeep.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.whywood.deepdeep.runtime.InteractionStyle
import io.github.whywood.deepdeep.runtime.Personalization
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.personalizationDataStore by preferencesDataStore(name = "personalization")

enum class TonePreset {
    WARM,
    CUTE,
    CALM,
    CUSTOM,
}

data class PersonalizationUi(
    val nickname: String = "Milous",
    val interaction: InteractionStyle = InteractionStyle.BALANCED,
    val tone: TonePreset = TonePreset.CUSTOM,
    val instruction: String = "",
) {
    fun toRuntime(): Personalization = Personalization(
        nickname = nickname.trim(),
        interaction = interaction,
        instruction = instruction,
    )
}

class PersonalizationStore(context: Context) {
    private val store = context.applicationContext.personalizationDataStore
    private val nicknameKey = stringPreferencesKey("nickname")
    private val interactionKey = stringPreferencesKey("interaction")
    private val toneKey = stringPreferencesKey("tone")
    private val instructionKey = stringPreferencesKey("instruction")

    val personalization: Flow<PersonalizationUi> = store.data
        .map { values ->
            PersonalizationUi(
                nickname = values[nicknameKey] ?: "Milous",
                interaction = InteractionStyle.entries
                    .firstOrNull { it.name == values[interactionKey] }
                    ?: InteractionStyle.BALANCED,
                tone = TonePreset.entries
                    .firstOrNull { it.name == values[toneKey] }
                    ?: TonePreset.CUSTOM,
                instruction = values[instructionKey].orEmpty(),
            )
        }
        .catch { error ->
            if (error is IOException) emit(PersonalizationUi()) else throw error
        }

    suspend fun read(): PersonalizationUi = personalization.first()

    suspend fun save(value: PersonalizationUi) {
        store.edit { values ->
            values[nicknameKey] = value.nickname.take(MAX_NICKNAME_LENGTH)
            values[interactionKey] = value.interaction.name
            values[toneKey] = value.tone.name
            values[instructionKey] = value.instruction
        }
    }

    companion object {
        const val MAX_NICKNAME_LENGTH = 40
    }
}
