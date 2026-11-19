package io.github.whywood.superpi.service

import io.github.whywood.deepdeep.sdk.ai.RunSignal
import io.github.whywood.superpi.runtime.CommandResult
import io.github.whywood.superpi.runtime.DetailedShellExecutor
import io.github.whywood.superpi.shell.LinuxExecutor
import io.github.whywood.superpi.shell.LinuxShellExecutor

class AppShellExecutor(executor: LinuxExecutor) : DetailedShellExecutor {
    private val delegate = LinuxShellExecutor(executor)

    override suspend fun runDetailed(command: String, timeoutSeconds: Long?, signal: RunSignal?): CommandResult {
        val result = delegate.runDetailed(command, timeoutSeconds, signal)
        return CommandResult(result.output, result.exitCode, result.timedOut, result.truncated, result.fullOutputPath)
    }
}
