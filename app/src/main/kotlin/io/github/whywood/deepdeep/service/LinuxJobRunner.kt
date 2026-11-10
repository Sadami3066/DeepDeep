package io.github.whywood.deepdeep.service

import io.github.whywood.deepdeep.runtime.BackgroundCommandRunner
import io.github.whywood.deepdeep.runtime.CommandResult
import io.github.whywood.deepdeep.runtime.DetailedShellExecutor
import io.github.whywood.deepdeep.runtime.host.HostEnvironmentProvider

class LinuxJobRunner(
    private val hostEnvironment: HostEnvironmentProvider,
    private val cwd: String?,
) : BackgroundCommandRunner {
    override suspend fun run(command: String, onOutput: (String) -> Unit): Int {
        val environment = hostEnvironment.current()
        val streaming = environment.bashStreaming(cwd.orEmpty())
        if (streaming != null) {
            val result = streaming.execute(command, TIMEOUT_MILLIS) { line -> onOutput(line + "\n") }
            result.fullOutputPath?.let { path -> onOutput("\n[Full output: $path]\n") }
            if (result.timedOut) onOutput("\n[Command timed out and was terminated]\n")
            return result.exitCode ?: -1
        }
        val executor = environment.bash(cwd.orEmpty())
        val result = if (executor is DetailedShellExecutor) {
            executor.runDetailed(command, TIMEOUT_SECONDS, null)
        } else {
            executor.run(command, TIMEOUT_SECONDS, null).let { shell ->
                CommandResult(output = shell.output, exitCode = shell.exitCode, truncated = shell.truncated)
            }
        }
        result.output.lineSequence().forEach { line -> onOutput(line + "\n") }
        result.fullOutputPath?.let { path -> onOutput("\n[Full output: $path]\n") }
        if (result.timedOut) onOutput("\n[Command timed out and was terminated]\n")
        return result.exitCode ?: -1
    }

    private companion object {
        private const val TIMEOUT_SECONDS = 24L * 60 * 60
        private const val TIMEOUT_MILLIS = TIMEOUT_SECONDS * 1000
    }
}
