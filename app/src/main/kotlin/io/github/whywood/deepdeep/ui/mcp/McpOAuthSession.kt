package io.github.whywood.deepdeep.ui.mcp

import io.github.whywood.deepdeep.sdk.ai.RunSignal
import io.github.whywood.deepdeep.sdk.ai.auth.oauth.OAuthCallbackServer
import io.github.whywood.deepdeep.sdk.ai.auth.oauth.OAuthCallbackServerOptions
import io.github.whywood.deepdeep.sdk.ai.auth.oauth.startOAuthCallbackServerOrNull
import io.github.whywood.deepdeep.sdk.mcp.oauth.McpOAuthInteraction
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

const val MCP_CALLBACK_PATH = "/callback"

const val MCP_FALLBACK_REDIRECT_URI = "http://127.0.0.1:53682/callback"

private const val MCP_CALLBACK_TIMEOUT_MS = 5 * 60_000L

enum class McpOAuthError {
    INVALID_CALLBACK,
}

data class McpOAuthUiState(
    val waiting: Boolean = false,
    val error: McpOAuthError? = null,
    val manualPrompt: Boolean = false,
    val authorizationUrl: String? = null,
)

class McpOAuthSession(
    val serverName: String,
    val url: String,
    private val onOpenUrl: (String) -> Unit,
) : McpOAuthInteraction {
    private val mutableState = MutableStateFlow(McpOAuthUiState())
    val state: StateFlow<McpOAuthUiState> = mutableState.asStateFlow()

    private var manualCallback = CompletableDeferred<String>()
    private var callback: OAuthCallbackServer<String>? = null
    private var cancelled = false

    val redirectUri: String
        get() = callback?.redirectUri ?: MCP_FALLBACK_REDIRECT_URI

    fun begin() {
        cancelled = false
        manualCallback = CompletableDeferred()
        mutableState.value = McpOAuthUiState()
    }

    fun attachCallback(server: OAuthCallbackServer<String>?) {
        callback = server
    }

    fun reopen() {
        mutableState.value.authorizationUrl?.let(onOpenUrl)
    }

    override suspend fun authorize(url: String, redirectUri: String): String {
        mutableState.update {
            it.copy(waiting = true, error = null, manualPrompt = false, authorizationUrl = url)
        }
        onOpenUrl(url)
        return try {
            awaitCallback(callback, null)
        } finally {
            mutableState.update { it.copy(waiting = false, manualPrompt = false) }
        }
    }

    suspend fun awaitCallback(callback: OAuthCallbackServer<String>?, signal: RunSignal? = null): String {
        signal?.throwIfAborted()
        if (callback != null) {
            val removeAbort = signal?.onAbort { callback.cancel() }
            val result = try {
                callback.wait()
            } catch (cancelledError: CancellationException) {
                throw cancelledError
            } catch (_: Exception) {
                null
            } finally {
                removeAbort?.invoke()
            }
            if (result != null) return result
            if (signal?.isAborted == true) throw CancellationException("MCP sign-in aborted")
        }
        if (cancelled) throw CancellationException("MCP sign-in cancelled")
        mutableState.update { it.copy(manualPrompt = true, waiting = false) }
        return manualCallback.await()
    }

    fun submitManualCallback(text: String): Boolean {
        val value = text.trim()
        val hasQuery = value.substringAfter('?', "").isNotEmpty()
        val schemeOk = value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true)
        if (!schemeOk || !hasQuery) {
            mutableState.update { it.copy(error = McpOAuthError.INVALID_CALLBACK) }
            return false
        }
        mutableState.update { it.copy(error = null, manualPrompt = false) }
        manualCallback.complete(value)
        return true
    }

    fun cancel() {
        cancelled = true
        callback?.cancel()
        manualCallback.cancel()
        mutableState.update { it.copy(waiting = false, manualPrompt = false, error = null) }
    }
}

suspend fun startMcpCallbackServer(serverName: String): OAuthCallbackServer<String>? {
    var redirectUri = ""
    val server = startOAuthCallbackServerOrNull(
        OAuthCallbackServerOptions<String>(
            providerName = serverName,
            host = "127.0.0.1",
            port = 0,
            path = MCP_CALLBACK_PATH,
            timeoutMs = MCP_CALLBACK_TIMEOUT_MS,
            complete = { request ->
                val query = request.params.entries.joinToString("&") { (key, value) ->
                    "${urlEncode(key)}=${urlEncode(value)}"
                }
                if (query.isEmpty()) redirectUri else "$redirectUri?$query"
            },
        ),
    )
    redirectUri = server?.redirectUri.orEmpty()
    return server
}

private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")
