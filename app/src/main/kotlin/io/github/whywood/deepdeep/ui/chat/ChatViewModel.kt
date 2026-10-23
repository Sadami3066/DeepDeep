package io.github.whywood.deepdeep.ui.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.whywood.deepdeep.data.AttachmentImport
import io.github.whywood.deepdeep.data.AttachmentRef
import io.github.whywood.deepdeep.data.AttachmentStore
import io.github.whywood.deepdeep.data.encodeAttachmentBlock
import io.github.whywood.deepdeep.runtime.AgentRuntime
import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.AssistantStep
import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.runtime.ModelConfig
import io.github.whywood.deepdeep.runtime.ModelSettings
import io.github.whywood.deepdeep.runtime.ProviderDirectory
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import io.github.whywood.deepdeep.runtime.RuntimeProject
import io.github.whywood.deepdeep.runtime.SessionRuntimeEvent
import io.github.whywood.deepdeep.runtime.RunStatus
import io.github.whywood.deepdeep.runtime.ThinkingLevel
import io.github.whywood.deepdeep.runtime.ToolActivityStatus
import io.github.whywood.deepdeep.runtime.ToolTrace
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ConversationSnapshot(
    val messages: List<AssistantMessage> = emptyList(),
    val title: String = "",
    val sending: Boolean = false,
    val activity: String? = null,
    val activityStartedAt: Long? = null,
)

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

    private val conversationCache = mutableMapOf<String, ConversationSnapshot>()
    private val sessionOpenJobs = mutableMapOf<String, Job>()

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
        val current = mutableState.value
        if (!current.ready || !current.historyLoaded) {
            mutableState.update { it.copy(error = uiStrings.restoringSession) }
            return
        }
        val sessionId = current.conversationId
        if (sessionId == null) {
            mutableState.update { it.copy(error = uiStrings.sessionNotReady) }
            return
        }
        if (current.sending || current.switchingConversation) return
        val text = current.input.trim()
        if (text.isEmpty()) return
        val prompt = buildPrompt(current.quote, current.attachments, text)
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
                conversationTitle = it.conversationTitle.ifBlank { titleFrom(text) },
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
                finishWithError(sessionId, error.message ?: uiStrings.requestFailed)
            }
        }
    }

    fun stop() {
        mutableState.value.conversationId?.let(runtime::stop)
    }

    fun newChat() {
        viewModelScope.launch {
            cacheCurrentSession()
            createSession(mutableState.value.currentProjectId)
        }
    }

    fun selectProject(projectId: String) {
        if (mutableState.value.currentProjectId == projectId) return
        viewModelScope.launch {
            cacheCurrentSession()
            val projects = withContext(ioDispatcher) { runtime.projects() }
            val project = projects.firstOrNull { it.id == projectId } ?: return@launch
            mutableState.update { it.copy(currentProjectId = project.id, error = null) }
            val sessions = withContext(ioDispatcher) { runtime.sessions(project.id) }
            refreshProjectSessions(project, sessions)
            val recent = sessions.firstOrNull()
            if (recent != null) {
                switchToSummary(recent)
            } else {
                createSession(project.id)
            }
        }
    }

    fun createProject(name: String) {
        viewModelScope.launch {
            try {
                val project = withContext(ioDispatcher) { runtime.createProject(name) }
                val projects = withContext(ioDispatcher) { runtime.projects() }
                mutableState.update { it.copy(projects = projects) }
                selectProject(project.id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(error = userFacingError(error.message ?: uiStrings.createProjectFailed)) }
            }
        }
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
            conversationCache.remove(id)
            if (wasActive) runtime.stop(id)
            runCatching { withContext(ioDispatcher) { runtime.deleteSession(id) } }
            if (wasActive) {
                val projectId = mutableState.value.currentProjectId
                val sessions = withContext(ioDispatcher) { runtime.sessions(projectId) }
                val next = sessions.firstOrNull()
                if (next != null) switchToSummary(next) else createSession(projectId)
            } else {
                refreshSessions()
            }
        }
    }

    private suspend fun restoreInitialSession() {
        val projects = withContext(ioDispatcher) { runtime.projects() }
        val sessions = withContext(ioDispatcher) {
            projects.flatMap { project -> runtime.sessions(project.id) }
        }
        val recent = sessions.maxByOrNull { it.updatedAt }
        val home = projects.firstOrNull { it.isHome } ?: projects.firstOrNull()
        if (home == null) {
            mutableState.update {
                it.copy(ready = true, historyLoaded = true, error = "运行时未就绪")
            }
            return
        }
        val project = recent?.let { summary -> projects.firstOrNull { it.cwd == summary.cwd } } ?: home
        mutableState.update {
            it.copy(
                ready = true,
                projects = projects,
                currentProjectId = project.id,
                conversationCwd = recent?.cwd ?: home.cwd,
                conversations = sessions.filter { session -> session.cwd == project.cwd }
                    .map { session -> session.copy(title = localizedTitle(session.title)) },
                historyLoaded = recent != null,
            )
        }
        if (recent != null) {
            switchToSummary(recent)
        } else {
            createSession(project.id)
        }
    }

    private fun switchToSummary(summary: ConversationSummary) {
        val cached = conversationCache[summary.id]
        val projectId = mutableState.value.projects
            .firstOrNull { project -> project.cwd == summary.cwd }
            ?.id
            ?: mutableState.value.currentProjectId
        mutableState.update {
            it.copy(
                conversationId = summary.id,
                conversationCwd = summary.cwd,
                currentProjectId = projectId,
                conversationTitle = cached?.title?.ifBlank { summary.title } ?: summary.title,
                messages = cached?.messages ?: emptyList(),
                sending = cached?.sending ?: false,
                activity = cached?.activity,
                activityStartedAt = cached?.activityStartedAt,
                historyLoaded = cached != null,
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
        val cached = conversationCache[summary.id]
        val job = viewModelScope.launch {
            try {
                withContext(ioDispatcher) { runtime.openSession(summary.filePath, summary.cwd) }
                if (cached == null) {
                    val messages = withContext(ioDispatcher) { runtime.history(summary.id) }
                    val snapshot = ConversationSnapshot(
                        messages = messages,
                        title = summary.title.ifBlank { titleFrom(messages) },
                    )
                    conversationCache[summary.id] = snapshot
                    if (mutableState.value.conversationId == summary.id) {
                        mutableState.update {
                            it.copy(
                                messages = snapshot.messages,
                                conversationTitle = snapshot.title,
                                historyLoaded = true,
                                switchingConversation = false,
                            )
                        }
                    }
                } else {
                    if (mutableState.value.conversationId == summary.id) {
                        mutableState.update { it.copy(historyLoaded = true, switchingConversation = false) }
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

    private fun createSession(projectId: String) {
        viewModelScope.launch {
            try {
                val opened = withContext(ioDispatcher) { runtime.newSession(projectId) }
                val projectCwd = opened.cwd
                conversationCache[opened.sessionId] = ConversationSnapshot()
                mutableState.update {
                    it.copy(
                        conversationId = opened.sessionId,
                        conversationCwd = projectCwd,
                        currentProjectId = it.projects.firstOrNull { project -> project.cwd == projectCwd }?.id
                            ?: projectId,
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
        conversationCache[id] = ConversationSnapshot(
            messages = current.messages,
            title = current.conversationTitle,
            sending = current.sending,
            activity = current.activity,
            activityStartedAt = current.activityStartedAt,
        )
    }

    private fun onSessionEvent(scoped: SessionRuntimeEvent) {
        val event = scoped.event
        if (event is RuntimeEvent.SessionListChanged) {
            viewModelScope.launch { refreshSessions() }
            return
        }
        val activeId = mutableState.value.conversationId
        if (scoped.sessionId == activeId) {
            val reduced = reduce(mutableState.value.snapshot(), event)
            mutableState.update {
                it.copy(
                    messages = reduced.messages,
                    sending = reduced.sending,
                    activity = reduced.activity,
                    activityStartedAt = reduced.activityStartedAt,
                    error = if (event is RuntimeEvent.RunFailed) {
                        userFacingError(event.message.ifBlank { uiStrings.requestFailed })
                    } else {
                        it.error
                    },
                )
            }
        } else {
            val snapshot = conversationCache[scoped.sessionId] ?: ConversationSnapshot()
            conversationCache[scoped.sessionId] = reduce(snapshot, event)
        }
    }

    private fun ChatUiState.snapshot(): ConversationSnapshot = ConversationSnapshot(
        messages = messages,
        title = conversationTitle,
        sending = sending,
        activity = activity,
        activityStartedAt = activityStartedAt,
    )

    private fun reduce(snapshot: ConversationSnapshot, event: RuntimeEvent): ConversationSnapshot = when (event) {
        is RuntimeEvent.RunStarted -> {
            val target = snapshot.messages.lastOrNull { it.role == AssistantRole.ASSISTANT && it.streaming }
            if (target == null) {
                snapshot
            } else {
                snapshot.copy(
                    messages = snapshot.messages.map {
                        if (it.id == target.id) it.copy(runId = event.runId) else it
                    },
                    sending = true,
                    activity = uiStrings.thinking,
                    activityStartedAt = event.startedAt,
                )
            }
        }

        is RuntimeEvent.ActivityChanged -> {
            if (!snapshot.matchesRun(event.runId)) {
                snapshot
            } else {
                val label = uiStrings.activity(event.activity)
                snapshot.copy(
                    activity = label,
                    activityStartedAt = if (label == null) null else {
                        snapshot.activityStartedAt ?: System.currentTimeMillis()
                    },
                )
            }
        }

        is RuntimeEvent.TextDelta -> {
            if (!snapshot.matchesRun(event.runId)) {
                snapshot
            } else {
                snapshot.updateAssistant(event.runId) { message ->
                    val steps = message.steps.toMutableList()
                    val last = steps.lastOrNull()
                    if (last is AssistantStep.Text) {
                        steps[steps.lastIndex] = last.copy(text = last.text + event.text)
                    } else {
                        steps += AssistantStep.Text(nextStepId(steps), event.text)
                    }
                    message.copy(steps = steps)
                }
            }
        }

        is RuntimeEvent.ReasoningDelta -> {
            if (!snapshot.matchesRun(event.runId)) {
                snapshot
            } else {
                snapshot.updateAssistant(event.runId) { message ->
                    val steps = message.steps.toMutableList()
                    val last = steps.lastOrNull()
                    if (last is AssistantStep.Reasoning) {
                        steps[steps.lastIndex] = last.copy(text = last.text + event.text)
                    } else {
                        steps += AssistantStep.Reasoning(nextStepId(steps), event.text)
                    }
                    message.copy(steps = steps)
                }
            }
        }

        is RuntimeEvent.ToolStarted -> {
            if (!snapshot.matchesRun(event.runId)) {
                snapshot
            } else {
                snapshot.updateAssistant(event.runId) { message ->
                    message.copy(
                        steps = message.steps + AssistantStep.Tool(
                            ToolTrace(
                                id = nextStepId(message.steps),
                                name = event.name,
                                arguments = event.arguments,
                                toolCallId = event.callId,
                                startedAt = event.startedAt,
                            ),
                        ),
                    )
                }
            }
        }

        is RuntimeEvent.ToolFinished -> {
            if (!snapshot.matchesRun(event.runId)) {
                snapshot
            } else {
                snapshot.updateAssistant(event.runId) { message ->
                    val index = message.steps.indexOfLast { step ->
                        step is AssistantStep.Tool && step.trace.toolCallId == event.callId
                    }
                    if (index == -1) {
                        message.copy(
                            steps = message.steps + AssistantStep.Tool(
                                ToolTrace(
                                    id = nextStepId(message.steps),
                                    name = event.name,
                                    arguments = emptyMap(),
                                    status = if (event.isError) ToolActivityStatus.FAILED else ToolActivityStatus.DONE,
                                    output = event.result.takeIf { !event.isError && it.isNotBlank() },
                                    error = event.result.takeIf { event.isError && it.isNotBlank() },
                                    toolCallId = event.callId,
                                    durationMs = event.durationMs,
                                ),
                            ),
                        )
                    } else {
                        val trace = (message.steps[index] as AssistantStep.Tool).trace.copy(
                            status = if (event.isError) ToolActivityStatus.FAILED else ToolActivityStatus.DONE,
                            output = event.result.takeIf { !event.isError && it.isNotBlank() },
                            error = event.result.takeIf { event.isError && it.isNotBlank() },
                            durationMs = event.durationMs,
                        )
                        message.copy(
                            steps = message.steps.toMutableList().also { it[index] = AssistantStep.Tool(trace) },
                        )
                    }
                }
            }
        }

        is RuntimeEvent.RunFinished -> finishWithStatus(snapshot, event.runId, event.text, event.status, event.finishedAt)

        is RuntimeEvent.RunFailed -> finishWithError(snapshot, event.runId, event.message)

        is RuntimeEvent.SessionListChanged -> snapshot
    }

    private fun ConversationSnapshot.matchesRun(runId: String): Boolean =
        messages.any { it.runId == runId }

    private fun ConversationSnapshot.updateAssistant(
        runId: String,
        transform: (AssistantMessage) -> AssistantMessage,
    ): ConversationSnapshot {
        val index = messages.indexOfLast { it.runId == runId }
        if (index == -1) return this
        val target = messages[index]
        return copy(messages = messages.toMutableList().also { it[index] = transform(target) })
    }

    private fun nextStepId(steps: List<AssistantStep>): Long =
        (steps.maxOfOrNull { it.id } ?: -1L) + 1

    private fun finishWithStatus(
        snapshot: ConversationSnapshot,
        runId: String,
        text: String,
        status: RunStatus,
        finishedAt: Long,
    ): ConversationSnapshot {
        val index = snapshot.messages.indexOfLast { it.runId == runId }
        if (index == -1) return snapshot.copy(sending = false, activity = null, activityStartedAt = null)
        val message = snapshot.messages[index]
        val finalText = text.ifBlank { message.text }
        val finalized = message.copy(
            text = finalText,
            streaming = false,
            status = when (status) {
                RunStatus.DONE -> MessageStatus.DONE
                RunStatus.STOPPED -> MessageStatus.STOPPED
                RunStatus.FAILED -> MessageStatus.FAILED
            },
            finishedAt = finishedAt,
            steps = consumeFinalText(
                message.steps.map { step ->
                    if (step is AssistantStep.Tool && step.trace.status == ToolActivityStatus.RUNNING) {
                        step.copy(trace = step.trace.copy(status = ToolActivityStatus.FAILED))
                    } else {
                        step
                    }
                },
                finalText,
            ),
        )
        return snapshot.copy(
            messages = snapshot.messages.toMutableList().also { it[index] = finalized },
            sending = false,
            activity = null,
            activityStartedAt = null,
        )
    }

    private fun finishWithError(
        snapshot: ConversationSnapshot,
        runId: String,
        message: String,
    ): ConversationSnapshot {
        val index = snapshot.messages.indexOfLast { it.runId == runId }
        if (index == -1) return snapshot.copy(sending = false, activity = null, activityStartedAt = null)
        val current = snapshot.messages[index]
        val failed = current.copy(
            text = current.text.ifBlank { uiStrings.emptyReply },
            streaming = false,
            status = MessageStatus.FAILED,
            finishedAt = System.currentTimeMillis(),
            steps = current.steps.map { step ->
                if (step is AssistantStep.Tool && step.trace.status == ToolActivityStatus.RUNNING) {
                    step.copy(trace = step.trace.copy(status = ToolActivityStatus.FAILED))
                } else {
                    step
                }
            },
        )
        return snapshot.copy(
            messages = snapshot.messages.toMutableList().also { it[index] = failed },
            sending = false,
            activity = null,
            activityStartedAt = null,
        )
    }

    private fun finishWithError(sessionId: String, message: String) {
        if (mutableState.value.conversationId != sessionId) return
        val reduced = finishWithError(mutableState.value.snapshot(), "", message)
        mutableState.update {
            it.copy(
                messages = reduced.messages,
                sending = reduced.sending,
                activity = reduced.activity,
                activityStartedAt = reduced.activityStartedAt,
                error = userFacingError(message),
            )
        }
    }

    private suspend fun refreshSessions() {
        val current = mutableState.value
        val projects = withContext(ioDispatcher) { runtime.projects() }
        val project = projects.firstOrNull { it.id == current.currentProjectId }
            ?: projects.firstOrNull { it.isHome }
            ?: projects.firstOrNull()
        val sessions = project?.let { withContext(ioDispatcher) { runtime.sessions(it.id) } }.orEmpty()
        val archived = withContext(ioDispatcher) { runtime.archivedSessions() }
        mutableState.update {
            val title = it.conversationId?.let { id -> sessions.firstOrNull { item -> item.id == id }?.title }
            it.copy(
                ready = true,
                projects = projects,
                currentProjectId = project?.id ?: it.currentProjectId,
                conversations = sessions.map { session -> session.copy(title = localizedTitle(session.title)) },
                archivedConversations = archived.map { session -> session.copy(title = localizedTitle(session.title)) },
                archivedCount = archived.size,
                conversationTitle = title?.let(::localizedTitle) ?: it.conversationTitle,
            )
        }
        cacheCurrentSession()
    }

    private fun refreshProjectSessions(project: RuntimeProject, sessions: List<ConversationSummary>) {
        mutableState.update {
            it.copy(
                currentProjectId = project.id,
                conversations = sessions.map { session -> session.copy(title = localizedTitle(session.title)) },
            )
        }
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
        if (title.isBlank() || title == "新对话") uiStrings.newChatTitle else title

    private fun titleFrom(messages: List<AssistantMessage>): String {
        val firstUser = messages.firstOrNull { it.role == AssistantRole.USER }?.text ?: return "新对话"
        return titleFrom(firstUser)
    }

    private companion object {
        const val MAX_ATTACHMENTS = 5
    }
}
