package io.github.whywood.deepdeep.plugin.host.services

import io.github.whywood.deepdeep.plugin.api.LinuxChunk
import io.github.whywood.deepdeep.plugin.api.LinuxResult
import io.github.whywood.deepdeep.plugin.api.LinuxService
import io.github.whywood.deepdeep.runtime.CommandResult
import io.github.whywood.deepdeep.runtime.DetailedShellExecutor
import io.github.whywood.deepdeep.runtime.host.HostEnvironmentProvider
import io.github.whywood.deepdeep.shell.LinuxResult as ShellLinuxResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

internal class LinuxPluginService(
    private val hostEnvironment: HostEnvironmentProvider,
    private val defaultWorkspace: () -> String,
    private val availableProvider: () -> Boolean,
) : LinuxService {
    override val available: Boolean
        get() = availableProvider()

    override suspend fun exec(command: String, cwd: String?, timeoutMs: Long): LinuxResult =
        executeDetailed(command, cwd, timeoutMs).toPluginResult()

    override fun stream(command: String, cwd: String?): Flow<LinuxChunk> = linuxStream(
        execute = { onLine ->
            val environment = hostEnvironment.current()
            val workspace = cwd?.takeIf { it.isNotBlank() } ?: defaultWorkspace()
            val streaming = environment.bashStreaming(workspace)
            if (streaming != null) {
                val streamed = streaming.execute(command, STREAM_TIMEOUT_MS, onLine)
                ShellLinuxResult(
                    exitCode = streamed.exitCode,
                    output = streamed.output,
                    truncated = streamed.truncated,
                    timedOut = streamed.timedOut,
                    durationMillis = streamed.durationMillis,
                    fullOutputPath = streamed.fullOutputPath,
                )
            } else {
                val result = executeDetailed(command, cwd, STREAM_TIMEOUT_MS)
                result.output.lineSequence().forEach(onLine)
                ShellLinuxResult(
                    exitCode = result.exitCode ?: -1,
                    output = result.output,
                    truncated = result.truncated,
                    timedOut = result.timedOut,
                    durationMillis = 0L,
                    fullOutputPath = result.fullOutputPath,
                )
            }
        },
    )

    private suspend fun executeDetailed(command: String, cwd: String?, timeoutMs: Long): CommandResult {
        val executor = hostEnvironment.current().bash(cwd?.takeIf { it.isNotBlank() } ?: defaultWorkspace())
        val timeoutSeconds = timeoutSeconds(timeoutMs)
        return if (executor is DetailedShellExecutor) {
            executor.runDetailed(command, timeoutSeconds, null)
        } else {
            val result = executor.run(command, timeoutSeconds, null)
            CommandResult(output = result.output, exitCode = result.exitCode, truncated = result.truncated)
        }
    }

    private fun CommandResult.toPluginResult(): LinuxResult = LinuxResult(
        exitCode = exitCode ?: -1,
        stdout = output,
        stderr = "",
        timedOut = timedOut,
        truncated = truncated,
        fullOutputPath = fullOutputPath,
    )

    private fun timeoutSeconds(timeoutMs: Long): Long? =
        if (timeoutMs <= 0L) null else (timeoutMs + 999L) / 1000L

    private companion object {
        const val STREAM_TIMEOUT_MS = 24 * 60 * 60 * 1000L
    }
}

internal fun linuxStream(
    execute: suspend (onLine: (String) -> Unit) -> ShellLinuxResult,
): Flow<LinuxChunk> = callbackFlow {
    val pending = Channel<LinuxChunk>(Channel.UNLIMITED)
    val pump = launch { for (chunk in pending) send(chunk) }
    val result = execute { line -> pending.trySend(LinuxChunk.Stdout(line)) }
    pending.send(LinuxChunk.Exit(result.exitCode))
    pending.close()
    pump.join()
    close()
}
