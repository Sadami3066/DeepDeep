package io.github.whywood.deepdeep.plugin.host.extensions.data

import io.github.whywood.deepdeep.data.CustomProviderEntry
import io.github.whywood.deepdeep.data.CustomProviderStore
import io.github.whywood.deepdeep.plugin.api.CredentialService
import io.github.whywood.deepdeep.plugin.api.PluginExperimentalApi
import io.github.whywood.deepdeep.plugin.api.PluginMcpToken
import io.github.whywood.deepdeep.plugin.api.PluginProviderCredential
import io.github.whywood.deepdeep.runtime.McpConfigStore
import io.github.whywood.deepdeep.runtime.ProviderAuthKind
import io.github.whywood.deepdeep.runtime.ProviderCatalog
import io.github.whywood.deepdeep.runtime.ProviderSummary
import io.github.whywood.deepdeep.runtime.SdkCredentialStore
import io.github.whywood.deepdeep.sdk.mcp.config.McpServerConfig
import io.github.whywood.deepdeep.sdk.mcp.oauth.FileMcpTokenStore
import io.github.whywood.deepdeep.sdk.mcp.oauth.McpOAuthState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

internal fun mergedProviderCredentials(
    custom: List<CustomProviderEntry>,
    catalog: List<ProviderSummary>,
    apiKey: (String) -> String?,
    authKind: (String) -> String?,
): List<PluginProviderCredential> {
    val result = LinkedHashMap<String, PluginProviderCredential>()
    custom.forEach { entry ->
        result[entry.id] = PluginProviderCredential(
            id = entry.id,
            name = entry.name,
            baseUrl = entry.baseUrl.takeIf { it.isNotBlank() },
            apiKey = entry.apiKey.takeIf { it.isNotBlank() },
            authKind = "api-key",
        )
    }
    catalog.forEach { summary ->
        if (result.containsKey(summary.id)) return@forEach
        result[summary.id] = PluginProviderCredential(
            id = summary.id,
            name = summary.name,
            baseUrl = summary.baseUrl?.takeIf { it.isNotBlank() },
            apiKey = apiKey(summary.id)?.takeIf { it.isNotBlank() },
            authKind = authKind(summary.id),
        )
    }
    return result.values.toList()
}

internal fun pluginMcpToken(server: String, url: String, state: McpOAuthState?): PluginMcpToken? {
    val tokens = state?.tokens ?: return null
    val accessToken = tokens.accessToken.takeIf { it.isNotBlank() } ?: return null
    return PluginMcpToken(
        server = server,
        url = url,
        accessToken = accessToken,
        refreshToken = tokens.refreshToken?.takeIf { it.isNotBlank() },
        tokenType = tokens.tokenType.takeIf { it.isNotBlank() },
        expiresAtMs = state.tokensExpireAt,
    )
}

internal fun hostCredentialValues(installationId: String?, adbToken: String?): Map<String, String> = buildMap {
    installationId?.takeIf { it.isNotBlank() }?.let { put(HOST_VALUE_INSTALLATION_ID, it) }
    adbToken?.takeIf { it.isNotBlank() }?.let { put(HOST_VALUE_ADB_TOKEN, it) }
}

internal const val HOST_VALUE_INSTALLATION_ID = "installationId"

internal const val HOST_VALUE_ADB_TOKEN = "adbToken"

@PluginExperimentalApi
internal class CredentialPluginServiceImpl(
    private val credentialStore: SdkCredentialStore?,
    private val customProviders: CustomProviderStore?,
    private val providerCatalog: ProviderCatalog?,
    private val mcpConfig: McpConfigStore?,
    private val mcpTokenStore: FileMcpTokenStore?,
    private val installationId: suspend () -> String,
    private val adbToken: () -> String?,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CredentialService {
    @Volatile
    private var cachedInstallationId: String? = null

    @Volatile
    private var cachedMcpTokens: List<PluginMcpToken> = emptyList()

    @Volatile
    private var mcpTokensLoadedAt: Long = 0L

    init {
        scope.launch {
            cachedInstallationId = runCatching { installationId() }.getOrNull()
        }
        scope.launch { refreshMcpTokens() }
    }

    override fun providers(): List<PluginProviderCredential> = mergedProviderCredentials(
        custom = customProviders?.entries?.value.orEmpty(),
        catalog = providerCatalog?.summaries.orEmpty(),
        apiKey = { providerId -> credentialStore?.cachedApiKey(providerId) },
        authKind = { providerId ->
            when (credentialStore?.kindOf(providerId)) {
                ProviderAuthKind.API_KEY -> "api-key"
                ProviderAuthKind.OAUTH -> "oauth"
                null -> null
            }
        },
    )

    override fun provider(id: String): PluginProviderCredential? = providers().firstOrNull { it.id == id }

    override fun mcpTokens(): List<PluginMcpToken> {
        if (System.currentTimeMillis() - mcpTokensLoadedAt > MCP_TOKENS_TTL_MS) {
            runCatching {
                runBlocking(ioDispatcher) { refreshMcpTokens() }
            }
        }
        return cachedMcpTokens
    }

    override fun mcpToken(server: String): PluginMcpToken? = mcpTokens().firstOrNull { it.server == server }

    override fun hostValues(): Map<String, String> = hostCredentialValues(cachedInstallationId, adbToken())

    private suspend fun refreshMcpTokens() {
        val config = mcpConfig ?: return
        val store = mcpTokenStore ?: return
        val tokens = runCatching {
            config.loadServers().entries.mapNotNull { (server, serverConfig) ->
                val http = serverConfig as? McpServerConfig.Http ?: return@mapNotNull null
                val state = runCatching { store.read(server, http.url) }.getOrNull() ?: return@mapNotNull null
                pluginMcpToken(server, http.url, state)
            }
        }.getOrDefault(emptyList())
        cachedMcpTokens = tokens
        mcpTokensLoadedAt = System.currentTimeMillis()
    }

    private companion object {
        const val MCP_TOKENS_TTL_MS = 10_000L
    }
}
