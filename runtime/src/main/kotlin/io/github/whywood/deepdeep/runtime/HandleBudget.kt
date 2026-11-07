package io.github.whywood.deepdeep.runtime

object HandleBudget {
    const val MAX_HANDLES = 6
    const val IDLE_TTL_MS = 3 * 60 * 1000L

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
        size: Int,
        now: Long,
        max: Int = MAX_HANDLES,
        ttlMs: Long = IDLE_TTL_MS,
    ): List<String> {
        if (size <= max) return emptyList()
        val candidates = entries
            .filter { !it.busy && !it.reconfiguring && !it.keepAlive && !it.selected }
            .filter { now - it.lastActiveAt > ttlMs }
            .sortedWith(compareBy({ it.lastActiveAt }, { it.sessionId }))
        val evictions = mutableListOf<String>()
        var remaining = size
        for (candidate in candidates) {
            if (remaining <= max) break
            evictions += candidate.sessionId
            remaining -= 1
        }
        return evictions
    }

    fun reconnectTarget(sessions: List<ConversationSummary>, sessionId: String): ConversationSummary? =
        sessions.firstOrNull { it.id == sessionId }
}
