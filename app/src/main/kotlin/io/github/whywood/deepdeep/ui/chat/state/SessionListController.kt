package io.github.whywood.deepdeep.ui.chat.state

import io.github.whywood.deepdeep.conversation.ConversationStore
import io.github.whywood.deepdeep.conversation.ConversationStrings
import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.ConversationQueries
import io.github.whywood.deepdeep.runtime.WorkspaceAdmin
import io.github.whywood.deepdeep.ui.chat.ChatUiState
import io.github.whywood.deepdeep.ui.chat.SessionSearchHit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class SessionListController(
    private val runtime: ConversationQueries,
    private val admin: WorkspaceAdmin,
    private val ioDispatcher: CoroutineDispatcher,
    private val state: () -> ChatUiState,
    private val update: ((ChatUiState) -> ChatUiState) -> Unit,
    private val store: ConversationStore,
    private val strings: () -> ConversationStrings,
) {
    suspend fun refresh() {
        val current = state()
        val workspaces = withContext(ioDispatcher) { runtime.workspaces() }
        val workspace = workspaces.firstOrNull { it.id == current.currentWorkspaceId }
            ?: workspaces.firstOrNull { it.isHome }
            ?: workspaces.firstOrNull()
        val sessions = withContext(ioDispatcher) { runtime.allSessions() }
        val archived = withContext(ioDispatcher) { runtime.archivedSessions() }
        update {
            it.copy(
                ready = true,
                workspaces = workspaces,
                currentWorkspaceId = workspace?.id ?: it.currentWorkspaceId,
                conversations = sessions.map { session -> session.copy(title = localizedTitle(session.title)) },
                archivedConversations = archived.map { session -> session.copy(title = localizedTitle(session.title)) },
                archivedCount = archived.size,
            )
        }
        val sessionId = current.conversationId ?: return
        val title = sessions.firstOrNull { it.id == sessionId }?.title?.let(::localizedTitle) ?: return
        store.updateIfPresent(sessionId) { it.copy(title = title) }
    }

    suspend fun sessionCount(workspaceId: String): Int =
        withContext(ioDispatcher) { admin.workspaceSessionCount(workspaceId) }

    suspend fun search(): List<SessionSearchHit> = withContext(ioDispatcher) {
        val workspaces = state().workspaces.ifEmpty { runtime.workspaces() }
        val namesByCwd = workspaces.associate { it.cwd to it.name }
        runtime.allSessions()
            .map { summary ->
                SessionSearchHit(
                    conversation = summary.copy(title = localizedTitle(summary.title)),
                    workspaceName = namesByCwd[summary.cwd] ?: "Home",
                )
            }
            .sortedByDescending { it.conversation.updatedAt }
    }

    fun titleFrom(messages: List<AssistantMessage>): String {
        val firstUser = messages.firstOrNull { it.role == AssistantRole.USER }?.text ?: return strings().newChatTitle
        return titleFrom(firstUser)
    }

    fun localizedTitle(title: String): String =
        if (isDefaultChatTitle(title)) strings().newChatTitle else title

    fun isDefaultChatTitle(title: String): Boolean =
        title.isBlank() ||
            title == LEGACY_NEW_TITLE ||
            title == strings().newChatTitle ||
            title == ConversationStrings.fallback().newChatTitle

    fun titleFrom(text: String): String {
        val normalized = text.trim().replace(Regex("\\s+"), " ")
        return if (normalized.length <= 28) normalized else normalized.take(28) + "…"
    }

    private companion object {
        const val LEGACY_NEW_TITLE = "新对话"
    }
}

internal fun chatError(strings: ConversationStrings, message: String): String = when {
    message.contains("API key", ignoreCase = true) || message.contains("401") -> strings.invalidApiKey
    message.contains("429") -> strings.rateLimited
    message.contains("timed out", ignoreCase = true) -> strings.timeout
    message.contains("Network", ignoreCase = true) -> strings.networkError
    message.contains("tool input", ignoreCase = true) || message.contains("invalid JSON", ignoreCase = true) ->
        strings.invalidToolArgs

    message.contains("500") || message.contains("503") -> strings.serviceUnavailable
    else -> message
}
