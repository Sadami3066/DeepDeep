package io.github.whywood.deepdeep.ui.chat.state

import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.ConversationQueries
import io.github.whywood.deepdeep.ui.chat.ChatUiState
import io.github.whywood.deepdeep.ui.chat.localizedRunError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class UsageController(
    private val runtime: ConversationQueries,
    private val ioDispatcher: CoroutineDispatcher,
    private val target: ConversationWriteTarget,
    private val state: () -> ChatUiState,
    private val update: ((ChatUiState) -> ChatUiState) -> Unit,
    private val cacheCurrentSession: () -> Unit,
    private val titleFrom: (List<AssistantMessage>) -> String,
    private val localizeError: (String?) -> String,
) {
    suspend fun refresh(sessionId: String) {
        val usage = withContext(ioDispatcher) { runtime.usage(sessionId) } ?: return
        target.updateIfPresent(sessionId) { it.copy(usage = usage) }
        if (target.isActive(sessionId)) cacheCurrentSession()
    }

    suspend fun reloadHistoryAfterCompaction(sessionId: String) {
        if (state().conversationId != sessionId) return
        val current = state()
        if (current.sending) return
        val history = withContext(ioDispatcher) { runtime.history(sessionId) }
        val localized = history.map { message -> message.localizedRunError(localizeError) }
        var nextId = (localized.maxOfOrNull { it.id } ?: -1L) + 1
        val notices = current.messages.takeLastWhile { it.notice != null }.map { it.copy(id = nextId++) }
        val next = localized + notices
        update {
            it.copy(
                conversation = it.conversation.copy(
                    messages = next,
                    loaded = true,
                    title = it.conversationTitle.ifBlank { titleFrom(next) },
                ),
            )
        }
        cacheCurrentSession()
    }
}
