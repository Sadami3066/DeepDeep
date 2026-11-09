package io.github.whywood.deepdeep.shell

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

enum class RootStatus {
    Unsupported,
    NotRequested,
    Granted,
    Denied,
    Timeout,
    Error,
}

data class RootResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val truncated: Boolean,
    val timedOut: Boolean = false,
)

internal data class RootProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean,
    val started: Boolean,
)

internal fun interface RootCommandRunner {
    suspend fun run(command: String, timeoutMillis: Long, stdin: String?): RootProcessResult
}

class RootShell internal constructor(
    private val runner: RootCommandRunner,
    private val maxOutputBytes: Int = MAX_OUTPUT_BYTES,
) {
    constructor() : this(ProcessRootCommandRunner())

    suspend fun probe(timeoutMillis: Long = DEFAULT_PROBE_TIMEOUT_MILLIS): RootStatus {
        require(timeoutMillis > 0) { "timeoutMillis must be positive" }
        val result = try {
            runner.run("id", timeoutMillis, null)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return RootStatus.Error
        }
        return when {
            !result.started -> RootStatus.Unsupported
            result.timedOut -> RootStatus.Timeout
            result.exitCode == 0 && result.stdout.contains(UID_ZERO) -> RootStatus.Granted
            else -> RootStatus.Denied
        }
    }

    suspend fun execute(command: String, timeoutMillis: Long): RootResult {
        require(command.isNotBlank()) { "command must not be blank" }
        require(timeoutMillis > 0) { "timeoutMillis must be positive" }
        val process = runner.run(command, timeoutMillis, null)
        check(process.started) { "su is not available" }
        val boundedStdout = takeUtf8(process.stdout, maxOutputBytes)
        val used = boundedStdout.toByteArray(Charsets.UTF_8).size
        val boundedStderr = takeUtf8(process.stderr, maxOutputBytes - used)
        return RootResult(
            exitCode = if (process.timedOut) NO_EXIT_CODE else process.exitCode,
            stdout = boundedStdout,
            stderr = boundedStderr,
            timedOut = process.timedOut,
            truncated = boundedStdout.length < process.stdout.length ||
                boundedStderr.length < process.stderr.length,
        )
    }
}

internal class ProcessRootCommandRunner : RootCommandRunner {
    override suspend fun run(command: String, timeoutMillis: Long, stdin: String?): RootProcessResult =
        runInterruptible(Dispatchers.IO) {
            val commandPid = File.createTempFile("deepdeep-root-", ".pid")
            val process = startRootProcess(wrapRootCommand(command, timeoutMillis, commandPid))
                ?: return@runInterruptible RootProcessResult(NO_EXIT_CODE, "", "", false, false)
            val budget = AtomicInteger(MAX_OUTPUT_BYTES + 1)
            val stdout = ByteArrayOutputStream()
            val stderr = ByteArrayOutputStream()
            val stdoutReader = startReader("root-shell-stdout", process.inputStream, stdout, budget)
            val stderrReader = startReader("root-shell-stderr", process.errorStream, stderr, budget)
            runCatching {
                process.outputStream.use { output ->
                    if (stdin != null) output.write(stdin.toByteArray(Charsets.UTF_8))
                    output.flush()
                }
            }
            val timedOut = try {
                !process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
            } catch (interrupted: InterruptedException) {
                kill(process, commandPid)
                joinReaders(stdoutReader, stderrReader)
                commandPid.delete()
                throw interrupted
            }
            if (timedOut) kill(process, commandPid)
            joinReaders(stdoutReader, stderrReader)
            commandPid.delete()
            RootProcessResult(
                exitCode = runCatching { process.exitValue() }.getOrDefault(NO_EXIT_CODE),
                stdout = stdout.toByteArray().toString(Charsets.UTF_8),
                stderr = stderr.toByteArray().toString(Charsets.UTF_8),
                timedOut = timedOut,
                started = true,
            )
        }

    private fun startRootProcess(command: String): Process? {
        for (binary in ROOT_BINARIES) {
            try {
                return ProcessBuilder(binary, "-c", command).start()
            } catch (_: IOException) {
            }
        }
        return null
    }

    private fun kill(process: Process, commandPid: File) {
        val pid = runCatching { commandPid.readText().trim().toLongOrNull() }.getOrNull()
        if (pid != null && pid > 1) runCatching {
            startRootProcess("kill -KILL -- -$pid")?.waitFor(PROCESS_KILL_WAIT_MILLIS, TimeUnit.MILLISECONDS)
        }
        runCatching { process.destroyForcibly() }
        runCatching { process.waitFor(PROCESS_KILL_WAIT_MILLIS, TimeUnit.MILLISECONDS) }
    }

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
}

internal fun takeUtf8(text: String, limit: Int): String {
    if (limit <= 0) return ""
    val bytes = text.toByteArray(Charsets.UTF_8)
    if (bytes.size <= limit) return text
    var cut = limit
    while (cut > 0 && (bytes[cut].toInt() and 0xC0) == 0x80) cut--
    return String(bytes, 0, cut, Charsets.UTF_8)
}

private const val MAX_OUTPUT_BYTES = 64 * 1024
private const val DEFAULT_PROBE_TIMEOUT_MILLIS = 60_000L
private const val READER_BUFFER_BYTES = 8 * 1024
private const val READER_JOIN_TIMEOUT_MILLIS = 1_000L
private const val PROCESS_KILL_WAIT_MILLIS = 1_000L
internal fun wrapRootCommand(command: String, timeoutMillis: Long, pidFile: File? = null): String {
    val seconds = String.format(Locale.US, "%.1fs", timeoutMillis / 1000.0)
    val escaped = command.replace("'", "'\\''")
    if (pidFile != null) {
        val body = "echo ${'$'}${'$'} > '${pidFile.absolutePath.replace("'", "'\\''")}'; exec sh -c '$escaped'"
        return "exec setsid sh -c '${body.replace("'", "'\\''")}'"
    }
    return "timeout -s KILL $seconds sh -c '$escaped'"
}

private const val NO_EXIT_CODE = -1
private const val OUTER_TIMEOUT_GRACE_MILLIS = 3_000L
private const val UID_ZERO = "uid=0"
private val ROOT_BINARIES = listOf("su", "/system/bin/su", "/system/xbin/su", "/sbin/su", "/debug_ramdisk/su")
