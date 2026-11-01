package io.github.whywood.deepdeep.service

import io.github.whywood.deepdeep.runtime.BackgroundCommandRunner
import io.github.whywood.deepdeep.shell.LinuxEnvironment
import io.github.whywood.deepdeep.shell.LinuxInstallState

class LinuxJobRunner(private val environment: LinuxEnvironment) : BackgroundCommandRunner {
    override suspend fun run(command: String, onOutput: (String) -> Unit): Int {
        if (environment.state.value !is LinuxInstallState.Installed) {
            throw IllegalStateException("Linux environment is not installed")
        }
        val result = environment.executeStreaming(command, TIMEOUT_MILLIS) { line ->
            onOutput(line + "\n")
        }
        result.fullOutputPath?.let { onOutput("\n[Full output: $it]\n") }
        if (result.timedOut) onOutput("\n[Command timed out and was terminated]\n")
        return result.exitCode
    }

    private companion object {
        const val TIMEOUT_MILLIS = 24L * 60 * 60 * 1000
    }
}
