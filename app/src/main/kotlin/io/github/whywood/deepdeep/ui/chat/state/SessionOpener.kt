package io.github.whywood.deepdeep.ui.chat.state

import io.github.whywood.deepdeep.conversation.ConversationState
import io.github.whywood.deepdeep.conversation.ConversationStore
import io.github.whywood.deepdeep.runtime.AgentRuntime
import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.runtime.OpenedSession
import io.github.whywood.deepdeep.runtime.RuntimeWorkspace
import io.github.whywood.deepdeep.runtime.SessionModelSnapshot
import io.github.whywood.deepdeep.ui.common.chat.ChatUiState
import io.github.whywood.deepdeep.ui.common.chat.state.SessionDraft
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext




internal class SessionOpener(
    private val runtime: AgentRuntime,
    private val ioDispatcher: CoroutineDispatcher,
    private val scope: CoroutineScope,
    private val state: () -> ChatUiState,
    private val update: ((ChatUiState) -> ChatUiState) -> Unit,
    private val store: ConversationStore,
    private val draftFor: (String) -> SessionDraft?,
    private val moveDraft: (String, String) -> Unit,
    private val applySessionModel: (SessionModelSnapshot?) -> Unit,
    private val localizeTitle: (String) -> String,
    private val refreshSessions: suspend () -> Unit,
    private val userFacingError: (String) -> String,
    private val openFailedText: () -> String,
    private val newChatFailedText: () -> String,
    private val startupFailedText: () -> String,
    private val reloadBusyText: () -> String,
    private val sessionNotReadyText: () -> String,
    private val reloadFailedText: () -> String,
) {
    private val jobs = mutableMapOf<String, Job>()
    private val openMutex = Mutex()
    private var latestEpoch = 0

    fun isOpening(sessionId: String): Boolean = jobs[sessionId]?.isActive == true

    suspend fun awaitOpening(sessionId: String) {
        jobs[sessionId]?.join()
    }

    fun openConversation(id: String) {
        val current = state()
        if (current.conversationId == id && current.historyLoaded) return
        val summary = (current.conversations + current.archivedConversations).firstOrNull { it.id == id } ?: return
        switchToSummary(summary)
    }

    fun openConversationById(id: String) {
        val known = (state().conversations + state().archivedConversations).firstOrNull { it.id == id }
        if (known != null) {
            openConversation(id)
            return
        }
        scope.launch {
            val summary = withContext(ioDispatcher) {
                runtime.allSessions().firstOrNull { it.id == id }
                    ?: runtime.archivedSessions().firstOrNull { it.id == id }
            } ?: return@launch
            switchToSummary(summary)
        }
    }

    fun newSession(workspaceId: String, initialInput: String = "") {
        scope.launch {
            createSession(workspaceId, initialInput)
        }
    }

    fun selectWorkspace(workspaceId: String) {
        scope.launch {
            val workspaces = withContext(ioDispatcher) { runtime.workspaces() }
            val workspace = workspaces.firstOrNull { it.id == workspaceId } ?: return@launch
            update { it.copy(currentWorkspaceId = workspace.id, error = null) }
            val sessions = withContext(ioDispatcher) { runtime.sessions(workspace.id) }
            val recent = sessions.firstOrNull()
            if (recent != null) switchToSummary(recent) else createSession(workspace.id)
        }
    }

    fun switchToSummary(summary: ConversationSummary) {
        latestEpoch += 1
        val cached = store.peek(summary.id)
        val draft = if (cached == null) null else draftFor(summary.id)
        val current = state()
        val workspaceId = current.workspaces.firstOrNull { it.cwd == summary.cwd }?.id ?: current.currentWorkspaceId
        val title = cached?.title?.takeIf { it.isNotBlank() } ?: localizeTitle(summary.title)
        update {
            it.copy(
                conversation = cached?.copy(
                    title = if (cached.title.isBlank()) title else cached.title,
                    queuedInputs = emptyList(),
                ) ?: ConversationState(title = title),
                conversationId = summary.id,
                conversationCwd = summary.cwd,
                currentWorkspaceId = workspaceId,
                compacting = false,
                switchingConversation = cached?.loaded != true,
                input = draft?.input.orEmpty(),
                quote = draft?.quote,
                attachments = draft?.attachments.orEmpty(),
                error = null,
                importingAttachment = false,
            )
        }
        applySessionModel(cached?.sessionModel)
        openSession(summary)
    }

    fun openSession(summary: ConversationSummary) {
        jobs.remove(summary.id)?.cancel()
        val epoch = latestEpoch
        val job = scope.launch {
            try {
                openMutex.withLock { loadAndApply(summary, epoch) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (state().conversationId == summary.id) {
                    update {
                        it.copy(switchingConversation = false, error = userFacingError(error.message ?: openFailedText()))
                    }
                }
            } finally {
                jobs.remove(summary.id)
            }
        }
        jobs[summary.id] = job
    }

    private suspend fun loadAndApply(summary: ConversationSummary, epoch: Int) {
        withContext(ioDispatcher) { runtime.openSession(summary.filePath, summary.cwd) }
        if (epoch != latestEpoch) return
        val resolved = store.open(summary.id, localizeTitle(summary.title))
        if (epoch != latestEpoch) return
        if (state().conversationId == summary.id) {
            update {
                it.copy(
                    conversation = resolved,
                    switchingConversation = false,
                    importingAttachment = false,
                )
            }
            applySessionModel(resolved.sessionModel)
        }
        refreshSessions()
    }

    fun createSession(workspaceId: String, initialInput: String = "") {
        scope.launch {
            try {
                val opened: OpenedSession = withContext(ioDispatcher) { runtime.newSession(workspaceId) }
                val projectCwd = opened.cwd
                val resolved = store.open(opened.sessionId)
                update {
                    it.copy(
                        conversation = resolved,
                        conversationId = opened.sessionId,
                        conversationCwd = projectCwd,
                        currentWorkspaceId = it.workspaces.firstOrNull { workspace -> workspace.cwd == projectCwd }?.id
                            ?: workspaceId,
                        compacting = false,
                        switchingConversation = false,
                        input = initialInput,
                        quote = null,
                        attachments = emptyList(),
                        error = null,
                        importingAttachment = false,
                    )
                }
                applySessionModel(resolved.sessionModel)
                refreshSessions()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                update { it.copy(error = userFacingError(error.message ?: newChatFailedText())) }
            }
        }
    }

    suspend fun restoreInitialSession() {
        val workspaces: List<RuntimeWorkspace> = withContext(ioDispatcher) { runtime.workspaces() }
        val sessions = withContext(ioDispatcher) {
            workspaces.flatMap { workspace -> runtime.sessions(workspace.id) }
        }
        val recent = sessions.maxByOrNull { it.updatedAt }
        val home = workspaces.firstOrNull { it.isHome } ?: workspaces.firstOrNull()
        if (home == null) {
            update { it.copy(ready = true, conversation = it.conversation.copy(loaded = true), error = startupFailedText()) }
            return
        }
        val workspace = recent?.let { summary -> workspaces.firstOrNull { it.cwd == summary.cwd } } ?: home
        update {
            it.copy(
                ready = true,
                workspaces = workspaces,
                currentWorkspaceId = workspace.id,
                conversationCwd = recent?.cwd ?: home.cwd,
                conversations = sessions.map { session -> session.copy(title = localizeTitle(session.title)) },
                conversation = it.conversation.copy(loaded = recent != null),
            )
        }
        if (recent != null) switchToSummary(recent) else createSession(workspace.id)
    }

    suspend fun reloadSession(): Result<Unit> {
        val current = state()
        if (current.sending) return Result.failure(IllegalStateException(reloadBusyText()))
        val sessionId = current.conversationId ?: return Result.failure(IllegalStateException(sessionNotReadyText()))
        update { it.copy(reloading = true) }
        return try {
            val reopenedId = withContext(ioDispatcher) { runtime.reloadSession(sessionId) }
            if (reopenedId != sessionId) {
                moveDraft(sessionId, reopenedId)
                store.move(sessionId, reopenedId)
                val resolved = store.peek(reopenedId)
                update {
                    it.copy(
                        conversation = resolved ?: it.conversation,
                        conversationId = reopenedId,
                        error = null,
                        reloading = false,
                    )
                }
                applySessionModel(resolved?.sessionModel)
            } else {
                val resolved = store.reopen(sessionId)
                update {
                    it.copy(
                        conversation = resolved,
                        error = null,
                        reloading = false,
                    )
                }
                applySessionModel(resolved.sessionModel)
            }
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            update { it.copy(reloading = false) }
            throw cancelled
        } catch (error: Exception) {
            update { it.copy(reloading = false) }
            Result.failure(IllegalStateException(reloadFailedText(), error))
        }
    }
}
