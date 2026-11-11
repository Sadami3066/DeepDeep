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
        catalog.setCustomProviders(listOf(customProviderConfig()))
        assertEquals(43, catalog.summaries.size)
        assertEquals(listOf("custom-model"), catalog.models("custom-test").map { it.id })
        assertNotNull(catalog.sdkModel("custom-test", "custom-model"))
        catalog.setCustomProviders(emptyList())
        assertEquals(42, catalog.summaries.size)
        assertEquals(null, catalog.summary("custom-test"))
        assertEquals(null, catalog.sdkModel("custom-test", "custom-model"))
    }
}
