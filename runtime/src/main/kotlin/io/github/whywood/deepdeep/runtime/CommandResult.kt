package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.sdk.ai.RunSignal
import io.github.whywood.deepdeep.sdk.coding.tools.ShellExecutor
import io.github.whywood.deepdeep.sdk.coding.tools.ShellResult

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
