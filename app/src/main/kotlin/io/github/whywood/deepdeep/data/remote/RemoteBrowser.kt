package io.github.whywood.deepdeep.data.remote

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class RemoteBrowserPairing(
    val deviceId: String?,
    val sessionExpiresAt: Long,
)

enum class RemoteBrowserState {
    Online,
    NeedsPairing,
    Offline,
}

class RemoteBrowserClient(private val context: Context) {

    suspend fun pair(origin: String, token: String, label: String): RemoteBrowserPairing {
        val response = run(
            origin = origin,
            timeoutMs = PAIR_TIMEOUT_MS,
            script = """
                (function () {
                  window.__dshResult = null;
                  fetch('/mobile-access/auth/pair', {
                    method: 'POST',
                    credentials: 'same-origin',
                    headers: { 'content-type': 'application/json' },
                    body: JSON.stringify({ token: ${token.jsString()}, label: ${label.jsString()} })
                  }).then(function (response) {
                    return response.text().then(function (text) {
                      window.__dshResult = JSON.stringify({ status: response.status, body: text });
                    });
                  }).catch(function (error) {
                    window.__dshResult = JSON.stringify({ status: 0, body: '', error: String(error) });
                  });
                })();
            """.trimIndent(),
        ) ?: throw RemoteGatewayException(RemoteGatewayFailure.Unreachable)
        val envelope = response.jsonObjectOrNull() ?: throw RemoteGatewayException(RemoteGatewayFailure.BadResponse)
        val status = envelope["status"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
        val body = envelope["body"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (status == 0) throw RemoteGatewayException(RemoteGatewayFailure.Unreachable)
        if (status !in 200..299) throw RemoteGatewayException(failureFor(status))
        val payload = body.jsonObjectOrNull() ?: throw RemoteGatewayException(RemoteGatewayFailure.BadResponse)
        if (payload["paired"]?.jsonPrimitive?.contentOrNull != "true") {
            throw RemoteGatewayException(RemoteGatewayFailure.Unauthorized)
        }
        return RemoteBrowserPairing(
            deviceId = payload["deviceId"]?.jsonPrimitive?.contentOrNull,
            sessionExpiresAt = payload["sessionExpiresAt"]?.jsonPrimitive?.longOrNull ?: 0L,
        )
    }

    suspend fun state(origin: String): RemoteBrowserState {
        val response = run(
            origin = origin,
            timeoutMs = STATE_TIMEOUT_MS,
            script = """
                (function () {
                  window.__dshResult = null;
                  var result = { reachable: false, authed: false };
                  fetch('/mobile-access/health', { cache: 'no-store', credentials: 'same-origin' })
                    .then(function (response) {
                      result.reachable = response.ok;
                    })
                    .catch(function () {})
                    .then(function () {
                      if (!result.reachable) return;
                      return fetch('/api', { cache: 'no-store', credentials: 'same-origin' })
                        .then(function (response) {
                          result.authed = response.status !== 401 && response.status !== 403;
                        })
                        .catch(function () {});
                    })
                    .then(function () {
                      window.__dshResult = JSON.stringify(result);
                    })
                    .catch(function (error) {
                      window.__dshResult = JSON.stringify({ reachable: false, authed: false, error: String(error) });
                    });
                })();
            """.trimIndent(),
        ) ?: return RemoteBrowserState.Offline
        val payload = response.jsonObjectOrNull() ?: return RemoteBrowserState.Offline
        val reachable = payload["reachable"]?.jsonPrimitive?.contentOrNull == "true"
        val authed = payload["authed"]?.jsonPrimitive?.contentOrNull == "true"
        return when {
            authed -> RemoteBrowserState.Online
            reachable -> RemoteBrowserState.NeedsPairing
            else -> RemoteBrowserState.Offline
        }
    }

    suspend fun renew(origin: String): Boolean {
        val response = run(
            origin = origin,
            timeoutMs = RENEW_TIMEOUT_MS,
            script = """
                (function () {
                  window.__dshResult = null;
                  fetch('/mobile-access/auth/renew', {
                    method: 'POST',
                    credentials: 'same-origin',
                    headers: { 'content-type': 'application/json' },
                    body: '{}'
                  }).then(function (response) {
                    window.__dshResult = JSON.stringify({ status: response.status });
                  }).catch(function (error) {
                    window.__dshResult = JSON.stringify({ status: 0, error: String(error) });
                  });
                })();
            """.trimIndent(),
        ) ?: return false
        val payload = response.jsonObjectOrNull() ?: return false
        val status = payload["status"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
        return status in 200..299
    }

    fun clearCookies(origin: String) {
        val manager = CookieManager.getInstance()
        val target = "${origin.trimEnd('/')}/"
        listOf(
            "dsh_ma_session=; Max-Age=0; Path=/",
            "dsh_ma_csrf=; Max-Age=0; Path=/",
            "__Host-dsh_ma_session=; Max-Age=0; Path=/",
            "__Host-dsh_ma_csrf=; Max-Age=0; Path=/",
            "dsh_ma_device=; Max-Age=0; Path=/mobile-access/auth/renew",
            "__Host-dsh_ma_device=; Max-Age=0; Path=/",
        ).forEach { cookie -> manager.setCookie(target, cookie) }
        manager.flush()
    }

    private suspend fun run(origin: String, timeoutMs: Long, script: String): String? = withContext(Dispatchers.Main) {
        val webView = createWebView()
        try {
            val loaded = withTimeoutOrNull(LOAD_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            if (continuation.isActive) continuation.resume(true)
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?,
                        ) {
                            if (request?.isForMainFrame == true && continuation.isActive) continuation.resume(false)
                        }
                    }
                    webView.loadUrl("${origin.trimEnd('/')}/mobile-access/health")
                }
            } ?: false
            if (!loaded) return@withContext null
            webView.evaluateJavascript(script, null)
            val raw = withTimeoutOrNull(timeoutMs) {
                var value: String? = null
                while (value == null) {
                    delay(POLL_INTERVAL_MS)
                    val result = evaluate(webView)
                    if (result != null && result != "null" && result != EMPTY_JS_STRING) value = result
                }
                value
            } ?: return@withContext null
            decodeJs(raw)
        } finally {
            webView.stopLoading()
            webView.webViewClient = WebViewClient()
            webView.destroy()
            CookieManager.getInstance().flush()
        }
    }

