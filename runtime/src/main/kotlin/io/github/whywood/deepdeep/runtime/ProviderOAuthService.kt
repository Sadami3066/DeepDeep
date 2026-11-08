package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.sdk.ai.auth.CredentialStore
import io.github.whywood.deepdeep.sdk.ai.auth.oauth.OAuthAuth
import io.github.whywood.deepdeep.sdk.ai.auth.oauth.OAuthInteraction
import io.github.whywood.deepdeep.sdk.ai.auth.oauth.OAuthLoginOptions
import io.github.whywood.deepdeep.sdk.ai.auth.oauth.bundledOAuthFlows
import io.github.whywood.deepdeep.sdk.ai.auth.oauth.loginAndStoreOAuthCredential
import io.github.whywood.deepdeep.sdk.ai.http.HttpTransport

class ProviderOAuthService(
    private val credentials: CredentialStore,
    private val transport: HttpTransport,
    private val flows: Map<String, OAuthAuth> = bundledOAuthFlows(),
    private val deviceId: suspend () -> String? = { null },
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
            options = options ?: OAuthLoginOptions(
                deviceId = deviceId(),
                agentName = AGENT_NAME,
            ),
        )
        Unit
    }

    suspend fun logout(providerId: String) {
        credentials.delete(providerId)
    }

    private companion object {
        const val AGENT_NAME = "DeepDeep"
    }
}
