package io.github.whywood.deepdeep.shell

import io.github.whywood.deepdeep.sdk.ai.RequestAbortedException
import io.github.whywood.deepdeep.sdk.ai.RunSignal
import io.github.whywood.deepdeep.sdk.coding.tools.ShellExecutionException
import io.github.whywood.deepdeep.sdk.coding.tools.ShellExecutor
import io.github.whywood.deepdeep.sdk.coding.tools.ShellResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class LinuxShellExecutor(
    private val environment: LinuxEnvironment,
    private val defaultTimeoutSeconds: Long = 60,
) : ShellExecutor {
    override suspend fun run(command: String, timeoutSeconds: Long?, signal: RunSignal?): ShellResult {
        val result = runDetailed(command, timeoutSeconds, signal)
        if (result.timedOut) throw ShellExecutionException("command timed out after ${timeoutSeconds ?: defaultTimeoutSeconds}s")
        return ShellResult(result.output, result.exitCode, result.truncated)
    }

    suspend fun runDetailed(command: String, timeoutSeconds: Long?, signal: RunSignal?): LinuxResult {
        signal?.throwIfAborted()
        val timeout = (timeoutSeconds ?: defaultTimeoutSeconds).coerceIn(1, 600)
        return coroutineScope {
            val execution = async { environment.execute(command, timeout * 1000) }
            val unsubscribe = signal?.onAbort { execution.cancel() }
            val result = try {
                execution.await()
            } catch (cancelled: CancellationException) {
                if (signal?.isAborted == true) throw RequestAbortedException(signal.reason)
                throw cancelled
            } finally {
                unsubscribe?.invoke()
            }
            if (signal?.isAborted == true) throw RequestAbortedException(signal.reason)
            result
        }
    }
}
