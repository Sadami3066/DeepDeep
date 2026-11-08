package io.github.whywood.deepdeep.runtime

interface ProviderDirectory {
    val summaries: List<ProviderSummary>

    fun summary(providerId: String): ProviderSummary?

    fun models(providerId: String): List<ModelOption>

    fun resolveConfig(config: ModelConfig): ModelConfig

    fun sdkModel(providerId: String, modelId: String): io.github.whywood.deepdeep.sdk.ai.Model?

    fun modelReference(providerId: String, modelId: String): String
}
