package io.github.whywood.deepdeep.runtime

import dev.pi.ai.Model
import dev.pi.ai.Provider
import dev.pi.ai.auth.CredentialStore
import dev.pi.ai.auth.oauth.bundledOAuthFlows
import dev.pi.ai.http.HttpTransport
import dev.pi.ai.providers.builtinProviders

class ProviderCatalog(
    transport: HttpTransport,
    credentials: CredentialStore,
) : ProviderDirectory {
    private val providerMap: Map<String, Provider> = builtinProviders(transport, credentials)
        .associateBy { it.id }

    override val summaries: List<ProviderSummary> = providerMap.values.map { provider ->
        ProviderSummary(
            id = provider.id,
            name = provider.name,
            modelCount = provider.models.size,
            baseUrl = provider.baseUrl,
            hasOAuth = provider.id in OAUTH_PROVIDERS,
            supportsApiKey = provider.id !in KEYLESS_PROVIDERS,
        )
    }

    override fun summary(providerId: String): ProviderSummary? = summaries.firstOrNull { it.id == providerId }

    override fun models(providerId: String): List<ModelOption> =
        providerMap[providerId]?.models.orEmpty().map { model -> model.toOption(providerId) }

    fun defaultModel(providerId: String): ModelOption? = models(providerId).firstOrNull()

    fun model(providerId: String, modelId: String): ModelOption? =
        models(providerId).firstOrNull { it.id == modelId }

    override fun sdkModel(providerId: String, modelId: String): Model? =
        providerMap[providerId]?.models?.firstOrNull { it.id == modelId }

    override fun resolveConfig(config: ModelConfig): ModelConfig {
        val provider = if (config.providerId in providerMap) config.providerId else DEFAULT_PROVIDER_ID
        val models = models(provider)
        val model = models.firstOrNull { it.id == config.modelId }
            ?: models.firstOrNull()
            ?: ModelOption(provider, config.modelId, config.modelId, listOf(ThinkingLevel.OFF))
        val thinking = config.thinkingLevel.takeIf { it in model.thinkingLevels } ?: ThinkingLevel.OFF
        return ModelConfig(provider, model.id, thinking)
    }

    override fun modelReference(providerId: String, modelId: String): String = "$providerId/$modelId"

    fun modelsForConfiguredProviders(): List<ModelOption> =
        summaries.flatMap { models(it.id) }

    private fun Model.toOption(providerId: String): ModelOption = ModelOption(
        providerId = providerId,
        id = id,
        displayName = name,
        thinkingLevels = thinkingLevels(this),
    )

    private fun thinkingLevels(model: Model): List<ThinkingLevel> {
        if (!model.reasoning) return listOf(ThinkingLevel.OFF)
        val levels = model.thinkingLevelMap.orEmpty().filterValues { it != null }.keys
        return buildList {
            add(ThinkingLevel.OFF)
            if ("low" in levels) add(ThinkingLevel.LOW)
            if ("medium" in levels) add(ThinkingLevel.MEDIUM)
            if ("high" in levels) add(ThinkingLevel.HIGH)
            if ("max" in levels) add(ThinkingLevel.MAX)
        }.distinct()
    }

    companion object {
        const val DEFAULT_PROVIDER_ID = ProviderDefaults.DEFAULT_PROVIDER_ID
        const val DEFAULT_MODEL_ID = ProviderDefaults.DEFAULT_MODEL_ID

        private val OAUTH_PROVIDERS = bundledOAuthFlows().keys
        private val KEYLESS_PROVIDERS = setOf(
            "amazon-bedrock",
            "github-copilot",
            "openai-codex",
            "kimi-coding",
            "meta",
            "radius",
        )
    }
}
