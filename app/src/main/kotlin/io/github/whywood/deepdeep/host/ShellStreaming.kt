package io.github.whywood.deepdeep.host

import io.github.whywood.deepdeep.runtime.host.StreamingShellExecutor
import io.github.whywood.deepdeep.runtime.host.StreamingShellResult
import io.github.whywood.deepdeep.shell.LinuxResult

internal class LinuxStreamingShellExecutor(
    private val executeStreaming: suspend (
        command: String,
        timeoutMillis: Long,
        onOutputLine: (String) -> Unit,
    ) -> LinuxResult,
) : StreamingShellExecutor {
    override suspend fun execute(
        command: String,
        timeoutMillis: Long,
        onOutputLine: (String) -> Unit,
    ): StreamingShellResult {
        val result = executeStreaming(command, timeoutMillis, onOutputLine)
        return StreamingShellResult(
            exitCode = result.exitCode,
            output = result.output,
            truncated = result.truncated,
            timedOut = result.timedOut,
            durationMillis = result.durationMillis,
            fullOutputPath = result.fullOutputPath,
        )
    }
}
