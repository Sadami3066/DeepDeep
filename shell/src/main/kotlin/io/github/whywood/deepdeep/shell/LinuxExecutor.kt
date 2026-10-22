package io.github.whywood.deepdeep.shell

data class LinuxResult(
    val exitCode: Int,
    val output: String,
    val truncated: Boolean,
    val timedOut: Boolean,
    val durationMillis: Long,
)

interface LinuxExecutor {
    suspend fun execute(command: String, timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS): LinuxResult

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 60_000L
    }
}
