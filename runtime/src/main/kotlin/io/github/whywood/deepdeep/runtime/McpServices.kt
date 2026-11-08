package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.sdk.ai.RunSignal
import io.github.whywood.deepdeep.sdk.ai.http.HttpTransport
import io.github.whywood.deepdeep.sdk.mcp.McpAuthRequiredException
import io.github.whywood.deepdeep.sdk.mcp.McpClient
import io.github.whywood.deepdeep.sdk.mcp.McpClientOptions
import io.github.whywood.deepdeep.sdk.mcp.McpRequestOptions
import io.github.whywood.deepdeep.sdk.mcp.config.McpServerConfig
import io.github.whywood.deepdeep.sdk.mcp.oauth.FileMcpTokenStore
import io.github.whywood.deepdeep.sdk.mcp.oauth.McpAuthProvider
import io.github.whywood.deepdeep.sdk.mcp.oauth.McpOAuthInteraction
import io.github.whywood.deepdeep.sdk.mcp.oauth.McpOAuthOptions
import io.github.whywood.deepdeep.sdk.mcp.oauth.McpOAuthProvider
import io.github.whywood.deepdeep.sdk.mcp.oauth.McpTokenStore
import io.github.whywood.deepdeep.sdk.mcp.transport.StreamableHttpTransport
import io.github.whywood.deepdeep.sdk.mcp.transport.StreamableHttpTransportOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class McpProbeState {
    OK,
    AUTH_REQUIRED,
    FAILED,
    UNSUPPORTED,
}

data class McpProbeResult(
    val state: McpProbeState,
    val toolCount: Int = 0,
    val serverName: String? = null,
    val error: String? = null,
)

class McpServerProbe(
    private val transport: HttpTransport,
    private val tokenStore: McpTokenStore,
) {
    suspend fun probe(
        name: String,
        config: McpServerConfig,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): McpProbeResult = withContext(Dispatchers.IO) {
        if (config !is McpServerConfig.Http) {
            return@withContext McpProbeResult(McpProbeState.UNSUPPORTED)
        }
        val httpTransport = StreamableHttpTransport(
            http = transport,
            options = StreamableHttpTransportOptions(
                url = config.url,
                headers = config.headers,
                authProvider = config.httpAuthProvider(name, transport, tokenStore),
                requestTimeoutMs = timeoutMs,
            ),
        )
        val client = McpClient(
            serverName = name,
            transport = httpTransport,
            options = McpClientOptions(requestTimeoutMs = timeoutMs),
        )
        try {
            val initialized = client.initialize()
            val tools = client.listTools(McpRequestOptions(timeoutMs = timeoutMs))
            McpProbeResult(
                state = McpProbeState.OK,
                toolCount = tools.size,
                serverName = initialized.serverInfo?.name,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: McpAuthRequiredException) {
            McpProbeResult(McpProbeState.AUTH_REQUIRED)
        } catch (error: Exception) {
            McpProbeResult(McpProbeState.FAILED, error = error.message ?: error::class.java.simpleName)
        } finally {
            runCatching { client.close() }
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 15_000L
    }
}

class McpPluginBridge(
    private val transport: HttpTransport,
    private val tokenStore: McpTokenStore,
) {
    suspend fun listTools(
        name: String,
        config: McpServerConfig,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): List<io.github.whywood.deepdeep.sdk.mcp.protocol.McpTool> = withContext(Dispatchers.IO) {
        if (config !is McpServerConfig.Http) return@withContext emptyList()
        withClient(name, config, timeoutMs) { client ->
            client.listTools(McpRequestOptions(timeoutMs = timeoutMs))
        }
    }

    suspend fun callTool(
        name: String,
        config: McpServerConfig,
        tool: String,
        arguments: kotlinx.serialization.json.JsonObject,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): io.github.whywood.deepdeep.sdk.mcp.protocol.McpCallResult = withContext(Dispatchers.IO) {
        if (config !is McpServerConfig.Http) {
            return@withContext io.github.whywood.deepdeep.sdk.mcp.protocol.McpCallResult(emptyList())
        }
        withClient(name, config, timeoutMs) { client ->
            client.callTool(tool, arguments, McpRequestOptions(timeoutMs = timeoutMs))
        }
    }

    private suspend fun <T> withClient(
        name: String,
        config: McpServerConfig.Http,
        timeoutMs: Long,
        block: suspend (McpClient) -> T,
    ): T {
        val httpTransport = StreamableHttpTransport(
            http = transport,
            options = StreamableHttpTransportOptions(
                url = config.url,
                headers = config.headers,
                authProvider = config.httpAuthProvider(name, transport, tokenStore),
                requestTimeoutMs = timeoutMs,
            ),
        )
        val client = McpClient(
            serverName = name,
            transport = httpTransport,
            options = McpClientOptions(requestTimeoutMs = timeoutMs),
        )
        return try {
            client.initialize()
            block(client)
        } finally {
            runCatching { client.close() }
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 15_000L
    }
}

class McpOAuthService(
    private val transport: HttpTransport,
    private val tokenStore: McpTokenStore,
) {
    suspend fun hasToken(server: String, config: McpServerConfig.Http): Boolean =
        withContext(Dispatchers.IO) {
            tokenStore.read(server, config.url)?.tokens?.accessToken?.isNotBlank() == true
        }

    suspend fun signIn(
        server: String,
        config: McpServerConfig.Http,
        interaction: McpOAuthInteraction,
        redirectUri: String,
        signal: RunSignal? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val provider = McpOAuthProvider(
                options = config.oauthOptions(server),
                transport = transport,
                tokenStore = tokenStore,
                onChallenge = { challenge ->
                    if (challenge.error == "insufficient_scope") Unit
                },
            )
            val tokens = provider.signIn(interaction, redirectUri, signal)
            if (tokens.accessToken.isEmpty()) {
                throw IllegalStateException("OAuth server returned an empty access token")
            }
        }
    }

    suspend fun signOut(server: String, config: McpServerConfig.Http) {
        withContext(Dispatchers.IO) { tokenStore.clear(server, config.url) }
    }
}

internal fun McpServerConfig.Http.oauthOptions(serverName: String): McpOAuthOptions = McpOAuthOptions(
    serverName = serverName,
    serverUrl = url,
    scope = oauth?.scope,
    clientId = oauth?.clientId,
    clientSecret = oauth?.clientSecret,
    callbackPort = oauth?.callbackPort,
    callbackUrl = oauth?.callbackUrl,
    clientName = oauth?.clientName ?: "DeepDeep",
    authorizationServerMetadataUrl = oauth?.authServerMetadataUrl,
)

internal fun McpServerConfig.Http.httpAuthProvider(
    serverName: String,
    transport: HttpTransport,
    tokenStore: McpTokenStore,
): McpAuthProvider? {
    val hasAuthorization = headers.keys.any { it.equals("authorization", ignoreCase = true) }
    if (hasAuthorization) return null
    return McpOAuthProvider(
        options = oauthOptions(serverName),
        transport = transport,
        tokenStore = tokenStore,
        onChallenge = { challenge ->
            if (challenge.error == "insufficient_scope") Unit
        },
    )
}
