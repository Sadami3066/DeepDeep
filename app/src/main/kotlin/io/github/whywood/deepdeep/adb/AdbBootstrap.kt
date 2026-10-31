package io.github.whywood.deepdeep.adb

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import io.github.muntashirakon.adb.AdbPairingRequiredException
import io.github.muntashirakon.adb.AdbStream
import io.github.muntashirakon.adb.android.AdbMdns
import io.github.whywood.deepdeep.runtime.RootCommandResult
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class AdbEndpoint(val host: String, val port: Int)

class AdbBootstrap(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val notifier = AdbPairingNotifier(appContext)
    private val operationLock = Mutex()
    private val manager by lazy { LocalAdbManager(appContext) }
    private val shell by lazy { ShellClient(daemonPort, token) }
    private val mutableState =
        MutableStateFlow(AdbBridgeState(paired = preferences.getBoolean(KEY_PAIRED, false)))

    val state: StateFlow<AdbBridgeState> = mutableState.asStateFlow()

    val daemonPort: Int = DAEMON_PORT

    val token: String
        get() = preferences.getString(KEY_TOKEN, null) ?: UUID.randomUUID().toString().replace("-", "").also {
            preferences.edit().putString(KEY_TOKEN, it).apply()
        }

    val paired: Boolean
        get() = preferences.getBoolean(KEY_PAIRED, false)

    suspend fun isDaemonAlive(): Boolean = withContext(Dispatchers.IO) { shell.isAlive() }

    suspend fun refreshState() {
        val alive = isDaemonAlive()
        if (alive) markPaired(true)
        mutableState.update { it.refreshed(alive) }
    }

    suspend fun startPairing(): Result<Unit> = withContext(Dispatchers.IO) {
        mutableState.update { it.copy(error = null) }
        if (notifier.canNotify()) notifier.notifyCodeRequired()
        withContext(Dispatchers.Main) { openDevelopmentSettings() }
        Result.success(Unit)
    }

    suspend fun pairAndStart(code: String): Result<Unit> = withContext(Dispatchers.IO) {
        val normalized = code.trim()
        if (!PAIRING_CODE_PATTERN.matches(normalized)) {
            return@withContext failNow(AdbBridgeError.PAIRING_CODE_INVALID)
        }
        operationLock.withLock {
            mutableState.update { it.working() }
            runCatching {
                requireWirelessDebugging()
                pairKey(normalized)
                markPaired(true)
                mutableState.update { it.pairingSucceeded() }
            }.fold(
                onSuccess = { ensureDaemonUnderLock() },
                onFailure = { failWith(it) },
            )
        }
    }

    suspend fun connectAndStart(): Result<Unit> = withContext(Dispatchers.IO) {
        operationLock.withLock {
            mutableState.update { it.working() }
            ensureDaemonUnderLock()
        }
    }

    suspend fun handlePairingReply(code: String?) {
        if (mutableState.value.busy) return
        val normalized = code?.trim().orEmpty()
        if (!PAIRING_CODE_PATTERN.matches(normalized)) {
            failNow(AdbBridgeError.PAIRING_CODE_INVALID)
            notifier.notifyFailed(AdbBridgeError.PAIRING_CODE_INVALID)
            return
        }
        notifier.notifyPairingStarted()
        val result = pairAndStart(normalized)
        if (result.isSuccess) {
            notifier.notifyPaired()
        } else {
            notifier.notifyFailed(mutableState.value.error ?: AdbBridgeError.PAIRING_FAILED)
        }
    }

    suspend fun exec(command: String, timeoutMillis: Long): RootCommandResult = withContext(Dispatchers.IO) {
        ensureDaemon().exceptionOrNull()?.let { failure ->
            val error = (failure as? AdbBridgeException)?.error
            throw IllegalStateException(notifier.errorText(error ?: AdbBridgeError.CONNECT_FAILED), failure)
        }
        val timeoutSeconds = (timeoutMillis / 1000).coerceIn(1, MAX_EXEC_SECONDS)
        val raw = shell.exec(command, timeoutSeconds)
        val match = EXIT_PATTERN.find(raw)
        val exitCode = match?.groupValues?.get(1)?.toIntOrNull() ?: -1
        val output = if (match == null) raw else raw.removeRange(match.range)
        val truncated = utf8ByteLength(output) > MAX_OUTPUT_BYTES
        RootCommandResult(
            exitCode = exitCode,
            stdout = if (truncated) takeUtf8(output, MAX_OUTPUT_BYTES) else output,
            stderr = "",
            truncated = truncated,
        )
    }

    suspend fun startHelper(port: Int, nonce: String): Int? {
        if (ensureDaemon().isFailure) return null
        return shell.startHelper(appContext.applicationInfo.sourceDir, port, nonce)
    }

    suspend fun killHelper(pid: Int) {
        runCatching { shell.killHelper(pid) }
    }

    private suspend fun ensureDaemon(): Result<Unit> = withContext(Dispatchers.IO) {
        operationLock.withLock { ensureDaemonUnderLock() }
    }

    private suspend fun ensureDaemonUnderLock(): Result<Unit> {
        if (shell.isAlive()) {
            markPaired(true)
            mutableState.update { it.daemonStarted() }
            return Result.success(Unit)
        }
        mutableState.update { it.working() }
        return runCatching {
            requireWirelessDebugging()
            connectAndStartDaemon()
            markPaired(true)
        }.fold(
            onSuccess = {
                mutableState.update { it.daemonStarted() }
                Result.success(Unit)
            },
            onFailure = { failWith(it) },
        )
    }

    private suspend fun connectAndStartDaemon() {
        connectToServer()
        try {
            startDaemon()
        } finally {
            runCatching { manager.disconnect() }
        }
        var alive = false
        repeat(DAEMON_HEALTH_ATTEMPTS) {
            if (!alive) {
                alive = shell.isAlive()
                if (!alive) delay(DAEMON_HEALTH_INTERVAL_MS)
            }
        }
        if (!alive) throw AdbBridgeException(AdbBridgeError.DAEMON_START_FAILED)
    }

    private suspend fun connectToServer() {
        var unauthorized: AdbPairingRequiredException? = null
        var lastFailure: Exception? = null
        suspend fun tryEndpoint(endpoint: AdbEndpoint): Boolean {
            for (host in hostsOf(endpoint)) {
                try {
                    manager.connect(host, endpoint.port)
                    return true
                } catch (pairingRequired: AdbPairingRequiredException) {
                    unauthorized = pairingRequired
                } catch (failure: Exception) {
                    lastFailure = failure
                }
            }
            return false
        }
        val cachedPort = preferences.getInt(KEY_CONNECT_PORT, 0)
        if (cachedPort > 0 && tryEndpoint(AdbEndpoint(LOOPBACK, cachedPort))) {
            Log.i(TAG, "connected with cached port $cachedPort")
            return
        }
        for (attempt in 1..CONNECT_DISCOVERY_ATTEMPTS) {
            val endpoint = discover(AdbMdns.SERVICE_TYPE_TLS_CONNECT, CONNECT_DISCOVERY_TIMEOUT_MS)
            if (endpoint == null) {
                Log.w(TAG, "tls connect service not found: attempt=$attempt")
                continue
            }
            if (tryEndpoint(endpoint)) {
                preferences.edit().putInt(KEY_CONNECT_PORT, endpoint.port).apply()
                return
            }
            Log.w(TAG, "tls connect failed: port=${endpoint.port} attempt=$attempt", lastFailure)
        }
        unauthorized?.let { throw AdbBridgeException(AdbBridgeError.NOT_PAIRED, it) }
        throw AdbBridgeException(AdbBridgeError.CONNECT_FAILED, lastFailure)
    }

    private suspend fun pairKey(code: String) {
        val endpoint = discover(AdbMdns.SERVICE_TYPE_TLS_PAIRING, PAIR_DISCOVERY_TIMEOUT_MS)
            ?: throw AdbBridgeException(AdbBridgeError.NO_PAIRING_SERVICE)
        var lastFailure: Throwable? = null
        for (host in hostsOf(endpoint)) {
            val outcome = runCatching { manager.pair(host, endpoint.port, code) }
            if (outcome.getOrNull() == true) {
                Log.i(TAG, "paired with own key")
                return
            }
            lastFailure = outcome.exceptionOrNull() ?: lastFailure
        }
        throw AdbBridgeException(AdbBridgeError.PAIRING_FAILED, lastFailure)
    }

    private fun requireWirelessDebugging() {
        val enabled = runCatching {
            Settings.Global.getInt(appContext.contentResolver, SETTING_ADB_WIFI_ENABLED, 0) == 1
        }.getOrDefault(true)
        Log.i(TAG, "wireless debugging enabled=$enabled")
        if (!enabled) throw AdbBridgeException(AdbBridgeError.WIRELESS_DEBUGGING_DISABLED)
    }

    private fun failNow(error: AdbBridgeError): Result<Unit> {
        Log.w(TAG, "adb bridge error: $error")
        mutableState.update { it.failed(error) }
        return Result.failure(AdbBridgeException(error))
    }

    private fun failWith(failure: Throwable): Result<Unit> {
        val error = (failure as? AdbBridgeException)?.error ?: AdbBridgeError.CONNECT_FAILED
        Log.w(TAG, "adb bridge error: $error", failure)
        if (error == AdbBridgeError.NOT_PAIRED) {
            markPaired(false)
            mutableState.update { it.trustRevoked() }
        } else {
            mutableState.update { it.failed(error) }
        }
        return Result.failure(if (failure is AdbBridgeException) failure else AdbBridgeException(error, failure))
    }

    private fun markPaired(value: Boolean) {
        preferences.edit().putBoolean(KEY_PAIRED, value).apply()
    }

    private fun openDevelopmentSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { appContext.startActivity(intent) }
    }

    private fun startDaemon() {
        val apk = appContext.applicationInfo.sourceDir
        val daemonClass = ShellDaemon::class.java.name
        val script = buildString {
            append("for p in \$(pidof deepdeep-shd); do kill -9 \$p 2>/dev/null; done\n")
            append("sleep 0.3\n")
            append("rm -rf /data/local/tmp/dalvik-cache\n")
            append("rm -f /data/local/tmp/shd.log\n")
            append("CLASSPATH=")
            append(apk)
            append(" nohup /system/bin/setsid /system/bin/app_process /system/bin --nice-name=deepdeep-shd ")
            append(daemonClass)
            append(' ')
            append(daemonPort)
            append(' ')
            append(token)
            append(" </dev/null >/data/local/tmp/shd.log 2>&1 &\n")
            append("sleep 1\n")
        }
        Log.i(TAG, "igniting shell daemon")
        val stream: AdbStream = manager.openStream("shell:$script")
        try {
            val reader = Thread({
                runCatching { stream.openInputStream().readBytes() }
            }, "adb-daemon-drain").apply {
                isDaemon = true
                start()
            }
            reader.join(DAEMON_START_WAIT_MS)
        } finally {
            runCatching { stream.close() }
        }
    }

    private suspend fun discover(serviceType: String, timeoutMs: Long): AdbEndpoint? = withTimeoutOrNull(timeoutMs) {
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine<AdbEndpoint?> { continuation ->
                var discovery: AdbMdns? = null
                discovery = AdbMdns(appContext, serviceType) { address, port ->
                    if (address != null && port > 0 && continuation.isActive) {
                        runCatching { discovery?.stop() }
                        continuation.resume(AdbEndpoint(address.hostAddress ?: LOOPBACK, port))
                    }
                }
                continuation.invokeOnCancellation { runCatching { discovery?.stop() } }
                discovery.start()
            }
        }
    }

    private fun hostsOf(endpoint: AdbEndpoint): List<String> =
        listOf(endpoint.host, LOOPBACK).distinct()

    private companion object {
        const val TAG = "AdbBootstrap"
        const val PREFS = "adb_bridge"
        const val KEY_TOKEN = "token"
        const val KEY_PAIRED = "paired"
        const val LOOPBACK = "127.0.0.1"
        const val SETTING_ADB_WIFI_ENABLED = "adb_wifi_enabled"
        const val DAEMON_PORT = 18242
        const val PAIR_DISCOVERY_TIMEOUT_MS = 8000L
        const val CONNECT_DISCOVERY_TIMEOUT_MS = 8000L
        const val CONNECT_DISCOVERY_ATTEMPTS = 3
        const val KEY_CONNECT_PORT = "connect_port"
        const val DAEMON_START_WAIT_MS = 1500L
        const val DAEMON_HEALTH_ATTEMPTS = 24
        const val DAEMON_HEALTH_INTERVAL_MS = 250L
        const val MAX_EXEC_SECONDS = 120L
        const val MAX_OUTPUT_BYTES = 64 * 1024
        val PAIRING_CODE_PATTERN = Regex("\\d{6}")
        val EXIT_PATTERN = Regex("\\[EXIT=(-?\\d+)]\\s*$")
    }
}

internal fun takeUtf8(text: String, limitBytes: Int): String {
    if (limitBytes <= 0) return ""
    val bytes = text.toByteArray(Charsets.UTF_8)
    if (bytes.size <= limitBytes) return text
    var cut = limitBytes
    while (cut > 0 && (bytes[cut].toInt() and 0xC0) == 0x80) cut--
    return String(bytes, 0, cut, Charsets.UTF_8)
}

internal fun utf8ByteLength(text: String): Int = text.toByteArray(Charsets.UTF_8).size
