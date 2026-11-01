package io.github.whywood.deepdeep.vscreen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import io.github.whywood.deepdeep.R
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class VirtualScreenEngine(
    private val context: Context,
    private val launcher: PrivilegedLauncher,
) : VirtualScreenController {
    private val _state = MutableStateFlow<VirtualScreenState>(VirtualScreenState.Offline)
    override val state: StateFlow<VirtualScreenState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lifecycleMutex = Mutex()
    private val commandMutex = Mutex()
    private val connectionLock = Any()
    private val requestIds = AtomicInteger()
    private val heartbeatIds = AtomicInteger()
    private val writeLock = Any()

    @Volatile
    private var socket: Socket? = null

    @Volatile
    private var input: InputStream? = null

    @Volatile
    private var output: OutputStream? = null

    @Volatile
    private var helper: HelperProcess? = null

    @Volatile
    private var readerJob: Job? = null

    @Volatile
    private var heartbeatJob: Job? = null
    @Volatile
    private var commandInFlight = false

    @Volatile
    private var recoveryJob: Job? = null
    val reconnecting: Boolean get() = recoveryJob?.isActive == true

    private val correlator = ResponseCorrelator()
    private val screenshotGate = ScreenshotGate()
    private val heartbeat = HeartbeatPolicy()
    private val pongGate = PongGate()
    private val lifecycle = SessionLifecycle()

    @Volatile
    private var pendingResponse: CompletableDeferred<JsonObject>? = null

    @Volatile
    private var pendingScreenshotEpoch = 0L

    @Volatile
    private var pendingFailure: TransportFailure? = null

    @Volatile
    private var connectedBackend: String? = null

    @Volatile
    private var connectedUid: Int? = null

    @Volatile
    private var connectedHelperPid: Int? = null

    @Volatile
    internal var lastFailure: VirtualScreenFailureInfo? = null
        private set

    @Volatile
    internal var lastTransportNotice: String? = null
        private set

    val backend: String?
        get() = connectedBackend

    val helperUid: Int?
        get() = connectedUid

    fun isOnline(): Boolean = _state.value is VirtualScreenState.Online

    override suspend fun start(): VirtualScreenResult = lifecycleMutex.withLock {
        cancelRecovery()
        lastTransportNotice = null
        val current = _state.value
        val online = current as? VirtualScreenState.Online
        val healthOk = if (online != null) probeOnline(online) else null
        when (planStart(online = online != null, starting = current is VirtualScreenState.Starting, healthOk = healthOk)) {
            StartPlan.BUSY -> return@withLock VirtualScreenResult(false, string(R.string.virtual_screen_result_starting))
            StartPlan.REUSE -> return@withLock VirtualScreenResult(true, string(R.string.virtual_screen_result_started))
            StartPlan.REBUILD -> {
                Log.w(
                    TAG,
                    VirtualScreenTransportLog.event(
                        "online_unhealthy",
                        "backend" to online?.backend,
                        "helperPid" to connectedHelperPid,
                        "displayId" to online?.displayId,
                    ),
                )
                cleanupSession()
                online?.frame?.let { recycleLater(it) }
            }
            StartPlan.START -> Unit
        }
        startLocked()
    }

    override suspend fun stop() {
        lifecycleMutex.withLock {
            lifecycle.onStopped()
            cancelRecovery()
            if (_state.value is VirtualScreenState.Offline) return@withLock
            val frame = withContext(Dispatchers.IO) {
                runCatching { sendText(VirtualScreenCommands.STOP, JsonObject(emptyMap())) }
                cleanupSession()
                (_state.value as? VirtualScreenState.Online)?.frame
            }
            lastFailure = null
            lastTransportNotice = null
            _state.value = VirtualScreenState.Offline
            frame?.let { recycleLater(it) }
        }
    }

    override suspend fun launch(packageName: String): VirtualScreenResult {
        val report = launchWithReport(packageName)
        return VirtualScreenResult(report.ok, report.message)
    }

    suspend fun launchWithReport(packageName: String): VirtualScreenLaunchReport {
        val name = packageName.trim()
        if (name.isEmpty()) {
            return VirtualScreenLaunchReport(false, "Missing packageName", taskDisplayId = INVALID_TASK_DISPLAY)
        }
        if (!isOnline()) {
            return VirtualScreenLaunchReport(false, "Virtual screen is not running", taskDisplayId = INVALID_TASK_DISPLAY)
        }
        return withContext(Dispatchers.IO) {
            try {
                val response = request(
                    VirtualScreenCommands.LAUNCH,
                    buildJsonObject { put("packageName", name) },
                )
                val displayId = response.intField("displayId") ?: INVALID_TASK_DISPLAY
                val taskDisplayId = response.intField("taskDisplayId") ?: INVALID_TASK_DISPLAY
                VirtualScreenLaunchReport(
                    ok = true,
                    message = response.stringField("message").orEmpty(),
                    packageName = name,
                    displayId = displayId,
                    taskDisplayId = taskDisplayId,
                    landedOnMain = response.booleanField("landedOnMain") ?: (taskDisplayId == 0),
                )
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                VirtualScreenLaunchReport(false, t.readableMessage(), packageName = name, taskDisplayId = INVALID_TASK_DISPLAY)
            }
        }
    }

    override suspend fun tap(x: Int, y: Int): VirtualScreenResult = tap(x, y, 60)

    suspend fun tap(x: Int, y: Int, durationMs: Int): VirtualScreenResult {
        if (x < 0 || y < 0) return VirtualScreenResult(false, "Coordinates must not be negative")
        return executeCommand(
            VirtualScreenCommands.TAP,
            buildJsonObject {
                put("x", x)
                put("y", y)
                put("durationMs", durationMs)
            },
        )
    }

    override suspend fun swipe(
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        durationMs: Long,
    ): VirtualScreenResult = executeCommand(
        VirtualScreenCommands.SWIPE,
        buildJsonObject {
            put("x1", x1)
            put("y1", y1)
            put("x2", x2)
            put("y2", y2)
            put("durationMs", durationMs)
        },
    )

    override suspend fun key(name: String): VirtualScreenResult {
        val keyName = name.trim()
        if (keyName.isEmpty()) return VirtualScreenResult(false, "Missing key name")
        return executeCommand(
            VirtualScreenCommands.KEY,
            buildJsonObject { put("key", keyName) },
        )
    }

    override suspend fun text(value: String): VirtualScreenResult {
        if (value.isEmpty()) return VirtualScreenResult(false, "Text must not be empty")
        return executeCommand(
            VirtualScreenCommands.TEXT,
            buildJsonObject { put("text", value) },
        )
    }

    override suspend fun screenshot(): ByteArray? = withContext(Dispatchers.IO) {
        if (!isOnline()) return@withContext null
        commandMutex.withLock {
            val epoch = screenshotGate.open()
            try {
                val response = requestLocked(VirtualScreenCommands.SCREENSHOT, JsonObject(emptyMap()), screenshotEpoch = epoch)
                if (response.stringField("kind") != "screenshot") return@withLock null
                screenshotGate.await(epoch, SCREENSHOT_TIMEOUT_MS)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                null
            } finally {
                screenshotGate.close(epoch)
            }
        }
    }

    private suspend fun startLocked(): VirtualScreenResult {
        _state.value = VirtualScreenState.Starting
        val outcome = try {
            withContext(Dispatchers.IO) { startSession() }
        } catch (t: Throwable) {
            cleanupSession()
            if (t is CancellationException) {
                lifecycle.onStopped()
                val message = string(R.string.virtual_screen_result_start_cancelled)
                recordFailure(VirtualScreenFailureCategory.START, message, recoverable = false)
                _state.value = VirtualScreenState.Failed(message)
                throw t
            }
            val message = t.readableMessage()
            lifecycle.onStopped()
            recordFailure(VirtualScreenFailureCategory.START, message, recoverable = false)
            _state.value = VirtualScreenState.Failed(message)
            return VirtualScreenResult(false, message)
        }
        if (outcome.ok) {
            lastFailure = null
            lastTransportNotice = null
            return VirtualScreenResult(true, string(R.string.virtual_screen_result_started))
        }
        lifecycle.onStopped()
        recordFailure(outcome.category ?: VirtualScreenFailureCategory.START, outcome.message, recoverable = false)
        _state.value = VirtualScreenState.Failed(outcome.message)
        return VirtualScreenResult(false, outcome.message)
    }

    private suspend fun startSession(): SessionStart {
        val backends = launcher.backends()
        if (backends.isEmpty()) {
            return SessionStart(
                ok = false,
                message = string(R.string.virtual_screen_result_no_backend),
                category = VirtualScreenFailureCategory.BACKEND,
            )
        }
        val nonce = UUID.randomUUID().toString().replace("-", "")
        val errors = mutableListOf<String>()
        for (backend in backends) {
            try {
                tryBackend(backend, nonce)
                return SessionStart(ok = true)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                Log.w(TAG, VirtualScreenTransportLog.event("backend_start_failed", "backend" to backend, "error" to t.readableMessage()))
                errors += "${backendLabel(backend)}：${t.readableMessage()}"
                cleanupSession()
            }
        }
        return SessionStart(
            ok = false,
            message = string(R.string.virtual_screen_result_start_failed, errors.joinToString("; ")),
            category = VirtualScreenFailureCategory.START,
        )
    }

    private suspend fun tryBackend(backend: String, nonce: String) {
        val server = ServerSocket(0, 1, InetAddress.getByName(LOOPBACK))
        try {
            helper = launcher.start(backend, server.localPort, nonce)
            val client = accept(server) ?: throw IOException("Timed out waiting for the helper to connect")
            runCatching { server.close() }
            socket = client
            input = BufferedInputStream(client.getInputStream())
            output = BufferedOutputStream(client.getOutputStream())
            client.soTimeout = HANDSHAKE_TIMEOUT_MS
            val hello = readFrameJson() ?: throw IOException("The helper did not send a handshake")
            val uid = hello.intField("uid") ?: -1
            if (uid != VirtualScreenCommands.SHELL_UID) {
                throw IOException("Helper uid=$uid; shell uid 2000 is required")
            }
            if (hello.stringField("nonce") != nonce) {
                throw IOException("Helper handshake nonce mismatch")
            }
            connectedUid = uid
            connectedBackend = backend
            connectedHelperPid = hello.intField("pid") ?: helper?.pid
            val id = requestIds.incrementAndGet()
            sendText(
                VirtualScreenCommands.START,
                buildJsonObject { put("id", id) },
            )
            val response = readResponse(id) ?: throw IOException("The helper did not return a start result")
            if (response.booleanField("ok") != true) {
                throw IOException(response.stringField("error") ?: "Failed to start the virtual screen")
            }
            val displayId = response.intField("displayId") ?: throw IOException("The helper did not return a displayId")
            val width = response.intField("width") ?: VirtualScreenCommands.DEFAULT_WIDTH
            val height = response.intField("height") ?: VirtualScreenCommands.DEFAULT_HEIGHT
            correlator.reset()
            screenshotGate.reset()
            pongGate.reset()
            heartbeat.onConnected(nowMs())
            client.soTimeout = 0
            _state.value = VirtualScreenState.Online(displayId, width, height, backend, null)
            lifecycle.onStarted()
            lastFailure = null
            lastTransportNotice = null
            Log.i(
                TAG,
                VirtualScreenTransportLog.event(
                    "transport_online",
                    "backend" to backend,
                    "helperPid" to connectedHelperPid,
                    "displayId" to displayId,
                    "uid" to uid,
                    "width" to width,
                    "height" to height,
                ),
            )
            readerJob = scope.launch { readLoop(client) }
            heartbeatJob = scope.launch { heartbeatLoop(client) }
        } finally {
            runCatching { server.close() }
        }
    }

    private fun accept(server: ServerSocket): Socket? = runCatching {
        server.soTimeout = CONNECT_TIMEOUT_MS.toInt()
        server.accept()
    }.getOrNull()

    private fun readFrameJson(): JsonObject? {
        while (true) {
            val frame = VirtualScreenProtocol.read(input ?: return null) ?: return null
            if (frame.kind != VirtualScreenFrameKind.TEXT) continue
            return runCatching { VirtualScreenProtocol.json(frame) }.getOrNull()
        }
    }

    private fun readResponse(id: Int): JsonObject? {
        while (true) {
            val frame = VirtualScreenProtocol.read(input ?: return null) ?: return null
            if (frame.kind != VirtualScreenFrameKind.TEXT) continue
            val json = runCatching { VirtualScreenProtocol.json(frame) }.getOrNull() ?: continue
            if (json.intField("id") == id) return json
        }
    }

    private fun readLoop(client: Socket) {
        var kind = TransportFailureKind.EOF
        var detail = "stream ended"
        var error: Throwable? = null
        try {
            while (socket === client) {
                val frame = try {
                    VirtualScreenProtocol.read(input ?: break)
                } catch (t: IOException) {
                    kind = TransportFailureKind.FRAME_INVALID
                    detail = t.readableMessage()
                    error = t
                    break
                } ?: break
                when (frame.kind) {
                    VirtualScreenFrameKind.TEXT -> handleTextFrame(frame)
                    VirtualScreenFrameKind.BINARY -> handleStreamFrame(frame.payload)
                    VirtualScreenFrameKind.SCREENSHOT -> handleScreenshotFrame(frame.payload)
                }
            }
        } catch (t: Throwable) {
            if (t is CancellationException) return
            kind = TransportFailureKind.IO
            detail = t.readableMessage()
            error = t
        }
        if (socket !== client) return
        val logLine = VirtualScreenTransportLog.event(
            "read_loop_end",
            "reason" to kind.code,
            "backend" to connectedBackend,
            "helperPid" to connectedHelperPid,
            "displayId" to (_state.value as? VirtualScreenState.Online)?.displayId,
            "detail" to detail,
        )
        if (error != null) Log.w(TAG, logLine, error) else Log.w(TAG, logLine)
        failConnection(client, kind, detail)
    }

    private fun handleTextFrame(frame: VirtualScreenFrame) {
        val json = runCatching { VirtualScreenProtocol.json(frame) }.getOrNull()
        if (json == null) {
            Log.w(TAG, VirtualScreenTransportLog.event("text_frame_invalid", "backend" to connectedBackend))
            return
        }
        if (isPongResponse(json)) {
            heartbeat.onPong(nowMs())
            pongGate.onPong()
            return
        }
        val id = json.intField("id") ?: 0
        if (id <= 0) {
            Log.w(
                TAG,
                VirtualScreenTransportLog.event(
                    "text_frame_uncorrelated",
                    "backend" to connectedBackend,
                    "payload" to json.toString().take(MAX_LOG_PAYLOAD_CHARS),
                ),
            )
            return
        }
        when (correlator.resolve(id)) {
            ResponseMatch.MATCHED -> completePending(json)
            ResponseMatch.LATE -> Log.w(
                TAG,
                VirtualScreenTransportLog.event("late_response_dropped", "id" to id, "backend" to connectedBackend),
            )
            ResponseMatch.UNKNOWN -> Log.w(
                TAG,
                VirtualScreenTransportLog.event("unknown_response_dropped", "id" to id, "backend" to connectedBackend),
            )
        }
    }

    private fun completePending(json: JsonObject) {
        val deferred = pendingResponse ?: return
        val epoch = pendingScreenshotEpoch
        if (epoch > 0 && json.stringField("kind") == "screenshot" && !screenshotGate.arm(epoch)) {
            Log.w(TAG, VirtualScreenTransportLog.event("screenshot_arm_failed", "epoch" to epoch, "backend" to connectedBackend))
        }
        deferred.complete(json)
    }

    private fun handleScreenshotFrame(bytes: ByteArray) {
        if (!screenshotGate.onFrame(bytes)) {
            Log.w(
                TAG,
                VirtualScreenTransportLog.event("screenshot_frame_dropped", "bytes" to bytes.size, "backend" to connectedBackend),
            )
        }
    }

    private fun handleStreamFrame(bytes: ByteArray) {
        val online = _state.value as? VirtualScreenState.Online ?: return
        val bitmap = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull() ?: return
        val current = _state.value as? VirtualScreenState.Online ?: run {
            bitmap.recycle()
            return
        }
        _state.value = current.copy(frame = bitmap)
        online.frame?.takeIf { it !== bitmap }?.let { recycleLater(it) }
    }

    private fun failConnection(client: Socket, detectedKind: TransportFailureKind, detectedDetail: String) {
        synchronized(connectionLock) {
            if (socket !== client) return
            val online = _state.value as? VirtualScreenState.Online ?: return
            if (!lifecycle.shouldRecover()) return
            val failure = pendingFailure ?: TransportFailure(detectedKind, detectedDetail)
            pendingFailure = null
            Log.w(
                TAG,
                VirtualScreenTransportLog.failure(failure.kind, connectedBackend, connectedHelperPid, online.displayId, failure.detail),
            )
            cleanupSession()
            online.frame?.let { recycleLater(it) }
            val message = string(R.string.virtual_screen_failed_transport_lost, reasonLabel(failure.kind))
            recordFailure(VirtualScreenFailureCategory.TRANSPORT, message, recoverable = true, reason = failure.kind)
            _state.value = VirtualScreenState.Failed(message)
            scheduleRecovery(failure.kind)
        }
    }

    private suspend fun heartbeatLoop(client: Socket) {
        while (true) {
            try {
                delay(HeartbeatPolicy.CHECK_INTERVAL_MS)
            } catch (t: CancellationException) {
                throw t
            }
            if (socket !== client) return
            val now = nowMs()
            if (heartbeat.isTransportLost(now, commandInFlight)) {
                closeTransport(client, TransportFailureKind.HEARTBEAT, "no pong for ${HeartbeatPolicy.TIMEOUT_MS}ms")
                return
            }
            if (!heartbeat.isPingDue(now)) continue
            try {
                sendPing()
                heartbeat.onPingSent(now)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                closeTransport(client, TransportFailureKind.IO, t.readableMessage())
                return
            }
        }
    }

    private fun closeTransport(client: Socket, kind: TransportFailureKind, detail: String) {
        synchronized(connectionLock) {
            if (socket !== client) return
            if (pendingFailure == null) pendingFailure = TransportFailure(kind, detail)
            Log.w(
                TAG,
                VirtualScreenTransportLog.event(
                    "transport_closing",
                    "reason" to kind.code,
                    "backend" to connectedBackend,
                    "helperPid" to connectedHelperPid,
                    "detail" to detail,
                ),
            )
            runCatching { client.close() }
        }
    }

    private suspend fun probeOnline(online: VirtualScreenState.Online): Boolean = withContext(Dispatchers.IO) {
        commandMutex.withLock {
            val client = socket ?: return@withLock false
            if (client.isClosed) return@withLock false
            pongGate.arm()
            try {
                sendPing()
                pongGate.await(HeartbeatPolicy.HEALTH_PING_TIMEOUT_MS)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                Log.w(
                    TAG,
                    VirtualScreenTransportLog.event(
                        "health_probe_failed",
                        "backend" to online.backend,
                        "helperPid" to connectedHelperPid,
                        "displayId" to online.displayId,
                        "error" to t.readableMessage(),
                    ),
                )
                false
            } finally {
                pongGate.reset()
            }
        }
    }

    private fun scheduleRecovery(kind: TransportFailureKind) {
        recoveryJob?.cancel()
        val scheduler = ReconnectScheduler()
        recoveryJob = scope.launch {
            Log.w(
                TAG,
                VirtualScreenTransportLog.event(
                    "recovery_start",
                    "reason" to kind.code,
                    "attempts" to scheduler.maxAttempts,
                    "backend" to connectedBackend,
                ),
            )
            val recovered = scheduler.run { attempt ->
                if (!lifecycle.shouldRecover()) {
                    RecoveryOutcome.ABORT
                } else {
                    Log.i(TAG, VirtualScreenTransportLog.event("recovery_attempt", "reason" to kind.code, "attempt" to attempt))
                    withContext(Dispatchers.IO) { recoverAttempt(kind) }
                }
            }
            if (recovered) {
                val notice = string(R.string.virtual_screen_rebuilt_notice)
                lastTransportNotice = notice
                lastFailure = null
                Log.i(
                    TAG,
                    VirtualScreenTransportLog.event(
                        "recovery_success",
                        "reason" to kind.code,
                        "notice" to notice,
                        "backend" to connectedBackend,
                        "helperPid" to connectedHelperPid,
                        "displayId" to (_state.value as? VirtualScreenState.Online)?.displayId,
                    ),
                )
            } else {
                val failure = lastFailure
                if (_state.value !is VirtualScreenState.Online && (failure == null || failure.recoverable)) {
                    val message = string(R.string.virtual_screen_failed_reconnect_gave_up, reasonLabel(kind))
                    recordFailure(VirtualScreenFailureCategory.TRANSPORT, message, recoverable = false, reason = kind)
                    _state.value = VirtualScreenState.Failed(message)
                }
                Log.w(
                    TAG,
                    VirtualScreenTransportLog.event(
                        "recovery_gave_up",
                        "reason" to kind.code,
                        "category" to lastFailure?.category,
                        "backend" to connectedBackend,
                    ),
                )
            }
        }
    }

    private suspend fun recoverAttempt(kind: TransportFailureKind): RecoveryOutcome = lifecycleMutex.withLock {
        if (!lifecycle.shouldRecover()) return@withLock RecoveryOutcome.ABORT
        if (_state.value is VirtualScreenState.Online) return@withLock RecoveryOutcome.RECOVERED
        _state.value = VirtualScreenState.Starting
        val outcome = try {
            withContext(Dispatchers.IO) { startSession() }
        } catch (t: Throwable) {
            cleanupSession()
            if (t is CancellationException) throw t
            val message = string(R.string.virtual_screen_failed_reconnect_retrying, reasonLabel(kind))
            recordFailure(VirtualScreenFailureCategory.TRANSPORT, message, recoverable = true, reason = kind)
            _state.value = VirtualScreenState.Failed(message)
            return@withLock RecoveryOutcome.RETRY
        }
        when {
            outcome.ok -> RecoveryOutcome.RECOVERED
            outcome.category == VirtualScreenFailureCategory.BACKEND -> {
                lifecycle.onStopped()
                val message = string(R.string.virtual_screen_failed_reconnect_backend)
                recordFailure(VirtualScreenFailureCategory.BACKEND, message, recoverable = false, reason = kind)
                _state.value = VirtualScreenState.Failed(message)
                RecoveryOutcome.ABORT
            }
            else -> {
                val message = string(R.string.virtual_screen_failed_reconnect_retrying, reasonLabel(kind))
                recordFailure(VirtualScreenFailureCategory.TRANSPORT, message, recoverable = true, reason = kind)
                _state.value = VirtualScreenState.Failed(message)
                RecoveryOutcome.RETRY
            }
        }
    }

    private fun cancelRecovery() {
        recoveryJob?.cancel()
        recoveryJob = null
    }

    private fun recordFailure(category: VirtualScreenFailureCategory, message: String, recoverable: Boolean, reason: TransportFailureKind? = null) {
        lastFailure = VirtualScreenFailureInfo(category = category, message = message, recoverable = recoverable, reason = reason?.code)
    }

    private suspend fun executeCommand(command: String, params: JsonObject): VirtualScreenResult =
        withContext(Dispatchers.IO) {
            if (!isOnline()) return@withContext VirtualScreenResult(false, "Virtual screen is not running")
            try {
                val response = request(command, params)
                VirtualScreenResult(true, response.stringField("message").orEmpty())
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                VirtualScreenResult(false, t.readableMessage())
            }
        }

    private suspend fun request(command: String, params: JsonObject): JsonObject =
        commandMutex.withLock { requestLocked(command, params) }

    private suspend fun requestLocked(command: String, params: JsonObject, screenshotEpoch: Long = 0L): JsonObject {
        val id = requestIds.incrementAndGet()
        val deferred = CompletableDeferred<JsonObject>()
        correlator.expect(id)
        pendingResponse = deferred
        pendingScreenshotEpoch = screenshotEpoch
        val response = try {
            commandInFlight = true
            sendText(command, params, id)
            withTimeoutOrNull(COMMAND_TIMEOUT_MS) { deferred.await() }
        } finally {
            commandInFlight = false
            if (pendingResponse === deferred) pendingResponse = null
            pendingScreenshotEpoch = 0L
            if (correlator.isPending(id)) {
                correlator.abandon(id)
                Log.w(TAG, VirtualScreenTransportLog.event("command_abandoned", "id" to id, "command" to command))
            }
        }
        if (response == null) throw IOException("Command timed out: $command")
        if (response.booleanField("ok") != true) {
            throw IllegalStateException(response.stringField("error") ?: "Command failed: $command")
        }
        return response
    }

    private fun sendText(command: String, params: JsonObject, id: Int = 0) {
        val json = buildJsonObject {
            if (id != 0) put("id", id)
            put("cmd", command)
            for ((key, value) in params) put(key, value)
        }
        sendFrame(VirtualScreenProtocol.text(json))
    }

    private fun sendPing() {
        val id = -heartbeatIds.incrementAndGet()
        sendText(VirtualScreenCommands.PING, JsonObject(emptyMap()), id)
    }

    private fun sendFrame(frame: VirtualScreenFrame) {
        synchronized(writeLock) {
            val stream = output ?: throw IOException("Virtual screen is not connected")
            VirtualScreenProtocol.write(stream, frame)
        }
    }

    private fun cleanupSession() {
        synchronized(connectionLock) {
            readerJob?.cancel()
            readerJob = null
            heartbeatJob?.cancel()
            heartbeatJob = null
            pendingResponse?.completeExceptionally(IOException("Virtual screen connection closed"))
            pendingResponse = null
            pendingScreenshotEpoch = 0L
            correlator.reset()
            screenshotGate.reset()
            pongGate.reset()
            heartbeat.reset()
            pendingFailure = null
            runCatching { socket?.close() }
            socket = null
            input = null
            output = null
            helper?.destroy()
            helper = null
            connectedBackend = null
            connectedUid = null
            connectedHelperPid = null
        }
    }

    private fun recycleLater(bitmap: Bitmap) {
        scope.launch {
            delay(FRAME_RECYCLE_DELAY_MS)
            if ((_state.value as? VirtualScreenState.Online)?.frame !== bitmap) {
                runCatching { bitmap.recycle() }
            }
        }
    }

    private fun reasonLabel(kind: TransportFailureKind): String = string(
        when (kind) {
            TransportFailureKind.EOF -> R.string.virtual_screen_reason_eof
            TransportFailureKind.HEARTBEAT -> R.string.virtual_screen_reason_heartbeat
            TransportFailureKind.FRAME_INVALID -> R.string.virtual_screen_reason_frame_invalid
            TransportFailureKind.IO -> R.string.virtual_screen_reason_io
        },
    )

    private fun string(resId: Int, vararg args: Any): String = context.getString(resId, *args)

    private fun backendLabel(backend: String): String = when (backend) {
        PrivilegedLauncher.BACKEND_ROOT -> "Root"
        PrivilegedLauncher.BACKEND_ADB -> "ADB"
        else -> backend
    }

    private fun nowMs(): Long = System.nanoTime() / 1_000_000L

    private class TransportFailure(val kind: TransportFailureKind, val detail: String)

    private class SessionStart(
        val ok: Boolean,
        val message: String = "",
        val category: VirtualScreenFailureCategory? = null,
    )

    private companion object {
        const val TAG = "VScreenEngine"
        const val LOOPBACK = "127.0.0.1"
        const val CONNECT_TIMEOUT_MS = 10000L
        const val HANDSHAKE_TIMEOUT_MS = 5000
        const val COMMAND_TIMEOUT_MS = 10000L
        const val SCREENSHOT_TIMEOUT_MS = 10000L
        const val FRAME_RECYCLE_DELAY_MS = 1000L
        const val INVALID_TASK_DISPLAY = -1
        const val MAX_LOG_PAYLOAD_CHARS = 120
    }
}

data class VirtualScreenLaunchReport(
    val ok: Boolean,
    val message: String,
    val packageName: String = "",
    val displayId: Int = INVALID_DISPLAY,
    val taskDisplayId: Int = INVALID_DISPLAY,
    val landedOnMain: Boolean = false,
) {
    companion object {
        const val INVALID_DISPLAY = -1
    }
}

object VirtualScreenRuntime {
    @Volatile
    private var instance: VirtualScreenEngine? = null

    fun install(engine: VirtualScreenEngine): VirtualScreenEngine {
        instance = engine
        return engine
    }

    fun engine(): VirtualScreenEngine? = instance
}
