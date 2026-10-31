package io.github.whywood.deepdeep.vscreen

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.util.Log
import io.github.whywood.deepdeep.ui.vscreen.VirtualScreenActivity
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
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
    private val requestIds = AtomicInteger()
    private val writeLock = Any()

    private var socket: LocalSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private var helper: HelperProcess? = null
    private var readerJob: Job? = null
    private var pendingResponse: CompletableDeferred<JsonObject>? = null
    private var pendingBinary: CompletableDeferred<ByteArray>? = null

    @Volatile
    private var connectedBackend: String? = null

    @Volatile
    private var connectedUid: Int? = null

    val backend: String?
        get() = connectedBackend

    val helperUid: Int?
        get() = connectedUid

    fun isOnline(): Boolean = _state.value is VirtualScreenState.Online

    override suspend fun start(): VirtualScreenResult = lifecycleMutex.withLock {
        when (_state.value) {
            is VirtualScreenState.Online -> return@withLock VirtualScreenResult(true, "虚拟屏已启动")
            VirtualScreenState.Starting -> return@withLock VirtualScreenResult(false, "虚拟屏正在启动")
            else -> Unit
        }
        _state.value = VirtualScreenState.Starting
        val result = withContext(Dispatchers.IO) { startSession() }
        if (!result.ok) _state.value = VirtualScreenState.Failed(result.message)
        result
    }

    override suspend fun stop() {
        lifecycleMutex.withLock {
            if (_state.value is VirtualScreenState.Offline) return@withLock
            withContext(Dispatchers.IO) {
                runCatching { sendText(VirtualScreenCommands.STOP, JsonObject(emptyMap())) }
                cleanupSession()
            }
            val frame = (_state.value as? VirtualScreenState.Online)?.frame
            _state.value = VirtualScreenState.Offline
            frame?.let { recycleLater(it) }
        }
    }

    override suspend fun launch(packageName: String): VirtualScreenResult {
        val name = packageName.trim()
        if (name.isEmpty()) return VirtualScreenResult(false, "缺少包名")
        return executeCommand(
            VirtualScreenCommands.LAUNCH,
            buildJsonObject { put("packageName", name) },
        )
    }

    override suspend fun tap(x: Int, y: Int): VirtualScreenResult {
        if (x < 0 || y < 0) return VirtualScreenResult(false, "坐标不能为负数")
        return executeCommand(
            VirtualScreenCommands.TAP,
            buildJsonObject {
                put("x", x)
                put("y", y)
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
        if (keyName.isEmpty()) return VirtualScreenResult(false, "缺少按键名")
        return executeCommand(
            VirtualScreenCommands.KEY,
            buildJsonObject { put("key", keyName) },
        )
    }

    override suspend fun text(value: String): VirtualScreenResult {
        if (value.isEmpty()) return VirtualScreenResult(false, "文本不能为空")
        return executeCommand(
            VirtualScreenCommands.TEXT,
            buildJsonObject { put("text", value) },
        )
    }

    override suspend fun screenshot(): ByteArray? = withContext(Dispatchers.IO) {
        if (!isOnline()) return@withContext null
        commandMutex.withLock {
            val binary = CompletableDeferred<ByteArray>()
            pendingBinary = binary
            try {
                val response = requestLocked(VirtualScreenCommands.SCREENSHOT, JsonObject(emptyMap()))
                val kind = response.stringField("kind")
                if (kind != null && kind != "screenshot") return@withLock null
                withTimeoutOrNull(SCREENSHOT_TIMEOUT_MS) { binary.await() }
            } catch (t: Throwable) {
                null
            } finally {
                if (pendingBinary === binary) pendingBinary = null
            }
        }
    }

    override fun openViewer(context: Context) {
        val intent = Intent(context, VirtualScreenActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private fun startSession(): VirtualScreenResult {
        val backends = launcher.backends()
        if (backends.isEmpty()) return VirtualScreenResult(false, "需要 Shizuku 或 Root 权限")
        val socketName = "deepdeep-vscreen-" + UUID.randomUUID().toString().replace("-", "")
        val nonce = UUID.randomUUID().toString().replace("-", "")
        val errors = mutableListOf<String>()
        for (backend in backends) {
            try {
                tryBackend(backend, socketName, nonce)
                return VirtualScreenResult(true, "虚拟屏已启动")
            } catch (t: Throwable) {
                Log.w(TAG, "$backend 启动虚拟屏失败", t)
                errors += "${backendLabel(backend)}：${t.readableMessage()}"
                cleanupSession()
            }
        }
        return VirtualScreenResult(false, "虚拟屏启动失败：" + errors.joinToString("；"))
    }

    private fun tryBackend(backend: String, socketName: String, nonce: String) {
        val server = LocalServerSocket(socketName)
        try {
            helper = launcher.start(backend, socketName, nonce)
            val client = accept(server) ?: throw IOException("等待 helper 连接超时")
            socket = client
        input = BufferedInputStream(client.inputStream)
        output = BufferedOutputStream(client.outputStream)
        client.soTimeout = HANDSHAKE_TIMEOUT_MS
        val hello = readFrameJson() ?: throw IOException("helper 未发送握手信息")
        val uid = hello.intField("uid") ?: -1
        if (uid != VirtualScreenCommands.SHELL_UID) {
            throw IOException("helper 身份为 uid=$uid，需要 shell(2000)")
        }
        if (hello.stringField("nonce") != nonce) {
            throw IOException("helper 握手 nonce 不匹配")
        }
        connectedUid = uid
        connectedBackend = backend
        val id = requestIds.incrementAndGet()
        sendText(
            VirtualScreenCommands.START,
            buildJsonObject {
                put("id", id)
                put("width", VirtualScreenCommands.DEFAULT_WIDTH)
                put("height", VirtualScreenCommands.DEFAULT_HEIGHT)
                put("dpi", VirtualScreenCommands.DEFAULT_DPI)
            },
        )
        val response = readResponse(id) ?: throw IOException("helper 未返回启动结果")
        if (response.booleanField("ok") != true) {
            throw IOException(response.stringField("error") ?: "启动虚拟屏失败")
        }
        val displayId = response.intField("displayId") ?: throw IOException("helper 未返回 displayId")
        val width = response.intField("width") ?: VirtualScreenCommands.DEFAULT_WIDTH
        val height = response.intField("height") ?: VirtualScreenCommands.DEFAULT_HEIGHT
        client.soTimeout = 0
        _state.value = VirtualScreenState.Online(displayId, width, height, backend, null)
        readerJob = scope.launch { readLoop(client) }
        } finally {
            runCatching { server.close() }
        }
    }

    private fun accept(server: LocalServerSocket): LocalSocket? {
        val accepted = AtomicReference<LocalSocket?>(null)
        val thread = Thread({
            accepted.set(runCatching { server.accept() }.getOrNull())
        }, "vscreen-accept")
        thread.isDaemon = true
        thread.start()
        thread.join(CONNECT_TIMEOUT_MS)
        if (thread.isAlive) {
            runCatching { server.close() }
            thread.join(500)
        }
        return accepted.get()
    }

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

    private fun readLoop(client: LocalSocket) {
        try {
            while (true) {
                val frame = VirtualScreenProtocol.read(input ?: break) ?: break
                when (frame.kind) {
                    VirtualScreenFrameKind.TEXT -> handleTextFrame(frame)
                    VirtualScreenFrameKind.BINARY -> handleStreamFrame(frame.payload)
                    VirtualScreenFrameKind.SCREENSHOT -> handleScreenshotFrame(frame.payload)
                }
            }
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) return
            Log.w(TAG, "读取 helper 失败", t)
        }
        failConnection(client)
    }

    private fun handleTextFrame(frame: VirtualScreenFrame) {
        val json = runCatching { VirtualScreenProtocol.json(frame) }.getOrNull() ?: return
        val id = json.intField("id") ?: return
        pendingResponse?.complete(json)
    }

    private fun handleScreenshotFrame(bytes: ByteArray) {
        val screenshot = pendingBinary ?: return
        if (!screenshot.isCompleted) screenshot.complete(bytes)
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

    private fun failConnection(client: LocalSocket) {
        if (socket !== client) return
        val online = _state.value as? VirtualScreenState.Online ?: return
        cleanupSession()
        _state.value = VirtualScreenState.Failed("虚拟屏连接已断开")
        online.frame?.let { recycleLater(it) }
    }

    private suspend fun executeCommand(command: String, params: JsonObject): VirtualScreenResult =
        withContext(Dispatchers.IO) {
            if (!isOnline()) return@withContext VirtualScreenResult(false, "虚拟屏未启动")
            try {
                val response = request(command, params)
                VirtualScreenResult(true, response.stringField("message").orEmpty())
            } catch (t: Throwable) {
                VirtualScreenResult(false, t.readableMessage())
            }
        }

    private suspend fun request(command: String, params: JsonObject): JsonObject =
        commandMutex.withLock { requestLocked(command, params) }

    private suspend fun requestLocked(command: String, params: JsonObject): JsonObject {
        val id = requestIds.incrementAndGet()
        val deferred = CompletableDeferred<JsonObject>()
        pendingResponse = deferred
        val response = try {
            sendText(command, params, id)
            withTimeoutOrNull(COMMAND_TIMEOUT_MS) { deferred.await() }
                ?: throw IOException("命令超时：$command")
        } finally {
            if (pendingResponse === deferred) pendingResponse = null
        }
        if (response.booleanField("ok") != true) {
            throw IllegalStateException(response.stringField("error") ?: "命令失败：$command")
        }
        return response
    }

    private fun sendText(command: String, params: JsonObject, id: Int = 0) {
        val json = buildJsonObject {
            if (id > 0) put("id", id)
            put("cmd", command)
            for ((key, value) in params) put(key, value)
        }
        sendFrame(VirtualScreenProtocol.text(json))
    }

    private fun sendFrame(frame: VirtualScreenFrame) {
        synchronized(writeLock) {
            val stream = output ?: throw IOException("虚拟屏未连接")
            VirtualScreenProtocol.write(stream, frame)
        }
    }

    private fun cleanupSession() {
        readerJob?.cancel()
        readerJob = null
        pendingResponse?.completeExceptionally(IOException("虚拟屏连接已关闭"))
        pendingResponse = null
        pendingBinary?.completeExceptionally(IOException("虚拟屏连接已关闭"))
        pendingBinary = null
        runCatching { socket?.close() }
        socket = null
        input = null
        output = null
        helper?.destroy()
        helper = null
        connectedBackend = null
        connectedUid = null
    }

    private fun recycleLater(bitmap: Bitmap) {
        scope.launch {
            delay(FRAME_RECYCLE_DELAY_MS)
            if ((_state.value as? VirtualScreenState.Online)?.frame !== bitmap) {
                runCatching { bitmap.recycle() }
            }
        }
    }

    private fun backendLabel(backend: String): String = when (backend) {
        PrivilegedLauncher.BACKEND_ROOT -> "Root"
        PrivilegedLauncher.BACKEND_SHIZUKU -> "Shizuku"
        else -> backend
    }

    private companion object {
        const val TAG = "VScreenEngine"
        const val CONNECT_TIMEOUT_MS = 5000L
        const val CONNECT_RETRY_MS = 100L
        const val HANDSHAKE_TIMEOUT_MS = 5000
        const val COMMAND_TIMEOUT_MS = 10000L
        const val SCREENSHOT_TIMEOUT_MS = 10000L
        const val FRAME_RECYCLE_DELAY_MS = 1000L
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
