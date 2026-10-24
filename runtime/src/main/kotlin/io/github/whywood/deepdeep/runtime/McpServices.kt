package io.github.whywood.deepdeep.runtime

import dev.pi.ai.RunSignal
import dev.pi.ai.http.HttpTransport
import dev.pi.mcp.McpAuthRequiredException
import dev.pi.mcp.McpClient
import dev.pi.mcp.McpClientOptions
import dev.pi.mcp.McpRequestOptions
import dev.pi.mcp.config.McpServerConfig
import dev.pi.mcp.oauth.FileMcpTokenStore
import dev.pi.mcp.oauth.McpAuthProvider
import dev.pi.mcp.oauth.McpOAuthInteraction
import dev.pi.mcp.oauth.McpOAuthOptions
import dev.pi.mcp.oauth.McpOAuthProvider
import dev.pi.mcp.oauth.McpTokenStore
import dev.pi.mcp.transport.StreamableHttpTransport
import dev.pi.mcp.transport.StreamableHttpTransportOptions
import kotlinx.coroutines.CancellationException

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
    ): McpProbeResult {
        if (config !is McpServerConfig.Http) {
            return McpProbeResult(McpProbeState.UNSUPPORTED)
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
        return try {
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

class McpOAuthService(
    private val transport: HttpTransport,
    private val tokenStore: McpTokenStore,
) {
    suspend fun hasToken(server: String, config: McpServerConfig.Http): Boolean =
        tokenStore.read(server, config.url)?.tokens?.accessToken?.isNotBlank() == true

    suspend fun signIn(
        server: String,
        config: McpServerConfig.Http,
        interaction: McpOAuthInteraction,
        redirectUri: String,
        signal: RunSignal? = null,
    ): Result<Unit> = runCatching {
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

    suspend fun signOut(server: String, config: McpServerConfig.Http) {
        tokenStore.clear(server, config.url)
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
