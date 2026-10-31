package io.github.whywood.deepdeep.ui.chat.state

import io.github.whywood.deepdeep.runtime.AgentRuntime
import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.runtime.OpenedSession
import io.github.whywood.deepdeep.runtime.RuntimeActivity
import io.github.whywood.deepdeep.runtime.RuntimeWorkspace
import io.github.whywood.deepdeep.runtime.SessionModelSnapshot
import io.github.whywood.deepdeep.ui.chat.ChatUiState
import io.github.whywood.deepdeep.ui.chat.ConversationState
import io.github.whywood.deepdeep.ui.chat.adoptActiveRun
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
    private val cachedState: (String) -> ConversationState?,
    private val rememberState: (String, ConversationState) -> Unit,
    private val isActive: (String) -> Boolean,
    private val setActive: (String) -> Unit,
    private val draftFor: (String) -> SessionDraft?,
    private val moveDraft: (String, String) -> Unit,
    private val takeCached: (String) -> ConversationState?,
    private val applySessionModel: (SessionModelSnapshot?) -> Unit,
    private val titleFrom: (List<AssistantMessage>) -> String,
    private val localizeError: (String?) -> String,
    private val localizeTitle: (String) -> String,
    private val decorateNotices: suspend (ConversationState, String) -> ConversationState,
    private val cacheCurrentSession: () -> Unit,
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
        cacheCurrentSession()
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
            cacheCurrentSession()
            switchToSummary(summary)
        }
    }
    fun newSession(workspaceId: String, initialInput: String = "") {
        scope.launch {
            cacheCurrentSession()
            createSession(workspaceId, initialInput)
        }
    }
    fun selectWorkspace(workspaceId: String) {
        scope.launch {
            cacheCurrentSession()
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
        val animated = cachedState(summary.id)
        val draft = if (animated == null) null else draftFor(summary.id)
        val current = state()
        val workspaceId = current.workspaces.firstOrNull { it.cwd == summary.cwd }?.id ?: current.currentWorkspaceId
        update {
            it.copy(
                conversation = ConversationState(
                    messages = animated?.messages ?: emptyList(),
                    title = animated?.title?.ifBlank { summary.title } ?: summary.title,
                    sending = animated?.sending ?: false,
                    activity = animated?.activity,
                    activityStartedAt = animated?.activityStartedAt,
                    loaded = animated?.loaded == true,
                    usage = animated?.usage,
                ),
                conversationId = summary.id,
                conversationCwd = summary.cwd,
                currentWorkspaceId = workspaceId,
                compacting = false,
                switchingConversation = animated == null,
                input = draft?.input.orEmpty(),
                quote = draft?.quote,
                attachments = draft?.attachments.orEmpty(),
                error = null,
                importingAttachment = false,
            )
        }
        setActive(summary.id)
        applySessionModel(animated?.sessionModel)
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
        var history = withContext(ioDispatcher) { runtime.history(summary.id) }
        var sessionModel = withContext(ioDispatcher) { runtime.sessionModel(summary.id) }
        val usage = withContext(ioDispatcher) { runtime.usage(summary.id) }
        val queuedInputs = runtime.queuedInputs(summary.id)
        var activeRun = runtime.activeRuns.value[summary.id]
        if (activeRun != null) {
            activeRun = runtime.activeRuns.value[summary.id]
            if (activeRun == null) {
                history = withContext(ioDispatcher) { runtime.history(summary.id) }
                sessionModel = withContext(ioDispatcher) { runtime.sessionModel(summary.id) }
            }
        }
        if (epoch != latestEpoch) return
        val localized = history.map { message ->
            message.error?.let { raw -> message.copy(error = localizeError(raw)) } ?: message
        }
        val title = summary.title.ifBlank { titleFrom(localized) }
        var snapshot = ConversationState(
            messages = localized,
            title = title,
            loaded = true,
            usage = usage,
            sessionModel = sessionModel,
            queuedInputs = queuedInputs,
        )
        if (activeRun != null) {
            snapshot = snapshot.adoptActiveRun(activeRun.runId, RuntimeActivity.Thinking, activeRun.startedAt)
        }
        val resolved = decorateNotices(snapshot, summary.id)
        rememberState(summary.id, resolved)
        if (isActive(summary.id)) {
            update {
                it.copy(
                    conversation = resolved.copy(loaded = true),
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
                val sessionModel = withContext(ioDispatcher) { runtime.sessionModel(opened.sessionId) }
                rememberState(opened.sessionId, ConversationState(sessionModel = sessionModel))
                update {
                    it.copy(
                        conversation = ConversationState(loaded = true),
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
                setActive(opened.sessionId)
                applySessionModel(sessionModel)
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
                takeCached(sessionId)?.let { snapshot -> rememberState(reopenedId, snapshot) }
                update { it.copy(conversationId = reopenedId, error = null, reloading = false) }
                setActive(reopenedId)
            } else {
                update { it.copy(error = null, reloading = false) }
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
