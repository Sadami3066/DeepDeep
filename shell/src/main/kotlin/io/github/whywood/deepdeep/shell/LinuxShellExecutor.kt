package io.github.whywood.deepdeep.shell

import dev.pi.ai.RequestAbortedException
import dev.pi.ai.RunSignal
import dev.pi.coding.tools.ShellExecutionException
import dev.pi.coding.tools.ShellExecutor
import dev.pi.coding.tools.ShellResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class LinuxShellExecutor(
    private val environment: LinuxEnvironment,
    private val defaultTimeoutSeconds: Long = 60,
) : ShellExecutor {
    override suspend fun run(command: String, timeoutSeconds: Long?, signal: RunSignal?): ShellResult {
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
            if (result.timedOut) {
                throw ShellExecutionException("command timed out after ${timeout}s")
            }
            ShellResult(
                output = result.output,
                exitCode = result.exitCode,
                truncated = result.truncated,
            )
        }
    }
}
