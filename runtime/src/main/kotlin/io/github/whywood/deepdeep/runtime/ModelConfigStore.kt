package io.github.whywood.deepdeep.runtime

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.modelDataStore by preferencesDataStore(name = "model_config")

data class ModelConfig(
    val modelId: String = DeepSeekModels.defaultModelId(),
    val thinkingLevel: ThinkingLevel = ThinkingLevel.OFF,
)

class ModelConfigStore(context: Context) {
    private val store = context.applicationContext.modelDataStore
    private val modelKey = stringPreferencesKey("model_id")
    private val thinkingKey = stringPreferencesKey("thinking_level")

    val config: Flow<ModelConfig> = store.data
        .map { values ->
            val modelId = values[modelKey]?.takeIf { it.isNotBlank() } ?: DeepSeekModels.defaultModelId()
            val thinking = runCatching {
                ThinkingLevel.valueOf(values[thinkingKey] ?: ThinkingLevel.OFF.name)
            }.getOrDefault(ThinkingLevel.OFF)
            ModelConfig(modelId = modelId, thinkingLevel = thinking)
        }
        .catch { error ->
            if (error is IOException) emit(ModelConfig()) else throw error
        }

    suspend fun read(): ModelConfig = config.first()

    suspend fun save(config: ModelConfig) {
        store.edit { values ->
            values[modelKey] = config.modelId
            values[thinkingKey] = config.thinkingLevel.name
        }
    }
}