    private suspend fun evaluate(webView: WebView): String? = suspendCancellableCoroutine { continuation ->
        webView.evaluateJavascript("window.__dshResult === undefined ? '' : window.__dshResult") { value ->
            if (continuation.isActive) continuation.resume(value)
        }
    }

    private fun createWebView(): WebView = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.cacheMode = WebSettings.LOAD_NO_CACHE
        CookieManager.getInstance().setAcceptCookie(true)
    }

    private fun decodeJs(raw: String): String? =
        runCatching { Json.parseToJsonElement(raw).jsonPrimitive.contentOrNull }.getOrNull()

    private fun failureFor(status: Int): RemoteGatewayFailure = when (status) {
        401, 403 -> RemoteGatewayFailure.Unauthorized
        409 -> RemoteGatewayFailure.DeviceLimit
        429 -> RemoteGatewayFailure.RateLimited
        in 500..599 -> RemoteGatewayFailure.BadResponse
        else -> RemoteGatewayFailure.BadResponse
    }

    private fun String.jsonObjectOrNull() = runCatching { Json.parseToJsonElement(this).jsonObject }.getOrNull()

    private fun String.jsString(): String = buildString {
        append('"')
        this@jsString.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '<' -> append("\\u003c")
                '>' -> append("\\u003e")
                '&' -> append("\\u0026")
                else -> append(character)
            }
        }
        append('"')
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 20_000L
        const val PAIR_TIMEOUT_MS = 20_000L
        const val RENEW_TIMEOUT_MS = 20_000L
        const val STATE_TIMEOUT_MS = 15_000L
        const val POLL_INTERVAL_MS = 120L
        const val EMPTY_JS_STRING = "\"\""
    }
}
