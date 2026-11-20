package io.github.whywood.superpi.data.relay

import io.github.whywood.superpi.runtime.AgentRuntime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject

enum class RelayLiveState {
    Disabled,
    Connecting,
    Online,
    Offline,
    Unauthorized,
}

class RelayLive(
    private val scope: CoroutineScope,
    private val accounts: RelayStore,
    private val host: AgentRuntime,
    private val memorySync: RelayMemorySync,
    private val appVersion: String,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val socket = RelaySocket(scope, io)
    private val server = RemoteAgentServer(host, ::bots, ::send, scope)
    private val mutex = Mutex()

    private val _state = MutableStateFlow(RelayLiveState.Disabled)
    val state: StateFlow<RelayLiveState> = _state.asStateFlow()

    private val _devices = MutableStateFlow<List<RelayDevice>>(emptyList())
    val devices: StateFlow<List<RelayDevice>> = _devices.asStateFlow()

    private val _allowRemote = MutableStateFlow(false)
    val allowRemote: StateFlow<Boolean> = _allowRemote.asStateFlow()

    @Volatile
    private var started = false

    @Volatile
    private var account: RelayAccount? = null

    fun start() {
        if (started) return
        started = true
        scope.launch { accounts.account.collect { value -> account = value; reconcile() } }
        scope.launch { accounts.allowRemote.collect { value -> _allowRemote.value = value; reconcile() } }
        scope.launch { socket.state.collect { value -> onSocketState(value) } }
        scope.launch { socket.frames.collect { frame -> onFrame(frame) } }
    }

    fun stop() {
        started = false
        socket.stop()
        server.reset()
        _devices.value = emptyList()
        _state.value = RelayLiveState.Disabled
    }

    fun setAllowRemote(enabled: Boolean) {
        scope.launch { accounts.setAllowRemote(enabled) }
    }

    fun sendRoute(to: String, method: String, params: JsonObject): Boolean =
        send(RelayFrames.route(routeId(), to, method, params))

    private suspend fun bots(): List<RelayBot> = runCatching {
        host.workspaces().map { workspace -> RelayBot(workspace.id, workspace.name) }
    }.getOrDefault(emptyList())

    private fun send(frame: JsonObject): Boolean = socket.send(frame.toString())

    private fun routeId(): String = "pr-${System.currentTimeMillis().toString(36)}-${(0..0xFFFFFF).random().toString(36)}"

    private suspend fun reconcile() {
        mutex.withLock {
            val current = account
            val token = accounts.token()
            if (current == null || token == null || !_allowRemote.value) {
                socket.stop()
                server.reset()
                _devices.value = emptyList()
                _state.value = RelayLiveState.Disabled
                return
            }
            if (socket.state.value == RelaySocketState.Online) return
            socket.start(current, token) {
                RelayFrames.hello(
                    deviceId = current.deviceId,
                    platform = "android",
                    appVersion = appVersion,
                    bots = bots(),
                ).toString()
            }
        }
    }

    private fun onSocketState(value: RelaySocketState) {
        val mapped = when (value) {
            RelaySocketState.Stopped -> if (_allowRemote.value) RelayLiveState.Offline else RelayLiveState.Disabled
            RelaySocketState.Connecting -> RelayLiveState.Connecting
            RelaySocketState.Online -> RelayLiveState.Online
            RelaySocketState.Offline -> RelayLiveState.Offline
            RelaySocketState.Unauthorized -> RelayLiveState.Unauthorized
        }
        if (value != RelaySocketState.Online) {
            server.reset()
            _devices.value = emptyList()
        }
        _state.value = mapped
    }

    private suspend fun onFrame(frame: RelayServerFrame) {
        when (frame) {
            is RelayServerFrame.HelloOk -> {
                val self = account?.deviceId
                _devices.value = frame.devices.filter { device -> device.deviceId != self }
                runCatching { memorySync.refreshIfStale(0) }
            }

            is RelayServerFrame.Presence -> {
                val self = account?.deviceId
                if (frame.deviceId == self) return
                val existing = _devices.value
                _devices.value = if (frame.online) {
                    val bots = frame.bots ?: existing.firstOrNull { it.deviceId == frame.deviceId }?.bots.orEmpty()
                    existing.filterNot { it.deviceId == frame.deviceId } + RelayDevice(
                        deviceId = frame.deviceId,
                        name = existing.firstOrNull { it.deviceId == frame.deviceId }?.name ?: frame.deviceId,
                        platform = existing.firstOrNull { it.deviceId == frame.deviceId }?.platform.orEmpty(),
                        online = true,
                        lastSeen = frame.lastSeen,
                        bots = bots,
                    )
                } else {
                    existing.map { device ->
                        if (device.deviceId == frame.deviceId) {
                            device.copy(online = false, lastSeen = frame.lastSeen)
                        } else {
                            device
                        }
                    }
                }
            }

            is RelayServerFrame.MemoryChanged -> runCatching { memorySync.refreshIfStale(0) }
            is RelayServerFrame.Route -> server.handle(frame)
            else -> Unit
        }
    }
}
