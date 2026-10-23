package io.github.whywood.deepdeep.runtime

data class SessionUsage(
    val contextTokens: Long,
    val contextWindow: Long,
    val inputTokens: Long,
    val outputTokens: Long,
    val cacheReadTokens: Long,
    val cacheWriteTokens: Long,
    val cost: Double,
)

sealed interface CompactionOutcome {
    data class Done(val tokensBefore: Long, val tokensAfter: Long?) : CompactionOutcome

    data object Empty : CompactionOutcome

    data object Busy : CompactionOutcome

    data object Unavailable : CompactionOutcome

    data class Failed(val message: String?) : CompactionOutcome
}
