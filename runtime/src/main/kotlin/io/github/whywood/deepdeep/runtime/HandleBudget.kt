package io.github.whywood.deepdeep.runtime

object HandleBudget {
    const val IDLE_TTL_MS = 5 * 60 * 1000L
    const val SWEEP_INTERVAL_MS = 60 * 1000L

    data class Entry(
        val sessionId: String,
        val lastActiveAt: Long,
        val busy: Boolean = false,
        val reconfiguring: Boolean = false,
        val keepAlive: Boolean = false,
        val selected: Boolean = false,
    )

    fun decideEvictions(
        entries: List<Entry>,
        now: Long,
        ttlMs: Long = IDLE_TTL_MS,
    ): List<String> = entries
        .filter { !it.busy && !it.reconfiguring && !it.keepAlive && !it.selected }
        .filter { now - it.lastActiveAt > ttlMs }
        .sortedWith(compareBy({ it.lastActiveAt }, { it.sessionId }))
        .map { it.sessionId }

    fun reconnectTarget(sessions: List<ConversationSummary>, sessionId: String): ConversationSummary? =
        sessions.firstOrNull { it.id == sessionId }
}
