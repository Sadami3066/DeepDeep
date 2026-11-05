package io.github.whywood.deepdeep.core

import io.github.whywood.deepdeep.runtime.ModelConfig
import io.github.whywood.deepdeep.runtime.ModelOption
import io.github.whywood.deepdeep.runtime.ModelSettings
import io.github.whywood.deepdeep.runtime.ProviderDirectory
import io.github.whywood.deepdeep.runtime.ProviderDefaults
import io.github.whywood.deepdeep.runtime.ProviderSummary
import io.github.whywood.deepdeep.runtime.ThinkingLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelSelectionTest {
    private val catalog = FakeCatalog(
        linkedMapOf(
            "deepseek" to listOf(option("deepseek", "deepseek-v4.1-flash"), option("deepseek", "deepseek-v4.1-pro")),
            "opencode" to listOf(option("opencode", "opencode-1")),
            "anthropic" to listOf(option("anthropic", "claude")),
        ),
    )
    private val defaultConfig = ModelConfig("deepseek", "deepseek-v4.1-flash", ThinkingLevel.OFF)

    @Test
    fun resolveReturnsNullWithoutConfiguredProviders() {
        assertNull(resolve(defaultConfig, emptySet(), catalog))
    }

    @Test
    fun resolveKeepsConfiguredProvider() {
        assertNull(resolve(defaultConfig, setOf("deepseek"), catalog))
    }

    @Test
    fun resolveSwitchesToOnlyConfiguredProvider() {
        assertEquals(
            ModelConfig("opencode", "opencode-1", ThinkingLevel.OFF),
            resolve(defaultConfig, setOf("opencode"), catalog),
        )
    }

    @Test
    fun resolvePrefersCatalogOrder() {
        assertEquals(
            ModelConfig("opencode", "opencode-1", ThinkingLevel.OFF),
            resolve(defaultConfig, setOf("anthropic", "opencode"), catalog),
        )
    }

    @Test
    fun resolveSkipsConfiguredProvidersMissingFromCatalog() {
        assertEquals(
            ModelConfig("anthropic", "claude", ThinkingLevel.OFF),
            resolve(defaultConfig, setOf("ghost", "anthropic"), catalog),
        )
        assertNull(resolve(defaultConfig, setOf("ghost"), catalog))
    }

    @Test
    fun ensureSavesResolvedConfig() = runTest {
        val settings = FakeSettings(defaultConfig)
        val selection = ModelSelection(catalog, settings)

        val resolved = selection.ensure(setOf("opencode"))

        assertEquals(ModelConfig("opencode", "opencode-1", ThinkingLevel.OFF), resolved)
        assertEquals(listOf(ModelConfig("opencode", "opencode-1", ThinkingLevel.OFF)), settings.saved)
    }

    @Test
    fun ensureLeavesValidConfigUntouched() = runTest {
        val settings = FakeSettings(defaultConfig)
        val selection = ModelSelection(catalog, settings)

        assertNull(selection.ensure(setOf("deepseek")))
        assertNull(selection.ensure(emptySet()))
        assertTrue(settings.saved.isEmpty())
    }
}

private fun option(providerId: String, id: String) =
    ModelOption(providerId, id, id, listOf(ThinkingLevel.OFF))

private class FakeCatalog(
    private val providerModels: LinkedHashMap<String, List<ModelOption>>,
) : ProviderDirectory {
    override val summaries: List<ProviderSummary> = providerModels.keys.map { providerId ->
        ProviderSummary(
            id = providerId,
            name = providerId,
            modelCount = providerModels.getValue(providerId).size,
            baseUrl = null,
            hasOAuth = false,
            supportsApiKey = true,
        )
    }

    override fun summary(providerId: String): ProviderSummary? = summaries.firstOrNull { it.id == providerId }

    override fun models(providerId: String): List<ModelOption> = providerModels[providerId].orEmpty()

    override fun resolveConfig(config: ModelConfig): ModelConfig {
        val provider = if (config.providerId in providerModels) config.providerId else ProviderDefaults.DEFAULT_PROVIDER_ID
        val options = models(provider)
        val model = options.firstOrNull { it.id == config.modelId } ?: options.first()
        val thinking = config.thinkingLevel.takeIf { it in model.thinkingLevels } ?: ThinkingLevel.OFF
        return ModelConfig(provider, model.id, thinking)
    }

    override fun sdkModel(providerId: String, modelId: String): dev.pi.ai.Model? = null

    override fun modelReference(providerId: String, modelId: String): String = "$providerId/$modelId"
}

private class FakeSettings(initial: ModelConfig) : ModelSettings {
    private val state = MutableStateFlow(initial)
    val saved = mutableListOf<ModelConfig>()

    override val config: Flow<ModelConfig> = state

    override suspend fun read(): ModelConfig = state.value

    override suspend fun save(config: ModelConfig) {
        saved += config
        state.value = config
    }
}
