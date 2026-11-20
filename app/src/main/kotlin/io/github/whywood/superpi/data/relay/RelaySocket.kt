package io.github.whywood.superpi.data.relay

import io.github.whywood.superpi.data.remote.RemoteTls
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

enum class RelaySocketState {
    Stopped,
    Connecting,
    Online,
    Offline,
    Unauthorized,
}

class RelaySocket(
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) {
    private val _state = MutableStateFlow(RelaySocketState.Stopped)
    val state: StateFlow<RelaySocketState> = _state.asStateFlow()

    private val _frames = MutableSharedFlow<RelayServerFrame>(extraBufferCapacity = 128)
    val frames: SharedFlow<RelayServerFrame> = _frames.asSharedFlow()

    private var job: Job? = null

    @Volatile
    private var socket: WebSocket? = null

    @Volatile
    private var client: OkHttpClient? = null

    @Volatile
    private var lastFrameAt = 0L

    @Volatile
    private var running = false

    fun start(
        account: RelayAccount,
        token: String,
        hello: suspend () -> String?,
    ) {
        if (running) return
        running = true
        job = scope.launch(io) { connectLoop(account, token) { hello() } }
    }

    fun stop() {
        running = false
        socket?.close(1000, "closed")
        socket = null
        client?.let(::shutdownClient)
        client = null
        job?.cancel()
        job = null
        _state.value = RelaySocketState.Stopped
    }

    fun send(frame: String): Boolean = socket?.send(frame) ?: false

    private suspend fun connectLoop(
        account: RelayAccount,
        token: String,
        hello: suspend () -> String?,
    ) {
        var attempt = 0
        while (running) {
            _state.value = RelaySocketState.Connecting
            val closed = CompletableDeferred<Throwable?>()
            val pin = account.caPem?.let { RemoteTls.decodeBytes(it.toByteArray(Charsets.UTF_8)) }
            val http = RemoteTls.client(pin = pin, remote = true).newBuilder()
                .pingInterval(20, TimeUnit.SECONDS)
                .build()
            client = http
            val request = Request.Builder()
                .url(webSocketUrl(account.origin))
                .header("authorization", "Bearer $token")
                .build()
            lastFrameAt = System.currentTimeMillis()
            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    socket = webSocket
                    lastFrameAt = System.currentTimeMillis()
                    _state.value = RelaySocketState.Online
                    scope.launch {
                        val greeting = runCatching { hello() }.getOrNull() ?: return@launch
                        webSocket.send(greeting)
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    lastFrameAt = System.currentTimeMillis()
                    RelayFrames.parse(text)?.let { frame -> _frames.tryEmit(frame) }
                }

                override fun onFailure(webSocket: WebSocket, error: Throwable, response: Response?) {
                    val code = response?.code
                    if (code == 401 || code == 403) {
                        _state.value = RelaySocketState.Unauthorized
                        running = false
                    }
                    closed.complete(error)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    closed.complete(null)
                }
            }
            val active = http.newWebSocket(request, listener)
            socket = active
            val error = closed.await()
            socket = null
            shutdownClient(http)
            client = null
            if (!running) break
            _state.value = RelaySocketState.Offline
            attempt = (attempt + 1).coerceAtMost(BACKOFF.size - 1)
            if (error != null) delay(BACKOFF[attempt])
        }
        if (_state.value != RelaySocketState.Unauthorized) _state.value = RelaySocketState.Stopped
    }

    private fun shutdownClient(http: OkHttpClient) {
        runCatching { http.dispatcher.executorService.shutdown() }
        runCatching { http.connectionPool.evictAll() }
    }

    private fun webSocketUrl(origin: String): String {
        val secure = origin.startsWith("https://")
        val host = origin.substringAfter("://")
        return if (secure) "wss://$host/v1/ws" else "ws://$host/v1/ws"
    }

    private companion object {
        private val BACKOFF = longArrayOf(1_000, 2_000, 5_000, 10_000, 30_000)
    }
}
