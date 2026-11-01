package io.github.whywood.deepdeep.ui.chat.state

import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.ConversationQueries
import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.runtime.RunStatus
import io.github.whywood.deepdeep.runtime.RuntimeActivity
import io.github.whywood.deepdeep.runtime.SessionModelSnapshot
import io.github.whywood.deepdeep.runtime.WorkspaceAdmin
import io.github.whywood.deepdeep.ui.chat.ChatUiState
import io.github.whywood.deepdeep.ui.chat.ChatUiStrings
import io.github.whywood.deepdeep.ui.chat.SessionSearchHit
import io.github.whywood.deepdeep.ui.chat.adoptActiveRun
import io.github.whywood.deepdeep.ui.chat.localizedRunError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class SessionListController(
    private val runtime: ConversationQueries,
    private val admin: WorkspaceAdmin,
    private val ioDispatcher: CoroutineDispatcher,
    private val state: () -> ChatUiState,
    private val update: ((ChatUiState) -> ChatUiState) -> Unit,
    private val strings: () -> ChatUiStrings,
    private val cacheCurrentSession: () -> Unit,
    private val retainCache: (Set<String>) -> Unit,
    private val target: ConversationWriteTarget,
    private val applySessionModel: (SessionModelSnapshot?) -> Unit,
    private val isRetrying: (String) -> Boolean,
    private val localizeError: (String?) -> String,
) {
    suspend fun refresh() {
        val current = state()
        val workspaces = withContext(ioDispatcher) { runtime.workspaces() }
        val workspace = workspaces.firstOrNull { it.id == current.currentWorkspaceId }
            ?: workspaces.firstOrNull { it.isHome }
            ?: workspaces.firstOrNull()
        val sessions = withContext(ioDispatcher) { runtime.allSessions() }
        val archived = withContext(ioDispatcher) { runtime.archivedSessions() }
        retainCache((sessions + archived).map { it.id }.toSet())
        update {
            val title = it.conversationId?.let { id -> sessions.firstOrNull { item -> item.id == id }?.title }
            it.copy(
                ready = true,
                workspaces = workspaces,
                currentWorkspaceId = workspace?.id ?: it.currentWorkspaceId,
                conversations = sessions.map { session -> session.copy(title = localizedTitle(session.title)) },
                archivedConversations = archived.map { session -> session.copy(title = localizedTitle(session.title)) },
                archivedCount = archived.size,
                conversation = it.conversation.copy(title = title?.let(::localizedTitle) ?: it.conversationTitle),
            )
        }
        cacheCurrentSession()
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

    suspend fun reconcile(sessionId: String) {
        runtime.activeRuns.value[sessionId]?.let { active ->
            val current = state()
            if (current.conversationId != sessionId || !current.sending) return
            val conversation = current.conversation
            if (conversation.messages.any { it.runId == active.runId }) return
            val adopted = conversation.adoptActiveRun(active.runId, RuntimeActivity.Thinking, active.startedAt)
            update { it.copy(conversation = adopted) }
            cacheCurrentSession()
            return
        }
        val current = state()
        val isCurrent = current.conversationId == sessionId
        if (isCurrent && !current.sending) return
        if (isRetrying(sessionId)) return
        val history = withContext(ioDispatcher) { runtime.history(sessionId) }
        val sessionModel = withContext(ioDispatcher) { runtime.sessionModel(sessionId) }
        val settled = history
            .map { message -> message.localizedRunError(localizeError) }
            .map { message ->
                if (!message.streaming && !message.retrying) {
                    message
                } else {
                    message.copy(
                        streaming = false,
                        retrying = false,
                        status = if (message.status == MessageStatus.RUNNING) MessageStatus.DONE else message.status,
                    )
                }
            }
        if (isCurrent) {
            update {
                it.copy(
                    conversation = it.conversation.copy(
                        messages = settled,
                        sending = false,
                        activity = null,
                        activityStartedAt = null,
                        loaded = true,
                        title = it.conversationTitle.ifBlank { titleFrom(settled) },
                    ),
                )
            }
            applySessionModel(sessionModel)
            cacheCurrentSession()
        } else {
            target.updateIfPresent(sessionId) { cached ->
                cached.copy(
                    messages = settled,
                    sending = false,
                    activity = null,
                    activityStartedAt = null,
                    loaded = true,
                    sessionModel = sessionModel ?: cached.sessionModel,
                )
            }
        }
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
            title == ChatUiStrings.fallback().newChatTitle

    fun titleFrom(text: String): String {
        val normalized = text.trim().replace(Regex("\\s+"), " ")
        return if (normalized.length <= 28) normalized else normalized.take(28) + "…"
    }

    private companion object {
        const val LEGACY_NEW_TITLE = "新对话"
    }
}

internal fun chatError(strings: ChatUiStrings, message: String): String = when {
    message.contains("API key", ignoreCase = true) || message.contains("401") -> strings.invalidApiKey
    message.contains("429") -> strings.rateLimited
    message.contains("timed out", ignoreCase = true) -> strings.timeout
    message.contains("Network", ignoreCase = true) -> strings.networkError
    message.contains("tool input", ignoreCase = true) || message.contains("invalid JSON", ignoreCase = true) ->
        strings.invalidToolArgs

    message.contains("500") || message.contains("503") -> strings.serviceUnavailable
    else -> message
}
