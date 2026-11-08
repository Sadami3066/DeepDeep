package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.sdk.ai.auth.oauth.OAuthCallbackRequest
import io.github.whywood.deepdeep.sdk.ai.auth.oauth.OAuthCallbackServer
import io.github.whywood.deepdeep.sdk.ai.auth.oauth.OAuthCallbackServerFactory
import io.github.whywood.deepdeep.sdk.ai.auth.oauth.OAuthCallbackServerOptions
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DeepDeepOAuthCallbackServerFactory(
    private val returnUrl: String = "deepdeep://oauth/callback",
) : OAuthCallbackServerFactory {
    override suspend fun <T> start(options: OAuthCallbackServerOptions<T>): OAuthCallbackServer<T> =
        withContext(Dispatchers.IO) { CallbackServer(options, returnUrl) }
}

private const val CALLBACK_TIMEOUT_MS = 5 * 60 * 1000L

private class CallbackServer<T>(
    private val options: OAuthCallbackServerOptions<T>,
    private val returnUrl: String,
) : OAuthCallbackServer<T> {
    private val completion = CompletableDeferred<T?>()
    private val settled = AtomicBoolean(false)
    private val claimed = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val serverSocket = ServerSocket()
    private var timeoutJob: Job? = null

    override val redirectUri: String

    init {
        options.signal?.let { signal ->
            if (signal.isAborted) throw IllegalStateException("OAuth sign-in was cancelled")
        }
        serverSocket.reuseAddress = true
        serverSocket.bind(InetSocketAddress(options.host, options.port))
        val redirectHost = options.redirectHost ?: options.host
        val renderedHost = if (redirectHost.contains(":")) "[$redirectHost]" else redirectHost
        redirectUri = "http://$renderedHost:${serverSocket.localPort}${options.path}"
        timeoutJob = scope.launch {
            delay(options.timeoutMs ?: CALLBACK_TIMEOUT_MS)
            finish(Result.failure(IllegalStateException("${options.providerName} sign-in timed out")))
        }
        scope.launch { acceptLoop() }
        options.signal?.onAbort {
            finish(Result.failure(IllegalStateException("OAuth sign-in was cancelled")))
        }
    }

    override suspend fun wait(): T? = completion.await()

    override fun cancel() {
        if (!claimed.get()) finish(Result.success(null))
    }

    override fun close() {
        finish(Result.failure(IllegalStateException("OAuth callback server closed")))
        runCatching { serverSocket.close() }
    }

    private fun finish(result: Result<T?>) {
        if (!settled.compareAndSet(false, true)) return
        timeoutJob?.cancel()
        val error = result.exceptionOrNull()
        if (error != null) {
            completion.completeExceptionally(error)
        } else {
            completion.complete(result.getOrNull())
        }
        runCatching { serverSocket.close() }
    }

    private suspend fun acceptLoop() {
        while (!settled.get()) {
            val client = try {
                serverSocket.accept()
            } catch (_: Throwable) {
                if (settled.get()) return
                continue
            }
            scope.launch { handleConnection(client) }
        }
    }

    private suspend fun handleConnection(client: Socket) {
        client.use { socket ->
            try {
                val input = socket.getInputStream()
                val requestLine = readRequestLine(input) ?: return
                while (true) {
                    val header = readRequestLine(input) ?: break
                    if (header.isEmpty()) break
                }
                val parts = requestLine.split(" ")
                if (parts.size < 2 || parts[0] != "GET") {
                    sendPage(socket, 404, errorPage("Callback route not found."))
                    return
                }
                val target = parts[1]
                val path = target.substringBefore('?')
                val query = if (target.contains('?')) target.substringAfter('?') else ""
                if (path != options.path) {
                    sendPage(socket, 404, errorPage("Callback route not found."))
                    return
                }
                val params = parseQuery(query)
                if (options.state != null && params["state"] != options.state) {
                    sendPage(socket, 400, errorPage("State mismatch."))
                    return
                }
                if (claimed.get() || settled.get()) {
                    sendPage(socket, 409, errorPage("This sign-in has already been handled."))
                    return
                }
                val error = params["error"]
                if (!error.isNullOrEmpty()) {
                    val description = params["error_description"] ?: error
                    sendPage(socket, 400, errorPage("Authorization failed: $description"))
                    finish(Result.failure(IllegalStateException("Authorization failed: $description")))
                    return
                }
                val code = params["code"]
                if (code.isNullOrEmpty()) {
                    sendPage(socket, 400, errorPage("Missing authorization code."))
                    return
                }
                claimed.set(true)
                try {
                    val value = options.complete(OAuthCallbackRequest(code, params["state"], params))
                    sendPage(socket, 200, successPage(options.providerName))
                    finish(Result.success(value))
                } catch (failure: Throwable) {
                    val message = failure.message ?: failure.toString()
                    sendPage(socket, 502, errorPage("Sign-in failed: $message"))
                    finish(Result.failure(failure))
                }
            } catch (failure: Throwable) {
                finish(Result.failure(failure))
            }
        }
    }

    private fun successPage(providerName: String): String = page(
        title = "Signed in",
        message = "${escape(providerName)} · 已登录，正在返回 DeepDeep…",
        button = "返回 DeepDeep",
    )

    private fun errorPage(message: String): String = page(
        title = "Sign-in failed",
        message = escape(message),
        button = "返回 DeepDeep",
    )

    private fun page(title: String, message: String, button: String): String = """
        <!doctype html>
        <html><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>${escape(title)}</title>
        <style>
        body{font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;background:#f6f6f4;color:#161616;margin:0;display:flex;align-items:center;justify-content:center;min-height:100vh}
        main{text-align:center;padding:32px;max-width:420px}
        h1{font-size:20px;font-weight:600;margin:0 0 10px}
        p{color:#6b6b6b;font-size:15px;line-height:1.5;margin:0 0 28px;word-break:break-word}
        a{display:inline-block;background:#1f1f1f;color:#fff;text-decoration:none;padding:12px 26px;border-radius:999px;font-size:15px}
        </style></head>
        <body><main><h1>${escape(title)}</h1><p>$message</p>
        <a href="${escape(returnUrl)}">${escape(button)}</a></main>
        <script>setTimeout(function(){window.location.href="${returnUrl}"}, 400)</script>
        </body></html>
    """.trimIndent()

    private fun sendPage(socket: Socket, status: Int, html: String) {
        val body = html.encodeToByteArray()
        val reason = when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            404 -> "Not Found"
            409 -> "Conflict"
            502 -> "Bad Gateway"
            else -> "Error"
        }
        val head = buildString {
            append("HTTP/1.1 ").append(status).append(' ').append(reason).append("\r\n")
            append("Content-Type: text/html; charset=utf-8\r\n")
            append("Cache-Control: no-store\r\n")
            append("Content-Length: ").append(body.size).append("\r\n")
            append("Connection: close\r\n\r\n")
        }
        val output = socket.getOutputStream()
        output.write(head.encodeToByteArray())
        output.write(body)
        output.flush()
    }
}

private fun escape(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")

private fun readRequestLine(input: InputStream): String? {
    val line = StringBuilder()
    while (true) {
        val byte = input.read()
        if (byte == -1) return if (line.isEmpty()) null else line.toString()
        if (byte == '\n'.code) {
            if (line.isNotEmpty() && line[line.length - 1] == '\r') {
                line.setLength(line.length - 1)
            }
            return line.toString()
        }
        line.append(byte.toChar())
    }
}

private fun parseQuery(query: String): Map<String, String> {
    if (query.isEmpty()) return emptyMap()
    val result = LinkedHashMap<String, String>()
    for (part in query.split("&")) {
        if (part.isEmpty()) continue
        val name = part.substringBefore('=')
        val value = if (part.contains('=')) part.substringAfter('=') else ""
        val decodedName = runCatching {
            URLDecoder.decode(name, StandardCharsets.UTF_8.name())
        }.getOrDefault(name)
        val decodedValue = runCatching {
            URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        }.getOrDefault(value)
        result[decodedName] = decodedValue
    }
    return result
}
