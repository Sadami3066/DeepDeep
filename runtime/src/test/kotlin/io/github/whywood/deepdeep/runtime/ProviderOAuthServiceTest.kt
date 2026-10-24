package io.github.whywood.deepdeep.runtime

import dev.pi.ai.auth.Credential
import dev.pi.ai.auth.CredentialInfo
import dev.pi.ai.auth.CredentialStore
import dev.pi.ai.auth.OAuthCredential
import dev.pi.ai.auth.oauth.OAuthAuth
import dev.pi.ai.auth.oauth.OAuthCredential as OAuthToken
import dev.pi.ai.auth.oauth.OAuthInteraction
import dev.pi.ai.auth.oauth.OAuthLoginOptions
import dev.pi.ai.http.HttpRequest
import dev.pi.ai.http.HttpResponse
import dev.pi.ai.http.HttpTransport
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderOAuthServiceTest {
    private val transport = object : HttpTransport {
        override suspend fun execute(request: HttpRequest): HttpResponse = error("network is not used")
    }

    private class RecordingStore : CredentialStore {
        var raw: JsonElement? = null

        override fun read(providerId: String): Credential? = null
        override fun readAll(): Map<String, Credential> = emptyMap()
        override fun readApiKey(providerId: String): String? = null
        override fun readEnv(providerId: String): Map<String, String>? = null
        override fun list(): List<CredentialInfo> = emptyList()
        override fun keys(): List<String> = emptyList()
        override fun modify(providerId: String, transform: (Credential?) -> Credential?): Credential? = null
        override fun writeApiKey(providerId: String, key: String, env: Map<String, String>?) = Unit
        override fun write(providerId: String, credential: Credential) = Unit
        override fun writeRawCredential(providerId: String, credential: JsonElement) {
            raw = credential
        }

        override fun delete(providerId: String) = Unit
        override fun readRaw(): Map<String, JsonElement> = raw?.let { mapOf("fake" to it) } ?: emptyMap()
    }

    private val fakeFlow = object : OAuthAuth {
        override val id: String = "fake"
        override val name: String = "Fake"
        override val loginLabel: String = "Sign in with Fake"

        override suspend fun login(
            interaction: OAuthInteraction,
            transport: HttpTransport,
            options: OAuthLoginOptions?,
        ): OAuthToken = OAuthToken(refresh = "refresh", access = "access", expires = 1_800_000_000_000L)

        override suspend fun refresh(
            credential: OAuthToken,
            transport: HttpTransport,
            signal: dev.pi.ai.RunSignal?,
        ): OAuthToken = credential

        override suspend fun toAuth(credential: OAuthToken): dev.pi.ai.auth.ResolvedAuth =
            dev.pi.ai.auth.ResolvedAuth(apiKey = credential.access, source = "oauth")
    }

    private val interaction = object : OAuthInteraction {
        override suspend fun openUrl(url: String, instructions: String?) = Unit
        override suspend fun showMessage(message: String) = Unit
        override suspend fun prompt(message: String, placeholder: String?, secret: Boolean): String = ""
        override suspend fun select(message: String, options: List<dev.pi.ai.auth.oauth.OAuthSelectOption>): String =
            options.first().id
    }

    @Test
    fun loginStoresRawOAuthCredential() = runTest {
        val store = RecordingStore()
        val service = ProviderOAuthService(store, transport, mapOf("fake" to fakeFlow))
        assertTrue(service.supports("fake"))
        assertFalse(service.supports("deepseek"))
        val result = service.login("fake", interaction)
        assertTrue(result.isSuccess)
        val raw = store.raw as kotlinx.serialization.json.JsonObject
        assertEquals("access", (raw["access"] as kotlinx.serialization.json.JsonPrimitive).content)
    }

    @Test
    fun loginFailsForUnknownProvider() = runTest {
        val service = ProviderOAuthService(RecordingStore(), transport, emptyMap())
        val result = service.login("deepseek", interaction)
        assertTrue(result.isFailure)
    }

    @Test
    fun logoutDeletesCredential() = runTest {
        val store = RecordingStore()
        val service = ProviderOAuthService(store, transport, mapOf("fake" to fakeFlow))
        service.logout("fake")
        assertTrue(store.raw == null)
    }
}
