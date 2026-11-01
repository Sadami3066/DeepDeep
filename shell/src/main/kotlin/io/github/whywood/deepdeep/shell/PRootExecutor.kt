package io.github.whywood.deepdeep.shell

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.suspendCancellableCoroutine

class PRootExecutor(
    private val prootBinary: File,
    private val prootLoader: File,
    private val rootfsDir: File,
    private val workspaceDir: File,
    private val tempDir: File,
    private val sharedStorageEnabled: () -> Boolean = { false },
    private val extraBinds: List<Pair<File, String>> = emptyList(),
    private val maxOutputBytes: Int = 64 * 1024,
    private val pidReader: (File) -> Long = ::readPidFile,
) : LinuxExecutor {
    init {
        require(maxOutputBytes > 0) { "maxOutputBytes must be positive" }
    }

    override suspend fun execute(command: String, timeoutMillis: Long): LinuxResult =
        executeStreaming(command, timeoutMillis, null)

    suspend fun executeStreaming(
        command: String,
        timeoutMillis: Long,
        onOutputLine: ((String) -> Unit)?,
    ): LinuxResult {
        require(command.isNotBlank()) { "command must not be blank" }
        require(timeoutMillis > 0) { "timeoutMillis must be positive" }

        return suspendCancellableCoroutine { continuation ->
            val runningRef = AtomicReference<RunningProcess?>()
            val cancelled = AtomicBoolean(false)
            val worker = Thread({
                try {
                    val result = executeBlocking(command, timeoutMillis, onOutputLine) { running ->
                        runningRef.set(running)
                        if (cancelled.get()) killTree(running.process, running.pid)
                    }
                    if (continuation.isActive) continuation.resumeWith(Result.success(result))
                } catch (error: Throwable) {
                    if (continuation.isActive) continuation.resumeWith(Result.failure(error))
                } finally {
                    runningRef.get()?.let { if (it.process.isAlive) killTree(it.process, it.pid) }
                    runningRef.set(null)
                }
            }, "linux-command")
            worker.isDaemon = true

            continuation.invokeOnCancellation {
                cancelled.set(true)
                runningRef.get()?.let { killTree(it.process, it.pid) }
                worker.interrupt()
            }
            if (continuation.isActive) worker.start()
        }
    }

    fun buildCommand(command: String): List<String> = buildList {
        add(prootBinary.absolutePath)
        add("-0")
        add("--link2symlink")
        add("-r")
        add(rootfsDir.absolutePath)
        add("-b")
        add("/dev")
        add("-b")
        add("/proc")
        add("-b")
        add("/sys")
        if (sharedStorageEnabled()) {
            add("-b")
            add("/storage/emulated/0:/storage/emulated/0")
            add("-b")
            add("/storage/emulated/0:/sdcard")
        }
        extraBinds.forEach { (host, guest) ->
            add("-b")
            add("${host.absolutePath}:$guest")
        }
        add("-b")
        add("${workspaceDir.absolutePath}:/workspace")
        add("-w")
        add("/workspace")
        add("--kill-on-exit")
        add("/bin/sh")
        add("-c")
        add(command)
    }

    private fun executeBlocking(
        command: String,
        timeoutMillis: Long,
        onOutputLine: ((String) -> Unit)?,
        onStarted: (RunningProcess) -> Unit,
    ): LinuxResult {
        require(prootBinary.isFile) { "PRoot binary missing: $prootBinary" }
        require(prootBinary.canExecute()) { "PRoot binary is not executable: $prootBinary" }
        require(prootLoader.isFile) { "PRoot loader missing: $prootLoader" }
        require(rootfsDir.isDirectory) { "rootfs not installed: $rootfsDir" }
        workspaceDir.mkdirs()
        tempDir.mkdirs()
        prepareBindDirectories(rootfsDir, extraBinds)
        if (sharedStorageEnabled()) {
            File(rootfsDir, "storage/emulated/0").mkdirs()
            File(rootfsDir, "sdcard").mkdirs()
        }

        val startedAt = System.nanoTime()
        val pidFile = File(tempDir, "proot-${UUID.randomUUID()}.pid")
        val running = startProcess(command, pidFile, onStarted)
        val process = running.process
        process.outputStream.close()

        val timedOut = AtomicBoolean(false)
        val watchdog = Thread({
            try {
                if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
                    timedOut.set(true)
                    killTree(process, running.pid)
                }
            } catch (_: InterruptedException) {
            }
        }, "linux-timeout")
        watchdog.isDaemon = true
        watchdog.start()

        val output = ByteArrayOutputStream()
        val lineBuffer = onOutputLine?.let { LineChunkBuffer() }
        var truncated = false
        try {
            process.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val read = try {
                        input.read(buffer)
                    } catch (_: IOException) {
                        break
                    }
                    if (read < 0) break
                    val remaining = maxOutputBytes - output.size()
                    if (remaining > 0) {
                        output.write(buffer, 0, minOf(read, remaining))
                    }
                    if (read > remaining) truncated = true
                    if (lineBuffer != null) lineBuffer.accept(buffer, read, onOutputLine)
                }
                if (lineBuffer != null) lineBuffer.finish(onOutputLine)
            }
            process.waitFor()
        } finally {
            if (process.isAlive) killTree(process, running.pid)
            watchdog.interrupt()
            runCatching { watchdog.join(500) }
            pidFile.delete()
        }

        return LinuxResult(
            exitCode = if (timedOut.get()) -1 else process.exitValue(),
            output = output.toByteArray().toString(Charsets.UTF_8),
            truncated = truncated,
            timedOut = timedOut.get(),
            durationMillis = (System.nanoTime() - startedAt) / 1_000_000,
        )
    }

    private fun startProcess(
        command: String,
        pidFile: File,
        onStarted: (RunningProcess) -> Unit,
    ): RunningProcess {
        val shell = if (File("/system/bin/sh").isFile) "/system/bin/sh" else "/bin/sh"
        val script = "echo \$\$ > \"\$1\"; shift; exec \"\$@\""
        val commandLine = listOf(shell, "-c", script, "sh", pidFile.absolutePath) + buildCommand(command)
        val process = try {
            ProcessBuilder(commandLine)
                .directory(workspaceDir)
                .redirectErrorStream(true)
                .apply {
                    environment().clear()
                    environment().putAll(
                        mapOf(
                            "HOME" to "/root",
                            "PATH" to "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
                            "TERM" to "dumb",
                            "LANG" to "C.UTF-8",
                            "TZ" to posixTimeZone(TimeZone.getDefault(), System.currentTimeMillis()),
                            "PROOT_TMP_DIR" to tempDir.absolutePath,
                            "PROOT_LOADER" to prootLoader.absolutePath,
                        ),
                    )
                }
                .start()
        } catch (error: IOException) {
            throw IllegalStateException("failed to start PRoot: ${error.message}", error)
        }

        val initial = RunningProcess(process, 0)
        onStarted(initial)
        return try {
            val ready = initial.copy(pid = pidReader(pidFile))
            onStarted(ready)
            ready
        } catch (error: Throwable) {
            killTree(process, 0)
            runCatching { pidFile.delete() }
            throw error
        }
    }

    private fun killTree(process: Process, pid: Long) {
        if (pid > 0) {
            val descendants = mutableListOf<Long>()
            collectChildren(pid, descendants, mutableSetOf())
            val targets = listOf(pid) + descendants
            signalPids(targets, SIGSTOP)
            signalPids(targets, SIGKILL)
        }
        runCatching { process.destroy() }
        runCatching { process.waitFor(500, TimeUnit.MILLISECONDS) }
        if (process.isAlive) {
            if (pid > 0) killPid(pid)
            runCatching { process.destroyForcibly() }
        }
    }

    private fun collectChildren(pid: Long, output: MutableList<Long>, visited: MutableSet<Long>) {
        if (!visited.add(pid)) return
        childPids(pid).forEach { child ->
            collectChildren(child, output, visited)
            output += child
        }
    }

    private fun childPids(pid: Long): List<Long> {
        val childrenFile = File("/proc/$pid/task/$pid/children")
        if (childrenFile.isFile) {
            val direct = runCatching {
                childrenFile.readText().trim().split(Regex("\\s+")).mapNotNull { it.toLongOrNull() }
            }.getOrDefault(emptyList())
            if (direct.isNotEmpty()) return direct
        }

        return File("/proc").listFiles().orEmpty().mapNotNull { entry ->
            val childPid = entry.name.toLongOrNull() ?: return@mapNotNull null
            val stat = runCatching { File(entry, "stat").readText() }.getOrNull() ?: return@mapNotNull null
            val close = stat.lastIndexOf(')')
            if (close < 0) return@mapNotNull null
            val fields = stat.substring(close + 2).split(' ')
            if (fields.getOrNull(1)?.toLongOrNull() == pid) childPid else null
        }
    }

    private fun signalPids(pids: List<Long>, signal: Int) {
        if (pids.isEmpty()) return
        val killBinary = listOf("/system/bin/kill", "/bin/kill").firstOrNull { File(it).isFile } ?: "kill"
        runCatching {
            ProcessBuilder(listOf(killBinary, signal.toString()) + pids.map { it.toString() })
                .start()
                .waitFor(500, TimeUnit.MILLISECONDS)
        }
    }

    private fun killPid(pid: Long) {
        signalPids(listOf(pid), SIGKILL)
    }

    private data class RunningProcess(
        val process: Process,
        val pid: Long,
    )
}

