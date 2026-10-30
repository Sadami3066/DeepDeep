package io.github.whywood.deepdeep.ui.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.whywood.deepdeep.data.AttachmentImport
import io.github.whywood.deepdeep.data.AttachmentRef
import io.github.whywood.deepdeep.data.AttachmentStore
import io.github.whywood.deepdeep.data.ImageNormalizer
import io.github.whywood.deepdeep.data.NormalizedImage
import io.github.whywood.deepdeep.runtime.AgentRuntime
import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.AssistantStep
import io.github.whywood.deepdeep.runtime.CompactionOutcome
import io.github.whywood.deepdeep.runtime.InterruptedQuestion
import io.github.whywood.deepdeep.runtime.InputQueueKind
import io.github.whywood.deepdeep.runtime.InterruptedRun
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.runtime.ModelSettings
import io.github.whywood.deepdeep.runtime.ProviderDirectory
import io.github.whywood.deepdeep.runtime.QueuedInput
import io.github.whywood.deepdeep.runtime.RuntimeActivity
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import io.github.whywood.deepdeep.runtime.SessionModelSnapshot
import io.github.whywood.deepdeep.runtime.SessionRuntimeEvent
import io.github.whywood.deepdeep.runtime.SkillExpander
import io.github.whywood.deepdeep.runtime.SkillSummary
import io.github.whywood.deepdeep.runtime.ThinkingLevel
import io.github.whywood.deepdeep.runtime.TurnNotice
import io.github.whywood.deepdeep.ui.chat.state.AutoRetryController
import io.github.whywood.deepdeep.ui.chat.state.ConversationCache
import io.github.whywood.deepdeep.ui.chat.state.ConversationWriteTarget
import io.github.whywood.deepdeep.ui.chat.state.DeltaBatcher
import io.github.whywood.deepdeep.ui.chat.state.SessionDraftStore
import io.github.whywood.deepdeep.ui.chat.state.SessionOpener
import io.github.whywood.deepdeep.ui.chat.state.chatError
import io.github.whywood.deepdeep.ui.chat.state.SessionListController
import io.github.whywood.deepdeep.ui.chat.state.ModelController
import io.github.whywood.deepdeep.ui.chat.state.UsageController
import io.github.whywood.deepdeep.ui.chat.state.WorkspaceController
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val BUSY_RECONCILE_DELAY_MS = 500L
private const val STOP_RECONCILE_DELAY_MS = 500L

