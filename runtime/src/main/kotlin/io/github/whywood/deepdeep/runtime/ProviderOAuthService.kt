package io.github.whywood.deepdeep.runtime

import dev.pi.ai.auth.CredentialStore
import dev.pi.ai.auth.oauth.OAuthAuth
import dev.pi.ai.auth.oauth.OAuthInteraction
import dev.pi.ai.auth.oauth.OAuthLoginOptions
import dev.pi.ai.auth.oauth.bundledOAuthFlows
import dev.pi.ai.auth.oauth.loginAndStoreOAuthCredential
import dev.pi.ai.http.HttpTransport

class ProviderOAuthService(
    private val credentials: CredentialStore,
    private val transport: HttpTransport,
    private val flows: Map<String, OAuthAuth> = bundledOAuthFlows(),
) {

    fun supports(providerId: String): Boolean = flows.containsKey(providerId)

    fun flow(providerId: String): OAuthAuth? = flows[providerId]

    fun loginLabel(providerId: String): String? =
        flows[providerId]?.loginLabel ?: flows[providerId]?.name

    suspend fun login(
        providerId: String,
        interaction: OAuthInteraction,
        options: OAuthLoginOptions? = null,
    ): Result<Unit> = runCatching {
        val flow = flows[providerId]
            ?: throw IllegalArgumentException("Provider $providerId does not support OAuth")
        loginAndStoreOAuthCredential(
            providerId = providerId,
            flow = flow,
            store = credentials,
            transport = transport,
            interaction = interaction,
            options = options,
        )
        Unit
    }

    suspend fun logout(providerId: String) {
        credentials.delete(providerId)
    }
}
