package io.github.whywood.deepdeep.ui.chat.state

import io.github.whywood.deepdeep.ui.chat.ConversationState
import java.util.LinkedHashMap

internal class ConversationWriteTarget(
    private val maxCachedSessions: Int = MAX_CACHED_SESSIONS,
    private val activeReader: () -> ConversationState,
    private val activeWriter: (ConversationState) -> Unit,
    private val protectedSessionIds: () -> Set<String> = { emptySet() },
    private val onEvict: (String) -> Unit = {},
) {
    private val lock = Any()
    private val cache = LinkedHashMap<String, ConversationState>(16, 0.75f, true)
    private val pinned = mutableSetOf<String>()
    private var activeSessionId: String? = null

    fun setActive(sessionId: String?) {
        activeSessionId = sessionId
    }

    fun isActive(sessionId: String): Boolean = sessionId == activeSessionId

    fun read(sessionId: String): ConversationState =
        if (isActive(sessionId)) activeReader() else cache[sessionId] ?: ConversationState()

    fun readCached(sessionId: String): ConversationState? = synchronized(lock) { cache[sessionId] }

    fun store(sessionId: String, state: ConversationState) {
        synchronized(lock) {
            cache[sessionId] = state
            trim()
        }
    }

    fun update(sessionId: String, transform: (ConversationState) -> ConversationState) {
        synchronized(lock) {
            if (isActive(sessionId)) {
                activeWriter(transform(activeReader()))
            } else {
                cache[sessionId] = transform(cache[sessionId] ?: ConversationState())
                trim()
            }
        }
    }

    fun updateIfPresent(sessionId: String, transform: (ConversationState) -> ConversationState): Boolean {
        synchronized(lock) {
            if (isActive(sessionId)) {
                activeWriter(transform(activeReader()))
                return true
            }
            val cached = cache[sessionId] ?: return false
            cache[sessionId] = transform(cached)
            trim()
            return true
        }
    }

    fun pin(sessionId: String) {
        synchronized(lock) { pinned += sessionId }
    }

    fun unpin(sessionId: String) {
        synchronized(lock) { pinned -= sessionId }
    }

    fun take(sessionId: String): ConversationState? = synchronized(lock) {
        val cached = cache.remove(sessionId)
        if (cached != null) {
            pinned -= sessionId
            onEvict(sessionId)
        }
        cached
    }

    fun evict(sessionId: String) {
        take(sessionId)
    }

    fun clearUnprotected() {
        synchronized(lock) {
            val protectedNow = protectedSessionIds() + pinned + listOfNotNull(activeSessionId)
            cache.keys.filterNot { it in protectedNow }.forEach { sessionId ->
                cache.remove(sessionId)
                pinned -= sessionId
                onEvict(sessionId)
            }
        }
    }

    fun retainOnly(sessionIds: Set<String>) {
        synchronized(lock) {
            cache.keys.filterNot { it in sessionIds }.forEach { sessionId ->
                cache.remove(sessionId)
                pinned -= sessionId
                onEvict(sessionId)
            }
        }
    }

    fun sessionIds(): Set<String> = synchronized(lock) { cache.keys.toSet() }

    private fun trim() {
        while (cache.size > maxCachedSessions) {
            val protectedNow = protectedSessionIds() + pinned + listOfNotNull(activeSessionId)
            val victim = cache.keys.firstOrNull { it !in protectedNow } ?: return
            cache.remove(victim)
            pinned -= victim
            onEvict(victim)
        }
    }

    private companion object {
        const val MAX_CACHED_SESSIONS = 8
    }
}