class ChatViewModel(
    private val runtime: AgentRuntime,
    private val providers: ProviderDirectory,
    private val modelConfigStore: ModelSettings,
    private val attachmentStore: AttachmentStore,
    private val prepareRuntime: suspend () -> Unit,
    private val autoRetry: Flow<Boolean> = flowOf(false),
    @Volatile private var uiStrings: ChatUiStrings = ChatUiStrings.fallback(),
    private val skillExpander: SkillExpander? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val interruptedQuestions: suspend (String) -> List<InterruptedQuestion> = { emptyList() },
    private val dismissInterruptedQuestions: suspend (String) -> Unit = {},
    private val interruptedRuns: suspend (String) -> List<InterruptedRun> = { emptyList() },
    private val dismissInterruptedRun: suspend (String) -> Unit = {},
    private val normalizeImage: suspend (File, String) -> NormalizedImage? = { file, mime ->
        ImageNormalizer.normalize(file, mime)
    },
) : ViewModel() {
    private val mutableState = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = mutableState.asStateFlow()
    private val mutableAvailableSkills = MutableStateFlow<List<SkillSummary>>(emptyList())
    val availableSkills: StateFlow<List<SkillSummary>> = mutableAvailableSkills.asStateFlow()

    private val sessionDrafts = SessionDraftStore(
        viewModelScope, ioDispatcher, attachmentStore, normalizeImage,
        state = { mutableState.value },
        strings = { uiStrings },
        updateState = { transform -> mutableState.update(transform) },
    )
    private val conversationTarget = ConversationWriteTarget(
        activeReader = { mutableState.value.conversation },
        activeWriter = { state -> mutableState.update { it.copy(conversation = state) } },
        protectedSessionIds = { runtime.busySessionIds.value + listOfNotNull(mutableState.value.conversationId) },
        onEvict = { sessionId -> sessionDrafts.forget(sessionId) },
    )
    private val conversationCache = ConversationCache(conversationTarget)
    private val deltaBatcher = DeltaBatcher(
        scope = viewModelScope,
        writeTarget = conversationTarget,
        activeSessionId = { mutableState.value.conversationId },
        reduceEvent = { state, event -> reduce(state, event, uiStrings) },
    )
    private val reconcileJobs = mutableMapOf<String, Job>()
    private val retryController: AutoRetryController = AutoRetryController(
        viewModelScope, conversationTarget, runtime, autoRetry,
        canRetry = { message -> uiStrings.canAutoRetry(uiStrings.runErrorKind(message)) },
        awaitSession = { sessionId -> sessionOpener.awaitOpening(sessionId) },
        onBegin = ::retryStarted,
        onActiveError = ::applyRunError,
        onFailure = { sessionId, message -> finishRunError(sessionId, message ?: uiStrings.requestFailed) },
    )
    private val sessionList: SessionListController = SessionListController(
        runtime = runtime, admin = runtime, ioDispatcher = ioDispatcher,
        state = { mutableState.value }, update = { transform -> mutableState.update(transform) },
        strings = { uiStrings }, cacheCurrentSession = ::cacheCurrentSession,
        retainCache = { ids -> conversationCache.retainOnly(ids) },
        target = conversationTarget, applySessionModel = { model -> modelController.applySessionModel(model) },
        isRetrying = retryController::isRetrying, localizeError = { raw -> uiStrings.runError(raw) },
    )
    private val modelController: ModelController = ModelController(
        runtime = runtime, queries = runtime, providers = providers, settings = modelConfigStore,
        ioDispatcher = ioDispatcher, scope = viewModelScope,
        state = { mutableState.value }, update = { transform -> mutableState.update(transform) },
        cacheCurrentSession = ::cacheCurrentSession, userFacingError = { message -> chatError(uiStrings, message) },
        switchModelFailedText = { uiStrings.switchModelFailed },
        switchThinkingFailedText = { uiStrings.switchThinkingFailed },
    )
    private val usageController: UsageController = UsageController(
        runtime = runtime, ioDispatcher = ioDispatcher, target = conversationTarget,
        state = { mutableState.value }, update = { transform -> mutableState.update(transform) },
        cacheCurrentSession = ::cacheCurrentSession, titleFrom = { messages -> sessionList.titleFrom(messages) },
        localizeError = { raw -> uiStrings.runError(raw) },
    )
    private val sessionOpener: SessionOpener = SessionOpener(
        runtime = runtime, ioDispatcher = ioDispatcher, scope = viewModelScope,
        state = { mutableState.value }, update = { transform -> mutableState.update(transform) },
        cachedState = { id -> conversationTarget.readCached(id) }, rememberState = { id, state -> conversationTarget.store(id, state) },
        isActive = { id -> conversationTarget.isActive(id) }, setActive = { id -> conversationTarget.setActive(id) },
        draftFor = { id -> sessionDrafts.restore(id) }, moveDraft = { from, to -> sessionDrafts.reopen(from, to) },
        takeCached = { id -> conversationTarget.take(id) }, applySessionModel = modelController::applySessionModel,
        titleFrom = { messages -> sessionList.titleFrom(messages) }, localizeError = { raw -> uiStrings.runError(raw) },
        localizeTitle = { title -> sessionList.localizedTitle(title) }, decorateNotices = ::decorateHistory,
        cacheCurrentSession = ::cacheCurrentSession, refreshSessions = ::refreshSessions,
        userFacingError = { message -> chatError(uiStrings, message) }, openFailedText = { uiStrings.openConversationFailed },
        newChatFailedText = { uiStrings.newChatFailed }, startupFailedText = { uiStrings.runtimeStartFailed },
        reloadBusyText = { uiStrings.reloadBusy }, sessionNotReadyText = { uiStrings.sessionNotReady },
        reloadFailedText = { uiStrings.reloadFailed },
    )
    private val workspaceController: WorkspaceController = WorkspaceController(
        runtime = runtime, queries = runtime, ioDispatcher = ioDispatcher, scope = viewModelScope,
        state = { mutableState.value }, update = { transform -> mutableState.update(transform) },
        selectWorkspace = sessionOpener::selectWorkspace, refreshSessions = ::refreshSessions,
        userFacingError = { message -> chatError(uiStrings, message) }, createWorkspaceFailedText = { uiStrings.createWorkspaceFailed },
    )
    private val interruptedQuestionNoticeSessions = mutableSetOf<String>()
    private val interruptedRunNoticeSessions = mutableSetOf<String>()

    init {
        viewModelScope.launch { runtime.events.collect(::onSessionEvent) }
        viewModelScope.launch {
            var previousBusy = runtime.busySessionIds.value
            runtime.busySessionIds.collect { ids ->
                mutableState.update { it.copy(busySessionIds = ids) }
                val current = mutableState.value
                val sessionId = current.conversationId
                if (
                    sessionId != null &&
                    sessionId in previousBusy &&
                    sessionId !in ids &&
                    current.sending &&
                    !retryController.isRetrying(sessionId)
                ) {
                    scheduleReconcile(sessionId, BUSY_RECONCILE_DELAY_MS)
                }
                previousBusy = ids
            }
        }
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) {
                    prepareRuntime()
                    runtime.ensureStarted()
                }
                val config = providers.resolveConfig(modelConfigStore.read())
                modelConfigStore.save(config)
                if (mutableState.value.providerId.isBlank()) modelController.applyConfig(config)
                sessionOpener.restoreInitialSession()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(ready = true, conversation = it.conversation.copy(loaded = true), error = chatError(uiStrings, error.message ?: uiStrings.runtimeStartFailed))
                }
            }
        }
    }
    fun updateUiStrings(strings: ChatUiStrings) {
        val languageChanged = uiStrings.newChatTitle != strings.newChatTitle ||
            uiStrings.thinking != strings.thinking
        uiStrings = strings
        if (languageChanged) {
            viewModelScope.launch { refreshSessions() }
        }
    }
    fun requestSessionsRefresh() {
        viewModelScope.launch { refreshSessions() }
    }
    fun onInputChange(value: String) = sessionDrafts.updateInput(value)
    fun refreshAvailableSkills() {
        val cwd = mutableState.value.conversationCwd
        if (cwd.isBlank()) {
            mutableAvailableSkills.value = emptyList()
            return
        }
        viewModelScope.launch {
            val skills = withContext(ioDispatcher) {
                runCatching { skillExpander?.list(cwd) }.getOrNull()
            }.orEmpty()
            if (mutableState.value.conversationCwd == cwd) mutableAvailableSkills.value = skills
        }
    }

    fun quote(messageId: Long, text: String) = sessionDrafts.setQuote(messageId, text)

    fun clearQuote() = sessionDrafts.clearQuote()

    fun attach(uri: Uri) = sessionDrafts.attach(uri)

    internal fun launchAttachmentImport(
        sessionId: String,
        root: File,
        load: suspend () -> AttachmentImport,
    ) = sessionDrafts.importAttachment(sessionId, root, load)

    fun removeAttachment(id: String) = sessionDrafts.removeAttachment(id)

    fun send() {
        deltaBatcher.flush()
        val current = mutableState.value
        if (!current.ready || !current.historyLoaded) {
            mutableState.update { it.copy(error = uiStrings.restoringSession) }
            return
        }
        if (current.conversationId == null) {
            mutableState.update { it.copy(error = uiStrings.sessionNotReady) }
            return
        }
        if (current.switchingConversation || current.importingAttachment) return
        val text = current.input.trim()
        if (text.isEmpty()) return
        if (current.sending) {
            if (text.startsWith("/")) {
                mutableState.update { it.copy(error = uiStrings.stopBeforeCommand) }
            } else {
                steerNow()
            }
            return
        }
        when (val parsed = parseChatCommand(text)) {
            is ParsedCommand.Skill -> {
                expandAndSend(parsed.name, parsed.args)
                return
            }

            else -> Unit
        }
        val mentions = extractSkillMentions(text)
        if (mentions != null) {
            expandInlineAndSend(mentions)
            return
        }
        startTurn(
            prompt = sessionDrafts.buildPrompt(current.quote, current.attachments, text),
            imageRefs = sessionDrafts.imageRefs(current.attachments),
        )
    }

    fun steerNow() {
        submitQueued(InputQueueKind.STEER)
    }

    fun followUpNow() {
        submitQueued(InputQueueKind.FOLLOW_UP)
    }

    fun clearQueuedInputs() {
        val sessionId = mutableState.value.conversationId ?: return
        val dropped = runCatching { runtime.clearQueuedInputs(sessionId) }.getOrDefault(emptyList())
        mutableState.update { state ->
            state.copy(
                input = sessionDrafts.mergeDroppedInput(state.input, dropped),
                queuedInputs = emptyList(),
            )
        }
    }

    private fun submitQueued(kind: InputQueueKind) {
        val current = mutableState.value
        if (!current.ready || !current.historyLoaded) {
            mutableState.update { it.copy(error = uiStrings.restoringSession) }
            return
        }
        val sessionId = current.conversationId ?: return
        if (current.switchingConversation || current.importingAttachment) return
        if (!current.sending) {
            send()
            return
        }
        val text = current.input.trim()
        if (text.isEmpty()) return
        if (text.startsWith("/")) {
            mutableState.update { it.copy(error = uiStrings.stopBeforeCommand) }
            return
        }
        val root = sessionDrafts.attachmentRoot(current)
        val imageRefs = sessionDrafts.imageRefs(current.attachments)
        if (imageRefs.isNotEmpty() && root == null) {
            mutableState.update { it.copy(error = uiStrings.preparingSession) }
            return
        }
        val prompt = sessionDrafts.buildPrompt(current.quote, current.attachments, text)
        mutableState.update {
            it.copy(input = "", quote = null, attachments = emptyList(), error = null)
        }
        cacheCurrentSession()
        viewModelScope.launch {
            val images = sessionDrafts.normalizeImages(sessionId, imageRefs, root)
            try {
                sessionOpener.awaitOpening(sessionId)
                when (kind) {
                    InputQueueKind.STEER -> runtime.steer(sessionId, prompt, images)
                    InputQueueKind.FOLLOW_UP -> runtime.followUp(sessionId, prompt, images)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(error = chatError(uiStrings, error.message ?: uiStrings.requestFailed))
                }
            }
        }
    }

    fun retry(messageId: Long) {
        deltaBatcher.flush()
        retryController.manualRetry(mutableState.value, messageId)
    }

    private fun retryStarted(sessionId: String) {
        if (!conversationTarget.isActive(sessionId)) return
        mutableState.update { it.copy(error = null) }
        cacheCurrentSession()
    }

    private fun applyRunError(reduced: ConversationState, error: String?) {
        mutableState.update { it.copy(conversation = reduced, error = error ?: it.error) }
        cacheCurrentSession()
    }

    private fun dismissInterruptedNotice(sessionId: String) {
        interruptedQuestionNoticeSessions -= sessionId
        interruptedRunNoticeSessions -= sessionId
        conversationTarget.updateIfPresent(sessionId) { state ->
            state.copy(messages = state.messages.filterNot { message -> message.notice.isInterruptedNotice() })
        }
        viewModelScope.launch {
            ignoreFailure { dismissInterruptedQuestions(sessionId) }
            ignoreFailure { dismissInterruptedRun(sessionId) }
        }
    }

    private suspend fun ignoreFailure(block: suspend () -> Unit) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Unit
        }
    }

    private fun startTurn(
        prompt: String,
        titleSeed: String = prompt,
        imageRefs: List<AttachmentRef> = emptyList(),
    ) {
        val current = mutableState.value
        val sessionId = current.conversationId ?: return
        val root = sessionDrafts.attachmentRoot(current)
        if (imageRefs.isNotEmpty() && root == null) {
            mutableState.update { it.copy(error = uiStrings.preparingSession) }
            return
        }
        retryController.clear(sessionId)
        dismissInterruptedNotice(sessionId)
        val nextId = (current.messages.maxOfOrNull { it.id } ?: -1L) + 1
        val assistantId = nextId + 1
        val now = System.currentTimeMillis()
        mutableState.update {
            it.copy(
                conversation = it.conversation.copy(
                    messages = it.messages + AssistantMessage(nextId, AssistantRole.USER, prompt) + AssistantMessage(
                        id = assistantId,
                        role = AssistantRole.ASSISTANT,
                        text = "",
                        streaming = true,
                        status = MessageStatus.RUNNING,
                        startedAt = now,
                    ),
                    sending = true,
                    activity = RuntimeActivity.Thinking,
                    activityStartedAt = now,
                    title = if (sessionList.isDefaultChatTitle(it.conversationTitle)) {
                        sessionList.titleFrom(titleSeed)
                    } else {
                        it.conversationTitle
                    },
                ),
                input = "",
                quote = null,
                attachments = emptyList(),
                error = null,
            )
        }
        cacheCurrentSession()
        viewModelScope.launch {
            val images = sessionDrafts.normalizeImages(sessionId, imageRefs, root)
            try {
                sessionOpener.awaitOpening(sessionId)
                runtime.send(sessionId, prompt, images)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                finishRunError(sessionId, error.message ?: uiStrings.requestFailed)
            }
        }
    }

    private fun expandAndSend(name: String, args: String) {
        val current = mutableState.value
        val sessionId = current.conversationId ?: return
        mutableState.update { it.copy(conversation = it.conversation.copy(sending = true), error = null) }
        viewModelScope.launch {
            val expansion = withContext(ioDispatcher) {
                skillExpander?.expand(current.conversationCwd, name, args)
            }
            if (mutableState.value.conversationId != sessionId) return@launch
            if (expansion == null) {
                mutableState.update { it.copy(conversation = it.conversation.copy(sending = false), error = uiStrings.skillMissing(name)) }
                return@launch
            }
            startTurn(
                prompt = sessionDrafts.buildPrompt(current.quote, current.attachments, expansion.prompt),
                titleSeed = args.trim().ifBlank { expansion.name },
                imageRefs = sessionDrafts.imageRefs(current.attachments),
            )
        }
    }

    private fun expandInlineAndSend(mentions: SkillMentions) {
        val current = mutableState.value
        val sessionId = current.conversationId ?: return
        mutableState.update { it.copy(conversation = it.conversation.copy(sending = true), error = null) }
        viewModelScope.launch {
            val expansions = withContext(ioDispatcher) {
                mentions.names.map { name -> skillExpander?.expand(current.conversationCwd, name, "") }
            }
            if (mutableState.value.conversationId != sessionId) return@launch
            val missing = mentions.names.getOrNull(expansions.indexOfFirst { it == null })
            if (missing != null) {
                mutableState.update { it.copy(conversation = it.conversation.copy(sending = false), error = uiStrings.skillMissing(missing)) }
                return@launch
            }
            val prompt = buildString {
                expansions.forEachIndexed { index, expansion ->
                    if (index > 0) append("\n\n")
                    append(expansion?.prompt.orEmpty())
                }
                val body = mentions.text.trim()
                if (body.isNotEmpty()) {
                    if (isNotEmpty()) append("\n\n")
                    append(body)
                }
            }
            startTurn(
                prompt = sessionDrafts.buildPrompt(current.quote, current.attachments, prompt),
                titleSeed = mentions.text.trim().ifBlank { expansions.firstOrNull()?.name.orEmpty() },
                imageRefs = sessionDrafts.imageRefs(current.attachments),
            )
        }
    }

    fun stop() {
        deltaBatcher.flush()
        val sessionId = mutableState.value.conversationId ?: return
        retryController.clear(sessionId)
        val dropped = runtime.stop(sessionId)
        mutableState.update { state ->
            state.copy(
                input = sessionDrafts.mergeDroppedInput(state.input, dropped),
                queuedInputs = emptyList(),
            )
        }
        if (runtime.activeRuns.value[sessionId] == null) {
            scheduleReconcile(sessionId, STOP_RECONCILE_DELAY_MS)
        }
    }

    fun onAppForeground() {
        val current = mutableState.value
        if (!current.sending) return
        val sessionId = current.conversationId ?: return
        scheduleReconcile(sessionId, 0L)
    }

    fun updateScrollAnchor(sessionId: String, index: Int, offset: Int, atBottom: Boolean = false) =
        sessionDrafts.updateAnchor(sessionId, index, offset, atBottom)

    fun scrollAnchorFor(sessionId: String): ScrollAnchor? = sessionDrafts.anchor(sessionId)

    fun onTrimMemory(level: Int) = conversationCache.onTrimMemory(level)

    suspend fun reloadSession(): Result<Unit> = sessionOpener.reloadSession()

    fun newSession(workspaceId: String, initialInput: String = "") =
        sessionOpener.newSession(workspaceId, initialInput)

    fun openConversationById(id: String) = sessionOpener.openConversationById(id)

    fun selectWorkspace(workspaceId: String) = sessionOpener.selectWorkspace(workspaceId)

    fun createWorkspace(name: String) = workspaceController.create(name)

    fun renameWorkspace(workspaceId: String, name: String) = workspaceController.rename(workspaceId, name)

    fun deleteWorkspace(workspaceId: String) = workspaceController.delete(workspaceId)

    suspend fun loadWorkspaceSessionCount(workspaceId: String): Int = sessionList.sessionCount(workspaceId)

    suspend fun loadSearchSessions(): List<SessionSearchHit> = sessionList.search()

    fun openConversation(id: String) = sessionOpener.openConversation(id)

    fun setModel(modelId: String) = modelController.setModel(modelId)

    fun setProvider(providerId: String) = modelController.setProvider(providerId)

    fun setThinkingLevel(level: ThinkingLevel) = modelController.setThinkingLevel(level)

    fun compactContext() {
        deltaBatcher.flush()
        val current = mutableState.value
        if (!current.ready || !current.historyLoaded) return
        val sessionId = current.conversationId ?: return
        if (current.sending || current.switchingConversation || current.compacting) return
        mutableState.update { it.copy(compacting = true, error = null) }
        viewModelScope.launch {
            val outcome = try {
                runtime.compact(sessionId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                CompactionOutcome.Failed(error.message)
            }
            mutableState.update { it.copy(compacting = false) }
            when (outcome) {
                is CompactionOutcome.Done -> {
                    reloadHistoryAfterCompaction(sessionId)
                    refreshUsage(sessionId)
                }

                CompactionOutcome.Empty -> showCommandError(uiStrings.nothingToCompact, sessionId)
                CompactionOutcome.Busy -> showCommandError(uiStrings.compactionBusy, sessionId)
                CompactionOutcome.Unavailable -> showCommandError(uiStrings.compactionUnavailable, sessionId)
                is CompactionOutcome.Failed -> showCommandError(uiStrings.runError(outcome.message), sessionId)
            }
        }
    }

    fun showCommandError(message: String) {
        mutableState.update { it.copy(error = message) }
    }

    private fun showCommandError(message: String, sessionId: String) {
        if (mutableState.value.conversationId == sessionId) {
            mutableState.update { it.copy(error = message) }
        }
    }

    fun toggleStep(messageId: Long, stepId: Long) {
        mutableState.update { state ->
            state.copy(
                conversation = state.conversation.copy(
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
                ),
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
            if (wasActive) deltaBatcher.clear(id)
            conversationCache.evict(id)
            if (wasActive) runtime.stop(id)
            runCatching { withContext(ioDispatcher) { runtime.deleteSession(id) } }
            if (wasActive) {
                val workspaceId = mutableState.value.currentWorkspaceId
                val sessions = withContext(ioDispatcher) { runtime.sessions(workspaceId) }
                val next = sessions.firstOrNull()
                if (next != null) sessionOpener.switchToSummary(next) else sessionOpener.createSession(workspaceId)
            } else {
                refreshSessions()
            }
        }
    }

    private fun scheduleReconcile(sessionId: String, delayMs: Long) {
        if (reconcileJobs[sessionId]?.isActive == true) return
        reconcileJobs[sessionId] = viewModelScope.launch {
            try {
                if (delayMs > 0) delay(delayMs)
                sessionList.reconcile(sessionId)
            } finally {
                reconcileJobs.remove(sessionId)
            }
        }
    }

    private suspend fun decorateHistory(state: ConversationState, sessionId: String): ConversationState =
        state.withCachedInterruptedNotices(sessionId)
            .withInterruptedNotice(sessionId, interruptedQuestionNoticeSessions) {
                interruptedQuestions(sessionId).size.takeIf { count -> count > 0 }
                    ?.let { count -> TurnNotice.InterruptedQuestions(count) }
            }
            .withInterruptedNotice(sessionId, interruptedRunNoticeSessions) {
                interruptedRuns(sessionId).size.takeIf { count -> count > 0 }
                    ?.let { count -> TurnNotice.InterruptedRun(count) }
            }

    private fun ConversationState.withCachedInterruptedNotices(sessionId: String): ConversationState {
        if (sessionId !in interruptedQuestionNoticeSessions && sessionId !in interruptedRunNoticeSessions) return this
        val cached = conversationTarget.readCached(sessionId) ?: return this
        val notices = cached.messages.filter { message -> message.notice.isInterruptedNotice() }
        if (notices.isEmpty()) return this
        var id = nextMessageId(messages)
        return copy(messages = messages + notices.map { message -> message.copy(id = id++) })
    }

    private suspend fun ConversationState.withInterruptedNotice(
        sessionId: String,
        shownSessions: MutableSet<String>,
        resolve: suspend () -> TurnNotice?,
    ): ConversationState {
        if (sessionId in shownSessions) return this
        val notice = ignoreFailureResult(resolve) ?: return this
        shownSessions += sessionId
        val now = System.currentTimeMillis()
        return copy(
            messages = messages + AssistantMessage(
                id = nextMessageId(messages),
                role = AssistantRole.ASSISTANT,
                text = "",
                status = MessageStatus.DONE,
                startedAt = now,
                finishedAt = now,
                notice = notice,
            ),
        )
    }

    private suspend fun <T> ignoreFailureResult(block: suspend () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        null
    }

    private fun TurnNotice?.isInterruptedNotice(): Boolean =
        this is TurnNotice.InterruptedQuestions || this is TurnNotice.InterruptedRun

    private fun cacheCurrentSession() {
        val current = mutableState.value
        val id = conversationCache.rememberCurrent(current) { deltaBatcher.flush() } ?: return
        sessionDrafts.remember(id, current)
    }

    private suspend fun refreshUsage(sessionId: String) = usageController.refresh(sessionId)

    private suspend fun reloadHistoryAfterCompaction(sessionId: String) =
        usageController.reloadHistoryAfterCompaction(sessionId)

    private fun onSessionEvent(scoped: SessionRuntimeEvent) {
        val event = scoped.event
        if (deltaBatcher.accept(scoped.sessionId, event)) return
        deltaBatcher.flush()
        val isActive = scoped.sessionId == mutableState.value.conversationId
        when (event) {
            is RuntimeEvent.QueueChanged -> {
                if (isActive) {
                    mutableState.update { it.copy(queuedInputs = queueInputs(event.steering, event.followUp)) }
                }
                return
            }

            is RuntimeEvent.QueuedInputsDropped -> {
                if (isActive) {
                    mutableState.update {
                        it.copy(input = sessionDrafts.mergeDroppedInput(it.input, event.texts), queuedInputs = emptyList())
                    }
                }
                return
            }

            is RuntimeEvent.SessionListChanged, is RuntimeEvent.SessionRemoved, is RuntimeEvent.SessionArchived -> {
                viewModelScope.launch { refreshSessions() }
                return
            }

            else -> Unit
        }
        if (event is RuntimeEvent.ActivityChanged && retryController.onActivityChanged(event.runId)) return
        if (isActive) {
            conversationTarget.update(scoped.sessionId) { reduce(it, event, uiStrings) }
            if (event is RuntimeEvent.RunFailed) {
                val failed = mutableState.value.messages.none { it.status == MessageStatus.FAILED }
                mutableState.update { it.copy(error = if (failed) uiStrings.runError(event.message) else null) }
            }
            cacheCurrentSession()
        } else {
            conversationTarget.update(scoped.sessionId) { cached -> reduce(cached, event, uiStrings) }
        }
        retryController.onRetryEvent(scoped.sessionId, event)
    }

    private fun finishRunError(sessionId: String, message: String) {
        retryController.finishRunError(sessionId, message, uiStrings)
    }

    private suspend fun refreshSessions() = sessionList.refresh()

    private fun applySessionModel(model: SessionModelSnapshot?) = modelController.applySessionModel(model)

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

    private fun queueInputs(steering: List<String>, followUp: List<String>): List<QueuedInput> =
        steering.map { QueuedInput(InputQueueKind.STEER, it) } +
            followUp.map { QueuedInput(InputQueueKind.FOLLOW_UP, it) }

    private companion object {
        const val LEGACY_NEW_TITLE = "新对话"
    }
}
