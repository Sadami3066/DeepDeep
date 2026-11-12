package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.sdk.ai.auth.Credential
import io.github.whywood.deepdeep.sdk.ai.auth.CredentialInfo
import io.github.whywood.deepdeep.sdk.ai.auth.CredentialStore
import io.github.whywood.deepdeep.sdk.ai.http.HttpRequest
import io.github.whywood.deepdeep.sdk.ai.http.HttpResponse
import io.github.whywood.deepdeep.sdk.ai.http.HttpTransport
import io.github.whywood.deepdeep.sdk.coding.models.CustomModelConfig
import io.github.whywood.deepdeep.sdk.coding.models.CustomProviderConfig
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderCatalogTest {
    private val transport = object : HttpTransport {
        override suspend fun execute(request: HttpRequest): HttpResponse =
            error("network is not used by the catalog")
    }

    private val credentials = object : CredentialStore {
        override fun read(providerId: String): Credential? = null
        override fun readAll(): Map<String, Credential> = emptyMap()
        override fun readApiKey(providerId: String): String? = null
        override fun readEnv(providerId: String): Map<String, String>? = null
        override fun list(): List<CredentialInfo> = emptyList()
        override fun keys(): List<String> = emptyList()
        override fun modify(providerId: String, transform: (Credential?) -> Credential?): Credential? = null
        override fun writeApiKey(providerId: String, key: String, env: Map<String, String>?) = Unit
        override fun write(providerId: String, credential: Credential) = Unit
        override fun writeRawCredential(providerId: String, credential: JsonElement) = Unit
        override fun delete(providerId: String) = Unit
        override fun readRaw(): Map<String, JsonElement> = emptyMap()
    }

    @Test
    fun exposesAllBuiltinProviders() {
        val catalog = ProviderCatalog(transport, credentials)
        assertEquals(42, catalog.summaries.size)
        assertTrue(catalog.summaries.all { it.name.isNotBlank() })
        assertNotNull(catalog.summary(ProviderCatalog.DEFAULT_PROVIDER_ID))
    }

    @Test
    fun resolvesDefaultConfig() {
        val catalog = ProviderCatalog(transport, credentials)
        val resolved = catalog.resolveConfig(ModelConfig())
        assertEquals(ProviderCatalog.DEFAULT_PROVIDER_ID, resolved.providerId)
        assertTrue(catalog.models(resolved.providerId).any { it.id == resolved.modelId })
    }

    private fun customProviderConfig() = CustomProviderConfig(
        id = "custom-test",
        name = "Custom Test",
        baseUrl = "https://api.example.com/v1",
        api = "openai-completions",
        apiKey = "sk-test",
        models = listOf(CustomModelConfig(id = "custom-model", name = "Custom Model")),
    )

    @Test
    fun mergesCustomProviders() {
        val catalog = ProviderCatalog(transport, credentials, listOf(customProviderConfig()))
        assertEquals(43, catalog.summaries.size)
        assertEquals("Custom Test", catalog.summary("custom-test")?.name)
        assertEquals(listOf("custom-model"), catalog.models("custom-test").map { it.id })
        assertNotNull(catalog.sdkModel("custom-test", "custom-model"))
        val resolved = catalog.resolveConfig(ModelConfig(providerId = "custom-test", modelId = "custom-model"))
        assertEquals("custom-test", resolved.providerId)
        assertEquals("custom-model", resolved.modelId)
    }

    @Test
    fun keepsValidCustomProvidersWhenAnotherIsInvalid() {
        val catalog = ProviderCatalog(
            transport,
            credentials,
            listOf(
                CustomProviderConfig(id = "custom-broken", name = "Broken"),
                CustomProviderConfig(
                    id = "custom-valid",
                    name = "Valid",
                    baseUrl = "https://api.example.com/v1",
                    api = "anthropic-messages",
                    apiKey = "sk-test",
                    models = listOf(CustomModelConfig(id = "claude-test")),
                ),
            ),
        )
        assertEquals(43, catalog.summaries.size)
        assertNotNull(catalog.summary("custom-valid"))
        assertEquals(null, catalog.summary("custom-broken"))
    }

    @Test
    fun updatesCustomProvidersAtRuntime() {
        val catalog = ProviderCatalog(transport, credentials)
        assertEquals(42, catalog.summaries.size)
        assertEquals(emptyList<String>(), catalog.customSdkProviders().map { it.id })
        catalog.setCustomProviders(listOf(customProviderConfig()))
        assertEquals(43, catalog.summaries.size)
        assertEquals(listOf("custom-model"), catalog.models("custom-test").map { it.id })
        assertNotNull(catalog.sdkModel("custom-test", "custom-model"))
        assertEquals(listOf("custom-test"), catalog.customSdkProviders().map { it.id })
        assertEquals(listOf("custom-model"), catalog.customSdkProviders().single().models.map { it.id })
        catalog.setCustomProviders(emptyList())
        assertEquals(42, catalog.summaries.size)
        assertEquals(null, catalog.summary("custom-test"))
        assertEquals(null, catalog.sdkModel("custom-test", "custom-model"))
        assertEquals(emptyList<String>(), catalog.customSdkProviders().map { it.id })
    }

    private fun thinkingProviderConfig(): CustomProviderConfig = CustomProviderConfig(
        id = "custom-thinking",
        name = "Custom Thinking",
        baseUrl = "https://api.example.com/v1",
        api = "openai-completions",
        apiKey = "sk-test",
        models = listOf(
            CustomModelConfig(
                id = "levels",
                name = "Levels",
                reasoning = true,
                thinkingLevelMap = mapOf(
                    "off" to null,
                    "minimal" to null,
                    "low" to "low",
                    "medium" to null,
                    "high" to "high",
                    "xhigh" to "xhigh",
                    "max" to null,
                ),
            ),
            CustomModelConfig(id = "plain", name = "Plain", reasoning = false),
        ),
    )

    @Test
    fun exposesOnlySupportedThinkingLevelsForCustomModels() {
        val catalog = ProviderCatalog(transport, credentials, listOf(thinkingProviderConfig()))
        val levels = catalog.models("custom-thinking").single { it.id == "levels" }.thinkingLevels
        assertEquals(
            listOf(ThinkingLevel.LOW, ThinkingLevel.HIGH, ThinkingLevel.XHIGH),
            levels,
        )
        val plain = catalog.models("custom-thinking").single { it.id == "plain" }
        assertEquals(listOf(ThinkingLevel.OFF), plain.thinkingLevels)
    }

    @Test
    fun resolvesUnsupportedThinkingLevelToClosestSupported() {
        val catalog = ProviderCatalog(transport, credentials, listOf(thinkingProviderConfig()))
        val off = catalog.resolveConfig(ModelConfig("custom-thinking", "levels", ThinkingLevel.OFF))
        assertEquals(ThinkingLevel.LOW, off.thinkingLevel)
        val max = catalog.resolveConfig(ModelConfig("custom-thinking", "levels", ThinkingLevel.MAX))
        assertEquals(ThinkingLevel.XHIGH, max.thinkingLevel)
        val supported = catalog.resolveConfig(ModelConfig("custom-thinking", "levels", ThinkingLevel.HIGH))
        assertEquals(ThinkingLevel.HIGH, supported.thinkingLevel)
    }

    @Test
    fun resolvesThinkingLevelsForBuiltinModels() {
        val catalog = ProviderCatalog(transport, credentials)
        val default = catalog.resolveConfig(ModelConfig())
        assertTrue(default.thinkingLevel in catalog.models(default.providerId).first { it.id == default.modelId }.thinkingLevels)
    }
}
