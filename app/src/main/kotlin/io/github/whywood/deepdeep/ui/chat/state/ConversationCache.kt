package io.github.whywood.deepdeep.ui.chat.state

import android.content.ComponentCallbacks2
import io.github.whywood.deepdeep.ui.chat.ChatUiState
import io.github.whywood.deepdeep.conversation.ConversationState

internal class ConversationCache(private val target: ConversationWriteTarget) {
    fun store(sessionId: String, state: ConversationState) = target.store(sessionId, state)

    fun readCached(sessionId: String): ConversationState? = target.readCached(sessionId)
    fun evict(sessionId: String) = target.evict(sessionId)

    fun take(sessionId: String): ConversationState? = target.take(sessionId)

    fun retainOnly(sessionIds: Set<String>) = target.retainOnly(sessionIds)

    fun isActive(sessionId: String): Boolean = target.isActive(sessionId)
    fun rememberCurrent(state: ChatUiState, flush: () -> Unit): String? {
        val sessionId = state.conversationId ?: return null
        if (!state.historyLoaded) return null
        flush()
        val loaded = target.readCached(sessionId)?.loaded == true
        target.store(sessionId, state.conversation.copy(loaded = loaded))
        return sessionId
    }

    fun onTrimMemory(level: Int) {
        if (level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
        ) {
            target.clearUnprotected()
        }
    }
}
