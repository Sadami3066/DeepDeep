package io.github.whywood.deepdeep.runtime

data class ModelConfig(
    val providerId: String = ProviderDefaults.DEFAULT_PROVIDER_ID,
    val modelId: String = ProviderDefaults.DEFAULT_MODEL_ID,
    val thinkingLevel: ThinkingLevel = ThinkingLevel.OFF,
)
