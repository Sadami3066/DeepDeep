package io.github.whywood.deepdeep.plugin.host.services

import io.github.whywood.deepdeep.plugin.api.LinuxChunk
import io.github.whywood.deepdeep.plugin.api.LinuxResult
import io.github.whywood.deepdeep.plugin.api.LinuxService
import io.github.whywood.deepdeep.shell.LinuxEnvironment
import io.github.whywood.deepdeep.shell.LinuxInstallState
import io.github.whywood.deepdeep.shell.LinuxResult as ShellLinuxResult
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

internal class LinuxPluginService(
    private val linux: LinuxEnvironment,
    private val onOutputDropped: (Long) -> Unit = {},
) : LinuxService {
    override val available: Boolean
        get() = linux.isSupported && linux.state.value is LinuxInstallState.Installed

    override suspend fun exec(command: String, cwd: String?, timeoutMs: Long): LinuxResult =
        linux.execute(withCwd(command, cwd), timeoutMs).toPluginResult()

    override fun stream(command: String, cwd: String?): Flow<LinuxChunk> = linuxStream(
        execute = { onLine -> linux.executeStreaming(withCwd(command, cwd), STREAM_TIMEOUT_MS, onLine) },
        onOutputDropped = onOutputDropped,
    )

    private fun withCwd(command: String, cwd: String?): String =
        if (cwd.isNullOrBlank()) command else "cd ${shellQuote(cwd)} && $command"

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private fun ShellLinuxResult.toPluginResult(): LinuxResult = LinuxResult(
        exitCode = exitCode,
        stdout = output,
        stderr = "",
        timedOut = timedOut,
        truncated = truncated,
        fullOutputPath = fullOutputPath,
    )

    private companion object {
        const val STREAM_TIMEOUT_MS = 10 * 60 * 1000L
    }
}

internal fun linuxStream(
    execute: suspend (onLine: (String) -> Unit) -> ShellLinuxResult,
    onOutputDropped: (Long) -> Unit = {},
): Flow<LinuxChunk> = callbackFlow {
    val dropped = AtomicLong()
    val result = execute { line ->
        if (trySend(LinuxChunk.Stdout(line)).isFailure) dropped.incrementAndGet()
    }
    if (dropped.get() > 0) onOutputDropped(dropped.get())
    send(LinuxChunk.Exit(result.exitCode))
    close()
}
