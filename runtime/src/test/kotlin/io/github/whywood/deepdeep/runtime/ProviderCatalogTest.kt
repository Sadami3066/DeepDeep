package io.github.whywood.deepdeep.runtime

import dev.pi.ai.auth.Credential
import dev.pi.ai.auth.CredentialInfo
import dev.pi.ai.auth.CredentialStore
import dev.pi.ai.http.HttpRequest
import dev.pi.ai.http.HttpResponse
import dev.pi.ai.http.HttpTransport
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
}