internal class LineChunkBuffer(private val limit: Int = LINE_CHUNK_LIMIT) {
    private val pending = ByteArrayOutputStream()
    private var lineCut = false

    fun accept(bytes: ByteArray, count: Int, onChunk: (String) -> Unit) {
        for (index in 0 until count) {
            val byte = bytes[index]
            if (byte == '\n'.code.toByte()) {
                if (lineCut && pending.size() == 0) {
                    lineCut = false
                } else {
                    emit(pending.toByteArray().toString(Charsets.UTF_8).trimEnd('\r'), onChunk)
                    pending.reset()
                    lineCut = false
                }
            } else {
                pending.write(byte.toInt())
                if (pending.size() >= limit) {
                    emit(flushBounded(), onChunk)
                    lineCut = true
                }
            }
        }
    }

    fun finish(onChunk: (String) -> Unit) {
        if (lineCut && pending.size() == 0) {
            lineCut = false
            return
        }
        if (pending.size() == 0) return
        emit(pending.toByteArray().toString(Charsets.UTF_8).trimEnd('\r'), onChunk)
        pending.reset()
        lineCut = false
    }

    private fun flushBounded(): String {
        val bytes = pending.toByteArray()
        val cut = utf8Cut(bytes)
        pending.reset()
        if (cut < bytes.size) pending.write(bytes, cut, bytes.size - cut)
        return bytes.copyOf(cut).toString(Charsets.UTF_8)
    }

