package io.github.whywood.deepdeep.shell

import java.io.File
import java.io.IOException

class ChrootExecutor internal constructor(
    private val linuxDir: File,
    private val workspaceDir: File,
    private val sharedStorageEnabled: () -> Boolean,
    private val resolvConf: () -> File?,
    private val maxOutputBytes: Int,
    private val runner: RootCommandRunner,
) : LinuxExecutor {
    constructor(
        linuxDir: File,
        workspaceDir: File,
        sharedStorageEnabled: () -> Boolean = { false },
        resolvConf: () -> File? = { null },
        maxOutputBytes: Int = 64 * 1024,
    ) : this(
        linuxDir = linuxDir,
        workspaceDir = workspaceDir,
        sharedStorageEnabled = sharedStorageEnabled,
        resolvConf = resolvConf,
        maxOutputBytes = maxOutputBytes,
        runner = ProcessRootCommandRunner(),
    )

    init {
        require(maxOutputBytes > 0) { "maxOutputBytes must be positive" }
    }

    override suspend fun execute(command: String, timeoutMillis: Long): LinuxResult =
        runCommand(command, timeoutMillis, null)

    suspend fun executeStreaming(
        command: String,
        timeoutMillis: Long,
        onOutputLine: (String) -> Unit,
    ): LinuxResult = runCommand(command, timeoutMillis, onOutputLine)

    private suspend fun runCommand(
        command: String,
        timeoutMillis: Long,
        onOutputLine: ((String) -> Unit)?,
    ): LinuxResult {
        require(command.isNotBlank()) { "command must not be blank" }
        require(timeoutMillis > 0) { "timeoutMillis must be positive" }
        val resolv = runCatching { resolvConf() }.getOrNull()
        val suCommand = buildCommand(
            script = File(linuxDir, RUN_SCRIPT_NAME),
            workspaceDir = workspaceDir,
            sharedStorage = sharedStorageEnabled(),
            resolvConf = resolv,
        )
        val startedAt = System.nanoTime()
        val callback = onOutputLine?.let { handler ->
            { line: String ->
                try {
                    handler(line)
                } catch (_: Exception) {
                }
            }
        }
        val process = runner.run(suCommand, timeoutMillis, stdin = command, onOutputLine = callback)
        val durationMillis = (System.nanoTime() - startedAt) / 1_000_000
        if (!process.started) throw IOException("su is not available")
        val combined = mergeOutput(process.stdout, process.stderr)
        val (output, truncated) = boundOutput(combined)
        return LinuxResult(
            exitCode = if (process.timedOut) NO_EXIT_CODE else process.exitCode,
            output = output,
            truncated = truncated,
            timedOut = process.timedOut,
            durationMillis = durationMillis,
            fullOutputPath = null,
        )
    }

    private fun boundOutput(text: String): Pair<String, Boolean> {
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.size <= maxOutputBytes) return text to false
        val tail = bytes.copyOfRange(bytes.size - maxOutputBytes, bytes.size)
        var offset = 0
        while (offset < tail.size && (tail[offset].toInt() and 0xC0) == 0x80) offset++
        return String(tail, offset, tail.size - offset, Charsets.UTF_8) to true
    }

    internal companion object {
        const val RUN_SCRIPT_NAME = "run.sh"
        private const val NO_EXIT_CODE = -1
        private const val USER_PATH_PREFIX = "/data/user/0/"
        private const val DATA_PATH_PREFIX = "/data/data/"

        internal fun buildCommand(
            script: File,
            workspaceDir: File,
            sharedStorage: Boolean,
            resolvConf: File?,
        ): String = listOf(
            shellQuote(script.absolutePath),
            shellQuote(suPath(workspaceDir.absolutePath)),
            if (sharedStorage) "1" else "0",
            shellQuote(suPath(resolvConf?.absolutePath.orEmpty())),
        ).joinToString(" ")

        internal fun suPath(path: String): String =
            if (path.startsWith(USER_PATH_PREFIX)) DATA_PATH_PREFIX + path.removePrefix(USER_PATH_PREFIX) else path

        internal fun shellQuote(value: String): String =
            "'" + value.replace("'", "'\\''") + "'"

        internal fun mergeOutput(stdout: String, stderr: String): String = when {
            stdout.isBlank() -> stderr
            stderr.isBlank() -> stdout
            else -> stdout.trimEnd() + "\n" + stderr
        }
    }
}
