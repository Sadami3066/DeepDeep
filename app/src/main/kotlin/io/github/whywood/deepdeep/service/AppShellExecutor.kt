package io.github.whywood.deepdeep.service

import io.github.whywood.deepdeep.sdk.ai.RunSignal
import io.github.whywood.deepdeep.runtime.CommandResult
import io.github.whywood.deepdeep.runtime.DetailedShellExecutor
import io.github.whywood.deepdeep.shell.LinuxExecutor
import io.github.whywood.deepdeep.shell.LinuxShellExecutor

class AppShellExecutor(executor: LinuxExecutor) : DetailedShellExecutor {
    private val delegate = LinuxShellExecutor(executor)

    override suspend fun runDetailed(command: String, timeoutSeconds: Long?, signal: RunSignal?): CommandResult {
        val result = delegate.runDetailed(command, timeoutSeconds, signal)
        return CommandResult(result.output, result.exitCode, result.timedOut, result.truncated, result.fullOutputPath)
    }
}