    private fun emit(chunk: String, onChunk: (String) -> Unit) {
        runCatching { onChunk(chunk) }
    }
}

private fun utf8Cut(bytes: ByteArray): Int {
    val cut = bytes.size
    if (cut == 0) return 0
    var index = cut - 1
    var backtrack = 0
    while (index >= 0 && (bytes[index].toInt() and 0xC0) == 0x80 && backtrack < 3) {
        index--
        backtrack++
    }
    if (index < 0) return cut
    val lead = bytes[index].toInt() and 0xFF
    val length = when {
        lead and 0x80 == 0 -> 1
        lead and 0xE0 == 0xC0 -> 2
        lead and 0xF0 == 0xE0 -> 3
        lead and 0xF8 == 0xF0 -> 4
        else -> 1
    }
    return if (index + length <= cut) cut else index
}

private const val LINE_CHUNK_LIMIT = 8 * 1024
private const val SIGSTOP = -19
private const val SIGKILL = -9

private fun readPidFile(pidFile: File): Long {
    repeat(50) {
        val pid = runCatching { pidFile.readText().trim().toLong() }.getOrNull()
        if (pid != null && pid > 0) return pid
        Thread.sleep(10)
    }
    return 0
}

internal fun prepareBindDirectories(rootfsDir: File, binds: List<Pair<File, String>>) {
    binds.forEach { (host, guest) ->
        host.mkdirs()
        guestRelativePath(guest)?.let { relative -> File(rootfsDir, relative).mkdirs() }
    }
}

private fun guestRelativePath(guest: String): String? {
    val relative = guest.trim().trim('/')
    if (relative.isEmpty()) return null
    if (relative.split('/').any { it.isEmpty() || it == "." || it == ".." }) return null
    return relative
}
