package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.sdk.ai.Context
import io.github.whywood.deepdeep.sdk.ai.Model
import io.github.whywood.deepdeep.sdk.ai.Models
import io.github.whywood.deepdeep.sdk.ai.PlainTextContent
import io.github.whywood.deepdeep.sdk.ai.Provider
import io.github.whywood.deepdeep.sdk.ai.StreamOptions
import io.github.whywood.deepdeep.sdk.ai.TextContent
import io.github.whywood.deepdeep.sdk.ai.UserMessage
import io.github.whywood.deepdeep.sdk.ai.auth.CredentialStore
import io.github.whywood.deepdeep.sdk.ai.auth.oauth.bundledOAuthFlows
import io.github.whywood.deepdeep.sdk.ai.http.HttpTransport
import io.github.whywood.deepdeep.sdk.ai.providers.builtinProviders
import java.util.UUID

class ProviderCatalog(
    transport: HttpTransport,
    credentials: CredentialStore,
) : ProviderDirectory {
    private val providerMap: Map<String, Provider> = builtinProviders(transport, credentials)
        .associateBy { it.id }
    private val registry = Models(providerMap.toMutableMap())

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

    suspend fun complete(providerId: String, modelId: String, prompt: String): String? {
        val provider = providerMap[providerId] ?: run {
            android.util.Log.w("SessionTitle", "provider not found: $providerId")
            return null
        }
        val model = provider.models.firstOrNull { it.id == modelId } ?: run {
            android.util.Log.w("SessionTitle", "model not found: $providerId/$modelId")
            return null
        }
        val message = registry.complete(
            model = model,
            context = Context(
                messages = listOf(UserMessage(PlainTextContent(prompt), System.currentTimeMillis())),
            ),
            options = StreamOptions(
                maxTokens = 4096,
                temperature = 0.3,
                timeoutMs = 20_000,
                sessionId = UUID.randomUUID().toString(),
            ),
        )
        val text = message.content
            .mapNotNull { (it as? TextContent)?.text }
            .joinToString("")
            .trim()
        if (text.isBlank()) {
            android.util.Log.w("SessionTitle", "empty completion $providerId/$modelId stop=${message.stopReason} error=${message.errorMessage} parts=${message.content.map { it::class.simpleName }}")
            return null
        }
        return text
    }

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
