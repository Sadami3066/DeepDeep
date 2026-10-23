package io.github.whywood.deepdeep.ui.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.whywood.deepdeep.data.AttachmentImport
import io.github.whywood.deepdeep.data.AttachmentRef
import io.github.whywood.deepdeep.data.AttachmentStore
import io.github.whywood.deepdeep.data.encodeAttachmentBlock
import io.github.whywood.deepdeep.runtime.AgentRuntime
import io.github.whywood.deepdeep.runtime.AgentRuntimeHost
import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.AssistantStep
import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.runtime.ModelConfig
import io.github.whywood.deepdeep.runtime.ModelSettings
import io.github.whywood.deepdeep.runtime.ProviderDirectory
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import io.github.whywood.deepdeep.runtime.SessionRuntimeEvent
import io.github.whywood.deepdeep.runtime.ThinkingLevel
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val STREAM_DELTA_BATCH_MS = 16L

class ChatViewModel(
    private val runtime: AgentRuntime,
    private val providers: ProviderDirectory,
    private val modelConfigStore: ModelSettings,
    private val attachmentStore: AttachmentStore,
    private val prepareRuntime: suspend () -> Unit,
    private val uiStrings: ChatUiStrings = ChatUiStrings.fallback(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = mutableState.asStateFlow()

    private val conversationCache = mutableMapOf<String, ConversationState>()
    private val sessionOpenJobs = mutableMapOf<String, Job>()
    private val pendingDeltas = mutableListOf<RuntimeEvent>()
    private var pendingDeltaJob: Job? = null
    private var pendingDeltaRunId: String? = null

    init {
        viewModelScope.launch { runtime.events.collect(::onSessionEvent) }
        viewModelScope.launch {
            runtime.busySessionIds.collect { ids ->
                mutableState.update { it.copy(busySessionIds = ids) }
            }
        }
        viewModelScope.launch {
            modelConfigStore.config.collect { applyConfig(it) }
        }
        viewModelScope.launch {
            try {
                prepareRuntime()
                withContext(ioDispatcher) { runtime.ensureStarted() }
                val config = providers.resolveConfig(modelConfigStore.read())
                modelConfigStore.save(config)
                applyConfig(config)
                restoreInitialSession()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(ready = true, historyLoaded = true, error = userFacingError(error.message ?: uiStrings.runtimeStartFailed))
                }
            }
        }
    }

    fun onInputChange(value: String) {
        mutableState.update { it.copy(input = value, error = null) }
    }

    fun quote(messageId: Long, text: String) {
        val excerpt = quoteExcerpt(text)
        if (excerpt.isEmpty()) return
        mutableState.update { it.copy(quote = QuotedMessage(messageId, excerpt)) }
    }

    fun clearQuote() {
        mutableState.update { if (it.quote == null) it else it.copy(quote = null) }
    }

    fun attach(uri: Uri) {
        val current = mutableState.value
        if (current.sending || current.switchingConversation) return
        val root = current.conversationCwd.takeIf { it.isNotBlank() }?.let(::File)
        if (root == null) {
            mutableState.update { it.copy(error = uiStrings.preparingSession) }
            return
        }
        if (current.attachments.size >= MAX_ATTACHMENTS) {
            mutableState.update { it.copy(error = uiStrings.maxAttachments) }
            return
        }
        viewModelScope.launch {
            when (val result = withContext(ioDispatcher) { attachmentStore.import(uri, root) }) {
                is AttachmentImport.Imported -> mutableState.update {
                    if (it.attachments.size >= MAX_ATTACHMENTS) {
                        it.copy(error = uiStrings.maxAttachments)
                    } else {
                        it.copy(attachments = it.attachments + result.ref, error = null)
                    }
                }

                is AttachmentImport.TooLarge -> mutableState.update { it.copy(error = uiStrings.attachmentTooLarge) }
                is AttachmentImport.Failed -> mutableState.update { it.copy(error = uiStrings.attachmentFailed) }
            }
        }
    }

    fun removeAttachment(id: String) {
        mutableState.update { state ->
            state.copy(attachments = state.attachments.filterNot { it.id == id })
        }
    }

    fun send() {
        flushPendingDeltas()
        val current = mutableState.value
        if (!current.ready || !current.historyLoaded) {
            mutableState.update { it.copy(error = uiStrings.restoringSession) }
            return
        }
        if (current.conversationId == null) {
            mutableState.update { it.copy(error = uiStrings.sessionNotReady) }
            return
        }
        if (current.sending || current.switchingConversation) return
        val text = current.input.trim()
        if (text.isEmpty()) return
        startTurn(buildPrompt(current.quote, current.attachments, text))
    }

    fun retry(messageId: Long) {
        flushPendingDeltas()
        val current = mutableState.value
        if (!current.ready || !current.historyLoaded) return
        if (current.sending || current.switchingConversation) return
        val index = current.messages.indexOfFirst { it.id == messageId }
        if (index <= 0) return
        val prompt = current.messages.take(index).lastOrNull { it.role == AssistantRole.USER }?.text ?: return
        startTurn(prompt)
    }

    private fun startTurn(prompt: String) {
        val current = mutableState.value
        val sessionId = current.conversationId ?: return
        val nextId = (current.messages.maxOfOrNull { it.id } ?: -1L) + 1
        val assistantId = nextId + 1
        val now = System.currentTimeMillis()
        mutableState.update {
            it.copy(
                messages = it.messages + AssistantMessage(nextId, AssistantRole.USER, prompt) + AssistantMessage(
                    id = assistantId,
                    role = AssistantRole.ASSISTANT,
                    text = "",
                    streaming = true,
                    status = MessageStatus.RUNNING,
                    startedAt = now,
                ),
                input = "",
                quote = null,
                attachments = emptyList(),
                sending = true,
                activity = uiStrings.thinking,
                activityStartedAt = now,
                error = null,
                conversationTitle = it.conversationTitle.ifBlank { titleFrom(prompt) },
            )
        }
        cacheCurrentSession()
        viewModelScope.launch {
            try {
                sessionOpenJobs[sessionId]?.join()
                runtime.send(sessionId, prompt)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                finishRunError(sessionId, error.message ?: uiStrings.requestFailed)
            }
        }
    }

    fun stop() {
        flushPendingDeltas()
        mutableState.value.conversationId?.let(runtime::stop)
    }

    suspend fun reloadSession(): Result<Unit> {
        val current = mutableState.value
        if (current.sending) {
            return Result.failure(IllegalStateException(uiStrings.reloadBusy))
        }
        val sessionId = current.conversationId
            ?: return Result.failure(IllegalStateException(uiStrings.sessionNotReady))
        mutableState.update { it.copy(reloading = true) }
        return try {
            val reopenedId = withContext(ioDispatcher) { runtime.reloadSession(sessionId) }
            if (reopenedId != sessionId) {
                conversationCache.remove(sessionId)?.let { snapshot ->
                    conversationCache[reopenedId] = snapshot
                }
                mutableState.update { it.copy(conversationId = reopenedId, error = null, reloading = false) }
            } else {
                mutableState.update { it.copy(error = null, reloading = false) }
            }
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            mutableState.update { it.copy(reloading = false) }
            throw cancelled
        } catch (error: Exception) {
            mutableState.update { it.copy(reloading = false) }
            Result.failure(IllegalStateException(uiStrings.reloadFailed, error))
        }
    }

    fun newSession(workspaceId: String) {
        viewModelScope.launch {
            cacheCurrentSession()
            createSession(workspaceId)
        }
    }

    fun openConversationById(id: String) {
        val current = mutableState.value
        val known = (current.conversations + current.archivedConversations).firstOrNull { it.id == id }
        if (known != null) {
            openConversation(id)
            return
        }
        viewModelScope.launch {
            val summary = withContext(ioDispatcher) {
                runtime.allSessions().firstOrNull { it.id == id }
                    ?: runtime.archivedSessions().firstOrNull { it.id == id }
            } ?: return@launch
            cacheCurrentSession()
            switchToSummary(summary)
        }
    }

    fun selectWorkspace(workspaceId: String) {
        viewModelScope.launch {
            cacheCurrentSession()
            val workspaces = withContext(ioDispatcher) { runtime.workspaces() }
            val workspace = workspaces.firstOrNull { it.id == workspaceId } ?: return@launch
            mutableState.update { it.copy(currentWorkspaceId = workspace.id, error = null) }
            val sessions = withContext(ioDispatcher) { runtime.sessions(workspace.id) }
            val recent = sessions.firstOrNull()
            if (recent != null) {
                switchToSummary(recent)
            } else {
                createSession(workspace.id)
            }
        }
    }

    fun createWorkspace(name: String) {
        viewModelScope.launch {
            try {
                val workspace = withContext(ioDispatcher) { runtime.createWorkspace(name) }
                val workspaces = withContext(ioDispatcher) { runtime.workspaces() }
                mutableState.update { it.copy(workspaces = workspaces) }
                selectWorkspace(workspace.id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(error = userFacingError(error.message ?: uiStrings.createWorkspaceFailed)) }
            }
        }
    }

    fun renameWorkspace(workspaceId: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                runtime.renameWorkspace(workspaceId, trimmed)
                refreshSessions()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(error = userFacingError(error.message ?: uiStrings.createWorkspaceFailed)) }
            }
        }
    }

    fun deleteWorkspace(workspaceId: String) {
        if (workspaceId == AgentRuntimeHost.HOME_ID) return
        viewModelScope.launch {
            try {
                runtime.deleteWorkspace(workspaceId)
                if (mutableState.value.currentWorkspaceId == workspaceId) {
                    val workspaces = withContext(ioDispatcher) { runtime.workspaces() }
                    val home = workspaces.firstOrNull { it.isHome } ?: workspaces.firstOrNull()
                    if (home != null) selectWorkspace(home.id) else refreshSessions()
                } else {
                    refreshSessions()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(error = userFacingError(error.message ?: uiStrings.createWorkspaceFailed)) }
            }
        }
    }

    suspend fun loadWorkspaceSessionCount(workspaceId: String): Int =
        withContext(ioDispatcher) { runtime.workspaceSessionCount(workspaceId) }

    suspend fun loadSearchSessions(): List<SessionSearchHit> = withContext(ioDispatcher) {
        val workspaces = mutableState.value.workspaces.ifEmpty { runtime.workspaces() }
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

    fun openConversation(id: String) {
        val current = mutableState.value
        if (current.conversationId == id && current.historyLoaded) return
        val summary = (current.conversations + current.archivedConversations).firstOrNull { it.id == id } ?: return
        cacheCurrentSession()
        switchToSummary(summary)
    }

    fun setModel(modelId: String) {
        if (mutableState.value.sending) return
        val current = mutableState.value
        val option = current.models.firstOrNull { it.id == modelId } ?: return
        val thinking = current.thinkingLevel.takeIf { it in option.thinkingLevels } ?: ThinkingLevel.OFF
        viewModelScope.launch {
            try {
                runtime.setModel(current.providerId, option.id, thinking)
                applyConfig(ModelConfig(current.providerId, option.id, thinking))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(error = userFacingError(error.message ?: uiStrings.switchModelFailed)) }
            }
        }
    }

    fun setThinkingLevel(level: ThinkingLevel) {
        if (mutableState.value.sending) return
        viewModelScope.launch {
            try {
                runtime.setThinkingLevel(level)
                mutableState.update { it.copy(thinkingLevel = level) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(error = userFacingError(error.message ?: uiStrings.switchThinkingFailed)) }
            }
        }
    }

    fun toggleStep(messageId: Long, stepId: Long) {
        mutableState.update { state ->
            state.copy(
                messages = state.messages.map { message ->
                    if (message.id != messageId) return@map message
                    message.copy(
                        steps = message.steps.map { step ->
                            when {
                                step is AssistantStep.Reasoning && step.id == stepId ->
                                    step.copy(expanded = !step.expanded)

                                step is AssistantStep.Tool && step.trace.id == stepId ->
                                    step.copy(trace = step.trace.copy(expanded = !step.trace.expanded))

                                else -> step
                            }
                        },
                    )
                },
            )
        }
    }

    fun renameSession(id: String, title: String) {
        viewModelScope.launch {
            runCatching { runtime.renameSession(id, title) }
            refreshSessions()
        }
    }

    fun setSessionPinned(id: String, pinned: Boolean) {
        viewModelScope.launch {
            runCatching { runtime.setSessionPinned(id, pinned) }
            refreshSessions()
        }
    }

    fun setSessionArchived(id: String, archived: Boolean) {
        viewModelScope.launch {
            runCatching { runtime.setSessionArchived(id, archived) }
            refreshSessions()
        }
    }

    fun deleteSession(id: String) {
        viewModelScope.launch {
            val wasActive = mutableState.value.conversationId == id
            if (wasActive) {
                pendingDeltaJob?.cancel()
                pendingDeltaJob = null
                pendingDeltas.clear()
                pendingDeltaRunId = null
            }
            conversationCache.remove(id)
            if (wasActive) runtime.stop(id)
            runCatching { withContext(ioDispatcher) { runtime.deleteSession(id) } }
            if (wasActive) {
                val workspaceId = mutableState.value.currentWorkspaceId
                val sessions = withContext(ioDispatcher) { runtime.sessions(workspaceId) }
                val next = sessions.firstOrNull()
                if (next != null) switchToSummary(next) else createSession(workspaceId)
            } else {
                refreshSessions()
            }
        }
    }

    private suspend fun restoreInitialSession() {
        val workspaces = withContext(ioDispatcher) { runtime.workspaces() }
        val sessions = withContext(ioDispatcher) {
            workspaces.flatMap { workspace -> runtime.sessions(workspace.id) }
        }
        val recent = sessions.maxByOrNull { it.updatedAt }
        val home = workspaces.firstOrNull { it.isHome } ?: workspaces.firstOrNull()
        if (home == null) {
            mutableState.update {
                it.copy(ready = true, historyLoaded = true, error = uiStrings.runtimeStartFailed)
            }
            return
        }
        val workspace = recent?.let { summary -> workspaces.firstOrNull { it.cwd == summary.cwd } } ?: home
        mutableState.update {
            it.copy(
                ready = true,
                workspaces = workspaces,
                currentWorkspaceId = workspace.id,
                conversationCwd = recent?.cwd ?: home.cwd,
                conversations = sessions.map { session -> session.copy(title = localizedTitle(session.title)) },
                historyLoaded = recent != null,
            )
        }
        if (recent != null) {
            switchToSummary(recent)
        } else {
            createSession(workspace.id)
        }
    }

    private fun switchToSummary(summary: ConversationSummary) {
        val cached = conversationCache[summary.id]
        val workspaceId = mutableState.value.workspaces
            .firstOrNull { workspace -> workspace.cwd == summary.cwd }
            ?.id
            ?: mutableState.value.currentWorkspaceId
        mutableState.update {
            it.copy(
                conversationId = summary.id,
                conversationCwd = summary.cwd,
                currentWorkspaceId = workspaceId,
                conversationTitle = cached?.title?.ifBlank { summary.title } ?: summary.title,
                messages = cached?.messages ?: emptyList(),
                sending = cached?.sending ?: false,
                activity = cached?.activity,
                activityStartedAt = cached?.activityStartedAt,
                historyLoaded = cached?.loaded == true,
                switchingConversation = cached == null,
                input = "",
                quote = null,
                attachments = emptyList(),
                error = null,
            )
        }
        openSession(summary)
    }

    private fun openSession(summary: ConversationSummary) {
        if (sessionOpenJobs.containsKey(summary.id)) return
        val job = viewModelScope.launch {
            try {
                withContext(ioDispatcher) { runtime.openSession(summary.filePath, summary.cwd) }
                val history = withContext(ioDispatcher) { runtime.history(summary.id) }
                val activeRunId = withContext(ioDispatcher) { runtime.activeRunId(summary.id) }
                val localized = history.map { message ->
                    message.error?.let { raw -> message.copy(error = uiStrings.runError(raw)) } ?: message
                }
                val title = summary.title.ifBlank { titleFrom(localized) }
                val snapshot = if (activeRunId == null) {
                    ConversationState(messages = localized, title = title, loaded = true)
                } else {
                    ConversationState(messages = localized, title = title, loaded = true)
                        .adoptActiveRun(activeRunId, uiStrings.thinking, System.currentTimeMillis())
                }
                conversationCache[summary.id] = snapshot
                if (mutableState.value.conversationId == summary.id) {
                    mutableState.update {
                        it.copy(
                            messages = snapshot.messages,
                            conversationTitle = snapshot.title,
                            sending = snapshot.sending,
                            activity = snapshot.activity,
                            activityStartedAt = snapshot.activityStartedAt,
                            historyLoaded = true,
                            switchingConversation = false,
                        )
                    }
                }
                refreshSessions()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (mutableState.value.conversationId == summary.id) {
                    mutableState.update {
                        it.copy(switchingConversation = false, error = userFacingError(error.message ?: uiStrings.openConversationFailed))
                    }
                }
            } finally {
                sessionOpenJobs.remove(summary.id)
            }
        }
        sessionOpenJobs[summary.id] = job
    }

    private fun createSession(workspaceId: String) {
        viewModelScope.launch {
            try {
                val opened = withContext(ioDispatcher) { runtime.newSession(workspaceId) }
                val projectCwd = opened.cwd
                conversationCache[opened.sessionId] = ConversationState()
                mutableState.update {
                    it.copy(
                        conversationId = opened.sessionId,
                        conversationCwd = projectCwd,
                        currentWorkspaceId = it.workspaces.firstOrNull { workspace -> workspace.cwd == projectCwd }?.id
                            ?: workspaceId,
                        conversationTitle = "",
                        messages = emptyList(),
                        sending = false,
                        activity = null,
                        activityStartedAt = null,
                        historyLoaded = true,
                        switchingConversation = false,
                        input = "",
                        quote = null,
                        attachments = emptyList(),
                        error = null,
                    )
                }
                refreshSessions()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(error = userFacingError(error.message ?: uiStrings.newChatFailed)) }
            }
        }
    }

    private fun cacheCurrentSession() {
        val current = mutableState.value
        val id = current.conversationId ?: return
        if (!current.historyLoaded) return
        flushPendingDeltas()
        val loaded = conversationCache[id]?.loaded == true
        conversationCache[id] = ConversationState(
            messages = current.messages,
            title = current.conversationTitle,
            sending = current.sending,
            activity = current.activity,
            activityStartedAt = current.activityStartedAt,
            loaded = loaded,
        )
    }

    private fun onSessionEvent(scoped: SessionRuntimeEvent) {
        val event = scoped.event
        when (event) {
            is RuntimeEvent.SessionListChanged -> {
                flushPendingDeltas()
                viewModelScope.launch { refreshSessions() }
                return
            }

            is RuntimeEvent.SessionRemoved -> {
                flushPendingDeltas()
                viewModelScope.launch { refreshSessions() }
                return
            }

            is RuntimeEvent.SessionArchived -> {
                flushPendingDeltas()
                viewModelScope.launch { refreshSessions() }
                return
            }

            else -> Unit
        }
        val activeId = mutableState.value.conversationId
        if (scoped.sessionId == activeId) {
            if (event is RuntimeEvent.TextDelta || event is RuntimeEvent.ReasoningDelta) {
                bufferActiveDelta(event)
                return
            }
            flushPendingDeltas()
            val reduced = reduce(mutableState.value.conversationState(), event, uiStrings)
            mutableState.update { base ->
                val updated = base.applyConversation(reduced)
                if (event is RuntimeEvent.RunFailed && reduced.messages.none { it.status == MessageStatus.FAILED }) {
                    updated.copy(error = uiStrings.runError(event.message))
                } else if (event is RuntimeEvent.RunFailed) {
                    updated.copy(error = null)
                } else {
                    updated
                }
            }
            cacheCurrentSession()
        } else {
            val cached = conversationCache[scoped.sessionId] ?: ConversationState()
            conversationCache[scoped.sessionId] = reduce(cached, event, uiStrings)
        }
    }

    private fun bufferActiveDelta(event: RuntimeEvent) {
        val runId = when (event) {
            is RuntimeEvent.TextDelta -> event.runId
            is RuntimeEvent.ReasoningDelta -> event.runId
            else -> return
        }
        if (pendingDeltas.isNotEmpty() && pendingDeltaRunId != runId) {
            flushPendingDeltas()
        }
        pendingDeltaRunId = runId
        pendingDeltas += event
        if (pendingDeltaJob == null) {
            pendingDeltaJob = viewModelScope.launch {
                delay(STREAM_DELTA_BATCH_MS)
                pendingDeltaJob = null
                applyPendingDeltas()
            }
        }
    }

    private fun flushPendingDeltas() {
        pendingDeltaJob?.cancel()
        pendingDeltaJob = null
        applyPendingDeltas()
    }

    private fun applyPendingDeltas() {
        if (pendingDeltas.isEmpty()) return
        val events = pendingDeltas.toList()
        pendingDeltas.clear()
        pendingDeltaRunId = null
        var snapshot = mutableState.value.conversationState()
        events.forEach { event -> snapshot = reduce(snapshot, event, uiStrings) }
        val reduced = snapshot
        mutableState.update { it.applyConversation(reduced) }
    }

    private fun ChatUiState.conversationState(): ConversationState = ConversationState(
        messages = messages,
        title = conversationTitle,
        sending = sending,
        activity = activity,
        activityStartedAt = activityStartedAt,
    )

    private fun ChatUiState.applyConversation(state: ConversationState): ChatUiState = copy(
        messages = state.messages,
        conversationTitle = state.title,
        sending = state.sending,
        activity = state.activity,
        activityStartedAt = state.activityStartedAt,
    )

    private fun finishRunError(sessionId: String, message: String) {
        if (mutableState.value.conversationId != sessionId) return
        val reduced = mutableState.value.conversationState().finishWithError("", message, uiStrings)
        val hasFailure = reduced.messages.any { it.status == MessageStatus.FAILED && it.error != null }
        mutableState.update {
            it.applyConversation(reduced).copy(error = if (hasFailure) it.error else uiStrings.runError(message))
        }
        cacheCurrentSession()
    }

    private suspend fun refreshSessions() {
        val current = mutableState.value
        val workspaces = withContext(ioDispatcher) { runtime.workspaces() }
        val workspace = workspaces.firstOrNull { it.id == current.currentWorkspaceId }
            ?: workspaces.firstOrNull { it.isHome }
            ?: workspaces.firstOrNull()
        val sessions = withContext(ioDispatcher) { runtime.allSessions() }
        val archived = withContext(ioDispatcher) { runtime.archivedSessions() }
        val visibleIds = (sessions + archived).map { it.id }.toSet()
        conversationCache.keys.retainAll(visibleIds)
        mutableState.update {
            val title = it.conversationId?.let { id -> sessions.firstOrNull { item -> item.id == id }?.title }
            it.copy(
                ready = true,
                workspaces = workspaces,
                currentWorkspaceId = workspace?.id ?: it.currentWorkspaceId,
                conversations = sessions.map { session -> session.copy(title = localizedTitle(session.title)) },
                archivedConversations = archived.map { session -> session.copy(title = localizedTitle(session.title)) },
                archivedCount = archived.size,
                conversationTitle = title?.let(::localizedTitle) ?: it.conversationTitle,
            )
        }
        cacheCurrentSession()
    }

    private fun quoteExcerpt(text: String): String {
        val plain = markdownToPlainText(text.take(4000))
            .lineSequence()
            .map { it.replace(Regex("[ \\t]+"), " ").trim() }
            .filter { it.isNotEmpty() }
            .take(3)
            .joinToString("\n")
        return if (plain.length <= 240) plain else plain.take(240).trimEnd() + "…"
    }

    private fun buildPrompt(
        quote: QuotedMessage?,
        attachments: List<AttachmentRef>,
        body: String,
    ): String = buildString {
        if (quote != null) {
            append(quote.text.lineSequence().joinToString("\n") { "> $it" })
            append("\n\n")
        }
        if (attachments.isNotEmpty()) {
            append(encodeAttachmentBlock(attachments))
            append("\n\n")
        }
        append(body)
    }

    private fun applyConfig(config: ModelConfig) {
        val options = providers.models(config.providerId)
        val model = options.firstOrNull { it.id == config.modelId } ?: options.firstOrNull()
        mutableState.update {
            it.copy(
                providerId = config.providerId,
                modelId = model?.id ?: config.modelId,
                modelLabel = model?.displayName ?: config.modelId,
                thinkingLevel = config.thinkingLevel,
                models = options,
            )
        }
    }

    private fun consumeFinalText(steps: List<AssistantStep>, finalText: String): List<AssistantStep> {
        val final = finalText.trim()
        if (final.isEmpty()) return steps
        val last = steps.lastOrNull() as? AssistantStep.Text ?: return steps
        val candidate = last.text.trim()
        return if (candidate.isNotEmpty() && (final == candidate || final.endsWith(candidate))) {
            steps.dropLast(1)
        } else {
            steps
        }
    }

    private fun userFacingError(message: String): String = when {
        message.contains("API key", ignoreCase = true) || message.contains("401") -> uiStrings.invalidApiKey
        message.contains("429") -> uiStrings.rateLimited
        message.contains("timed out", ignoreCase = true) -> uiStrings.timeout
        message.contains("Network", ignoreCase = true) -> uiStrings.networkError
        message.contains("tool input", ignoreCase = true) || message.contains("invalid JSON", ignoreCase = true) ->
            uiStrings.invalidToolArgs

        message.contains("500") || message.contains("503") -> uiStrings.serviceUnavailable
        else -> message
    }

    private fun titleFrom(text: String): String {
        val normalized = text.trim().replace(Regex("\\s+"), " ")
        return if (normalized.length <= 28) normalized else normalized.take(28) + "…"
    }

    private fun localizedTitle(title: String): String =
        if (title.isBlank() || title == LEGACY_NEW_TITLE) uiStrings.newChatTitle else title

    private fun titleFrom(messages: List<AssistantMessage>): String {
        val firstUser = messages.firstOrNull { it.role == AssistantRole.USER }?.text ?: return uiStrings.newChatTitle
        return titleFrom(firstUser)
    }

    private companion object {
        const val MAX_ATTACHMENTS = 15
        const val LEGACY_NEW_TITLE = "新对话"
    }
}
