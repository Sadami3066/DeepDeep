package io.github.whywood.deepdeep.runtime

import dev.pi.ai.RunSignal
import dev.pi.coding.tools.ShellExecutor
import dev.pi.coding.tools.ShellResult

data class CommandResult(
    val output: String,
    val exitCode: Int?,
    val timedOut: Boolean = false,
    val truncated: Boolean = false,
    val fullOutputPath: String? = null,
)

interface DetailedShellExecutor : ShellExecutor {
    suspend fun runDetailed(command: String, timeoutSeconds: Long?, signal: RunSignal?): CommandResult

    override suspend fun run(command: String, timeoutSeconds: Long?, signal: RunSignal?): ShellResult {
        val result = runDetailed(command, timeoutSeconds, signal)
        return ShellResult(result.output, result.exitCode, result.truncated)
    }
}
