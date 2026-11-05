package io.github.whywood.deepdeep.plugin.host.services

import io.github.whywood.deepdeep.plugin.api.LinuxChunk
import io.github.whywood.deepdeep.plugin.api.LinuxResult
import io.github.whywood.deepdeep.plugin.api.LinuxService
import io.github.whywood.deepdeep.shell.LinuxEnvironment
import io.github.whywood.deepdeep.shell.LinuxInstallState
import io.github.whywood.deepdeep.shell.LinuxResult as ShellLinuxResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

internal class LinuxPluginService(
    private val linux: LinuxEnvironment,
) : LinuxService {
    override val available: Boolean
        get() = linux.isSupported && linux.state.value is LinuxInstallState.Installed

    override suspend fun exec(command: String, cwd: String?, timeoutMs: Long): LinuxResult =
        linux.execute(withCwd(command, cwd), timeoutMs).toPluginResult()

    override fun stream(command: String, cwd: String?): Flow<LinuxChunk> = callbackFlow {
        val result = linux.executeStreaming(withCwd(command, cwd), STREAM_TIMEOUT_MS) { line ->
            trySend(LinuxChunk.Stdout(line))
        }
        trySend(LinuxChunk.Exit(result.exitCode))
    }

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
