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
    val providerId: String = ProviderCatalog.DEFAULT_PROVIDER_ID,
    val modelId: String = ProviderCatalog.DEFAULT_MODEL_ID,
    val thinkingLevel: ThinkingLevel = ThinkingLevel.OFF,
)

class ModelConfigStore(context: Context) : ModelSettings {
    private val store = context.applicationContext.modelDataStore
    private val providerKey = stringPreferencesKey("provider_id")
    private val modelKey = stringPreferencesKey("model_id")
    private val thinkingKey = stringPreferencesKey("thinking_level")

    override val config: Flow<ModelConfig> = store.data
        .map { values ->
            val providerId = values[providerKey]?.takeIf { it.isNotBlank() }
                ?: ProviderCatalog.DEFAULT_PROVIDER_ID
            val modelId = values[modelKey]?.takeIf { it.isNotBlank() }
                ?: ProviderCatalog.DEFAULT_MODEL_ID
            val thinking = runCatching {
                ThinkingLevel.valueOf(values[thinkingKey] ?: ThinkingLevel.OFF.name)
            }.getOrDefault(ThinkingLevel.OFF)
            ModelConfig(providerId = providerId, modelId = modelId, thinkingLevel = thinking)
        }
        .catch { error ->
            if (error is IOException) emit(ModelConfig()) else throw error
        }

    override suspend fun read(): ModelConfig = config.first()

    override suspend fun save(config: ModelConfig) {
        store.edit { values ->
            values[providerKey] = config.providerId
            values[modelKey] = config.modelId
            values[thinkingKey] = config.thinkingLevel.name
        }
    }
}
