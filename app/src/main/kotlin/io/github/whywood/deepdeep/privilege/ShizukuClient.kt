package io.github.whywood.deepdeep.privilege

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import io.github.whywood.deepdeep.runtime.RootCommandExecutor
import io.github.whywood.deepdeep.runtime.RootCommandResult
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.lang.reflect.Method
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runInterruptible
import rikka.shizuku.Shizuku

class ShizukuClient(context: Context) : RootCommandExecutor {
    private val appContext = context.applicationContext
    private val stateFlow = MutableStateFlow(ShizukuState())
    val state: StateFlow<ShizukuState> = stateFlow.asStateFlow()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDeadListener = Shizuku.OnBinderDeadListener { refresh() }
    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    private val newProcessMethod: Method? by lazy {
        runCatching {
            Shizuku::class.java
                .getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java,
                )
                .apply { isAccessible = true }
        }.getOrNull()
    }

    init {
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
        refresh()
    }

    fun refresh(): ShizukuState {
        val running = isRunning()
        val current = ShizukuState(
            installed = isInstalled(),
            running = running,
            granted = running && isPermissionGranted(),
            version = if (running) serverVersion() else -1,
            uid = if (running) serverUid() else -1,
        )
        stateFlow.value = current
        return current
    }

    fun requestPermission(activity: Activity) {
        when {
            !isRunning() -> openApp(activity)
            isPermissionGranted() -> refresh()
            shouldShowRationale() -> openApp(activity)
            else -> runCatching { Shizuku.requestPermission(REQUEST_PERMISSION_CODE) }
        }
    }

    fun openApp(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE) ?: return
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun openInstallPage(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_INSTALL_URL))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    override suspend fun execute(command: String, timeoutMillis: Long): RootCommandResult =
        runInterruptible(Dispatchers.IO) { executeBlocking(command, timeoutMillis) }

    private fun executeBlocking(command: String, timeoutMillis: Long): RootCommandResult {
        require(command.isNotBlank()) { "command must not be blank" }
        require(timeoutMillis > 0) { "timeoutMillis must be positive" }
        val process = startProcess(wrapCommand(command, timeoutMillis))
        runCatching { process.outputStream.close() }
        val stdin = process.inputStream
        val stderrStream = process.errorStream
        val budget = AtomicInteger(MAX_OUTPUT_BYTES + 1)
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val stdoutReader = startReader("shizuku-shell-stdout", stdin, stdout, budget)
        val stderrReader = startReader("shizuku-shell-stderr", stderrStream, stderr, budget)
        val timedOut = try {
            waitForExit(process, timeoutMillis)
        } catch (interrupted: InterruptedException) {
            kill(process, stdin, stderrStream)
            joinReaders(stdoutReader, stderrReader)
            throw interrupted
        }
        if (timedOut) kill(process, stdin, stderrStream)
        joinReaders(stdoutReader, stderrReader)
        val rawStdout = stdout.toByteArray().toString(Charsets.UTF_8)
        val rawStderr = stderr.toByteArray().toString(Charsets.UTF_8)
        val boundedStdout = takeUtf8(rawStdout, MAX_OUTPUT_BYTES)
        val used = boundedStdout.toByteArray(Charsets.UTF_8).size
        val boundedStderr = takeUtf8(rawStderr, MAX_OUTPUT_BYTES - used)
        return RootCommandResult(
            exitCode = if (timedOut) NO_EXIT_CODE else exitCodeOf(process),
            stdout = boundedStdout,
            stderr = boundedStderr,
            truncated = boundedStdout.length < rawStdout.length || boundedStderr.length < rawStderr.length,
        )
    }

    private fun startProcess(wrapped: String): Process {
        val method = newProcessMethod ?: throw IllegalStateException("Shizuku 不可用")
        val process = runCatching {
            method.invoke(null, arrayOf(SHELL_PATH, "-c", wrapped), null, null) as? Process
        }.getOrNull() ?: throw IllegalStateException("Shizuku 未运行或未授权")
        return process
    }

    private fun waitForExit(process: Process, timeoutMillis: Long): Boolean {
        val waiter = Thread({
            runCatching { process.waitFor() }
        }, "shizuku-shell-wait").apply {
            isDaemon = true
            start()
        }
        waiter.join(timeoutMillis + OUTER_TIMEOUT_GRACE_MILLIS)
        return waiter.isAlive
    }

    private fun exitCodeOf(process: Process): Int = runCatching { process.exitValue() }.getOrDefault(NO_EXIT_CODE)

    private fun kill(process: Process, vararg streams: InputStream) {
        runCatching { process.destroy() }
        streams.forEach { stream -> runCatching { stream.close() } }
    }

    private fun isRunning(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    private fun isPermissionGranted(): Boolean = runCatching {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    private fun shouldShowRationale(): Boolean =
        runCatching { Shizuku.shouldShowRequestPermissionRationale() }.getOrDefault(false)

    private fun serverVersion(): Int = runCatching { Shizuku.getVersion() }.getOrDefault(-1)

    private fun serverUid(): Int = runCatching { Shizuku.getUid() }.getOrDefault(-1)

    private fun isInstalled(): Boolean = runCatching {
        appContext.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE) != null
    }.getOrDefault(false)

    private fun startReader(
        name: String,
        stream: InputStream,
        sink: ByteArrayOutputStream,
        budget: AtomicInteger,
    ): Thread = Thread({
        try {
            stream.use { input ->
                val buffer = ByteArray(READER_BUFFER_BYTES)
                while (true) {
                    val read = try {
                        input.read(buffer)
                    } catch (_: IOException) {
                        break
                    }
                    if (read < 0) break
                    val allowed = reserve(budget, read)
                    if (allowed > 0) sink.write(buffer, 0, allowed)
                }
            }
        } catch (_: IOException) {
        }
    }, name).apply {
        isDaemon = true
        start()
    }

    private fun reserve(budget: AtomicInteger, requested: Int): Int {
        while (true) {
            val remaining = budget.get()
            if (remaining <= 0) return 0
            val allowed = minOf(requested, remaining)
            if (budget.compareAndSet(remaining, remaining - allowed)) return allowed
        }
    }

    private fun joinReaders(vararg readers: Thread) {
        readers.forEach { reader ->
            runCatching { reader.join(READER_JOIN_TIMEOUT_MILLIS) }
            if (reader.isAlive) reader.interrupt()
        }
    }

    private fun wrapCommand(command: String, timeoutMillis: Long): String {
        val seconds = String.format(Locale.US, "%.1fs", timeoutMillis / 1000.0)
        val escaped = command.replace("'", "'\\''")
        return "timeout -s KILL $seconds sh -c '$escaped'"
    }

    private fun takeUtf8(text: String, limit: Int): String {
        if (limit <= 0) return ""
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.size <= limit) return text
        var cut = limit
        while (cut > 0 && (bytes[cut].toInt() and 0xC0) == 0x80) cut--
        return String(bytes, 0, cut, Charsets.UTF_8)
    }
}

private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
private const val SHIZUKU_INSTALL_URL = "https://github.com/RikkaApps/Shizuku/releases"
private const val REQUEST_PERMISSION_CODE = 0x5A1
private const val SHELL_PATH = "/system/bin/sh"
private const val MAX_OUTPUT_BYTES = 64 * 1024
private const val READER_BUFFER_BYTES = 8 * 1024
private const val READER_JOIN_TIMEOUT_MILLIS = 1_000L
private const val OUTER_TIMEOUT_GRACE_MILLIS = 3_000L
private const val NO_EXIT_CODE = -1
