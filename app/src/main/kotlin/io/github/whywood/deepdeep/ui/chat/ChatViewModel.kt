package io.github.whywood.deepdeep.ui.chat

import io.github.whywood.deepdeep.ui.common.chat.SkillMentions

import io.github.whywood.deepdeep.ui.common.chat.extractSkillMentions

import io.github.whywood.deepdeep.ui.common.chat.ParsedCommand

import io.github.whywood.deepdeep.ui.common.chat.parseChatCommand

import android.content.ComponentCallbacks2
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.whywood.deepdeep.conversation.ConversationState
import io.github.whywood.deepdeep.conversation.ConversationStore
import io.github.whywood.deepdeep.conversation.ConversationStrings
import io.github.whywood.deepdeep.data.AttachmentImport
import io.github.whywood.deepdeep.data.AttachmentRef
import io.github.whywood.deepdeep.data.AttachmentStore
import io.github.whywood.deepdeep.data.ImageNormalizer
import io.github.whywood.deepdeep.data.NormalizedImage
import io.github.whywood.deepdeep.data.SendWhileRunning
import io.github.whywood.deepdeep.runtime.AgentRuntime
import io.github.whywood.deepdeep.runtime.CompactionOutcome
import io.github.whywood.deepdeep.runtime.InputQueueKind
import io.github.whywood.deepdeep.runtime.QueuedInput
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.runtime.ModelConfig
import io.github.whywood.deepdeep.runtime.ModelSettings
import io.github.whywood.deepdeep.runtime.ProviderDirectory
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import io.github.whywood.deepdeep.runtime.SessionModelSnapshot
import io.github.whywood.deepdeep.runtime.SkillExpander
import io.github.whywood.deepdeep.runtime.SkillSummary
import io.github.whywood.deepdeep.runtime.ThinkingLevel
import io.github.whywood.deepdeep.runtime.TurnNotice
import io.github.whywood.deepdeep.ui.chat.state.AutoRetryController
import io.github.whywood.deepdeep.ui.chat.state.ModelController
import io.github.whywood.deepdeep.ui.chat.state.SessionListController
import io.github.whywood.deepdeep.ui.chat.state.SessionOpener
import io.github.whywood.deepdeep.ui.chat.state.WorkspaceController
import io.github.whywood.deepdeep.ui.chat.state.chatError
import io.github.whywood.deepdeep.ui.common.chat.ChatUiState
import io.github.whywood.deepdeep.ui.common.chat.ScrollAnchor
import io.github.whywood.deepdeep.ui.common.chat.state.SessionDraftStore
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatViewModel(
    private val runtime: AgentRuntime,
    private val store: ConversationStore,
    private val providers: ProviderDirectory,
    private val modelConfigStore: ModelSettings,
    private val attachmentStore: AttachmentStore,
    private val prepareRuntime: suspend () -> Unit,
    private val autoRetry: Flow<Boolean> = flowOf(false),
    @Volatile private var uiStrings: ConversationStrings = ConversationStrings.fallback(),
    private val skillExpander: SkillExpander? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val dismissInterruptedQuestions: suspend (String) -> Unit = {},
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
    private val retryController: AutoRetryController = AutoRetryController(
        scope = viewModelScope,
        store = store,
        isActive = { sessionId -> mutableState.value.conversationId == sessionId },
        runtime = runtime,
        enabledFlow = autoRetry,
        canRetry = { message -> uiStrings.canAutoRetry(uiStrings.runErrorKind(message)) },
        awaitSession = { sessionId -> sessionOpener.awaitOpening(sessionId) },
        onBegin = ::retryStarted,
        onActiveError = ::applyRunError,
        onFailure = { sessionId, message -> finishRunError(sessionId, message) },
    )
    private val sessionList: SessionListController = SessionListController(
        runtime = runtime,
        admin = runtime,
        ioDispatcher = ioDispatcher,
        state = { mutableState.value },
        update = { transform -> mutableState.update(transform) },
        store = store,
        strings = { uiStrings },
    )
    private val modelController: ModelController = ModelController(
        runtime = runtime,
        queries = runtime,
        providers = providers,
        settings = modelConfigStore,
        ioDispatcher = ioDispatcher,
        scope = viewModelScope,
        state = { mutableState.value },
        update = { transform -> mutableState.update(transform) },
        onModelChanged = { sessionId, model ->
            store.updateIfPresent(sessionId) { state -> state.copy(sessionModel = model) }
        },
        userFacingError = { message -> chatError(uiStrings, message) },
        switchModelFailedText = { uiStrings.switchModelFailed },
        switchThinkingFailedText = { uiStrings.switchThinkingFailed },
    )
    private val sessionOpener: SessionOpener = SessionOpener(
        runtime = runtime,
        ioDispatcher = ioDispatcher,
        scope = viewModelScope,
        state = { mutableState.value },
        update = { transform -> mutableState.update(transform) },
        store = store,
        draftFor = { sessionId -> sessionDrafts.restore(sessionId) },
        moveDraft = { from, to -> sessionDrafts.reopen(from, to) },
        applySessionModel = modelController::applySessionModel,
        localizeTitle = { title -> sessionList.localizedTitle(title) },
        refreshSessions = ::refreshSessions,
        userFacingError = { message -> chatError(uiStrings, message) },
        openFailedText = { uiStrings.openConversationFailed },
        newChatFailedText = { uiStrings.newChatFailed },
        startupFailedText = { uiStrings.runtimeStartFailed },
        reloadBusyText = { uiStrings.reloadBusy },
        sessionNotReadyText = { uiStrings.sessionNotReady },
        reloadFailedText = { uiStrings.reloadFailed },
    )
    private val workspaceController: WorkspaceController = WorkspaceController(
        runtime = runtime,
        queries = runtime,
        ioDispatcher = ioDispatcher,
        scope = viewModelScope,
        state = { mutableState.value },
        update = { transform -> mutableState.update(transform) },
        selectWorkspace = sessionOpener::selectWorkspace,
        refreshSessions = ::refreshSessions,
        userFacingError = { message -> chatError(uiStrings, message) },
        createWorkspaceFailedText = { uiStrings.createWorkspaceFailed },
    )
    private var observationJob: Job? = null
    private var observedSessionId: String? = null
    private var expanding = false
    private val busyPinned = mutableSetOf<String>()

    init {
        store.setRetryGate(retryController::isRetrying)
        store.onEvict = sessionDrafts::forget
        viewModelScope.launch {
            mutableState.map { it.conversationId }.distinctUntilChanged().collect(::observeConversation)
        }
        viewModelScope.launch {
            runtime.busySessionIds.collect { ids ->
                (ids - busyPinned).forEach(store::pin)
                busyPinned.clear()
                busyPinned += ids
                mutableState.update { it.copy(busySessionIds = ids) }
            }
        }
        viewModelScope.launch {
            store.droppedInputs.collect { dropped ->
                if (dropped.sessionId == mutableState.value.conversationId) {
                    mutableState.update { state ->
                        state.copy(input = sessionDrafts.mergeDroppedInput(state.input, dropped.texts))
                    }
                }
            }
        }
        viewModelScope.launch {
            store.sessionChanges.collect { refreshSessions() }
        }
        viewModelScope.launch {
            store.runFailures.collect { failure ->
                if (failure.sessionId == mutableState.value.conversationId) {
                    val failed = mutableState.value.messages.none { message -> message.status == MessageStatus.FAILED }
                    if (failed) mutableState.update { it.copy(error = uiStrings.runError(failure.message)) }
                }
            }
        }
        viewModelScope.launch {
            store.failures.collect { failure ->
                if (failure.sessionId == mutableState.value.conversationId) {
                    mutableState.update {
                        it.copy(error = chatError(uiStrings, failure.message.ifBlank { uiStrings.requestFailed }))
                    }
                }
            }
        }
        viewModelScope.launch {
            store.lifecycle.collect { scoped ->
                val event = scoped.event
                if (event is RuntimeEvent.ActivityChanged && retryController.onActivityChanged(event.runId)) return@collect
                retryController.onRetryEvent(scoped.sessionId, event)
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
                    it.copy(
                        ready = true,
                        conversation = it.conversation.copy(loaded = true),
                        error = chatError(uiStrings, error.message ?: uiStrings.runtimeStartFailed),
                    )
                }
            }
        }
    }

    fun updateUiStrings(strings: ConversationStrings) {
        val languageChanged = uiStrings.newChatTitle != strings.newChatTitle ||
            uiStrings.thinking != strings.thinking
        uiStrings = strings
        store.updateStrings(strings)
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

    fun send(sendWhileRunning: SendWhileRunning = SendWhileRunning.STEER) {
        val current = mutableState.value
        if (expanding) return
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
            } else if (sendWhileRunning == SendWhileRunning.QUEUE) {
                submitQueued(InputQueueKind.FOLLOW_UP)
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

    fun removeQueuedInput(input: QueuedInput) {
        val sessionId = mutableState.value.conversationId ?: return
        runCatching { store.removeQueuedInput(sessionId, input.kind, input.text) }
    }

    fun sendQueuedNow(input: QueuedInput) {
        val current = mutableState.value
        val sessionId = current.conversationId ?: return
        if (!runCatching { store.removeQueuedInput(sessionId, input.kind, input.text) }.getOrDefault(false)) return
        store.steer(sessionId, input.text)
    }

    fun clearQueuedInputs() {
        val sessionId = mutableState.value.conversationId ?: return
        val dropped = runCatching { store.clearQueuedInputs(sessionId) }.getOrDefault(emptyList())
        mutableState.update { state ->
            state.copy(input = sessionDrafts.mergeDroppedInput(state.input, dropped))
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
        viewModelScope.launch {
            try {
                val images = sessionDrafts.normalizeImages(sessionId, imageRefs, root)
                sessionOpener.awaitOpening(sessionId)
                when (kind) {
                    InputQueueKind.STEER -> store.steer(sessionId, prompt, images)
                    InputQueueKind.FOLLOW_UP -> store.followUp(sessionId, prompt, images)
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
        retryController.manualRetry(mutableState.value, messageId)
    }

    private fun retryStarted(sessionId: String) {
        if (mutableState.value.conversationId != sessionId) return
        mutableState.update { it.copy(error = null) }
    }

    private fun applyRunError(sessionId: String, reduced: ConversationState, error: String?) {
        if (mutableState.value.conversationId != sessionId) return
        mutableState.update { it.copy(conversation = reduced, error = error ?: it.error) }
    }

    private fun dismissInterruptedNotice(sessionId: String) {
        store.updateIfPresent(sessionId) { state ->
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
        val title = if (sessionList.isDefaultChatTitle(current.conversationTitle)) {
            sessionList.titleFrom(titleSeed)
        } else {
            current.conversationTitle
        }
        val sent = store.send(
            sessionId = sessionId,
            prompt = prompt,
            title = title,
            prepareImages = { sessionDrafts.normalizeImages(sessionId, imageRefs, root) },
        )
        if (!sent) return
        mutableState.update {
            it.copy(
                conversation = store.peek(sessionId) ?: it.conversation,
                input = "",
                quote = null,
                attachments = emptyList(),
                error = null,
            )
        }
    }

    private fun expandAndSend(name: String, args: String) {
        val current = mutableState.value
        val sessionId = current.conversationId ?: return
        expanding = true
        mutableState.update { it.copy(conversation = it.conversation.copy(sending = true), error = null) }
        viewModelScope.launch {
            try {
                val expansion = withContext(ioDispatcher) {
                    skillExpander?.expand(current.conversationCwd, name, args)
                }
                if (mutableState.value.conversationId != sessionId) return@launch
                if (expansion == null) {
                    mutableState.update {
                        it.copy(conversation = it.conversation.copy(sending = false), error = uiStrings.skillMissing(name))
                    }
                    return@launch
                }
                startTurn(
                    prompt = sessionDrafts.buildPrompt(current.quote, current.attachments, expansion.prompt),
                    titleSeed = args.trim().ifBlank { expansion.name },
                    imageRefs = sessionDrafts.imageRefs(current.attachments),
                )
            } finally {
                expanding = false
            }
        }
    }

    private fun expandInlineAndSend(mentions: SkillMentions) {
        val current = mutableState.value
        val sessionId = current.conversationId ?: return
        expanding = true
        mutableState.update { it.copy(conversation = it.conversation.copy(sending = true), error = null) }
        viewModelScope.launch {
            try {
                val expansions = withContext(ioDispatcher) {
                    mentions.names.map { name -> skillExpander?.expand(current.conversationCwd, name, "") }
                }
                if (mutableState.value.conversationId != sessionId) return@launch
                val missing = mentions.names.getOrNull(expansions.indexOfFirst { it == null })
                if (missing != null) {
                    mutableState.update {
                        it.copy(conversation = it.conversation.copy(sending = false), error = uiStrings.skillMissing(missing))
                    }
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
            } finally {
                expanding = false
            }
        }
    }

    fun stop() {
        val sessionId = mutableState.value.conversationId ?: return
        retryController.clear(sessionId)
        val dropped = store.stop(sessionId)
        mutableState.update { state ->
            state.copy(input = sessionDrafts.mergeDroppedInput(state.input, dropped))
        }
    }

    fun onAppForeground() {
        store.onAppForeground()
    }

    fun updateScrollAnchor(sessionId: String, index: Int, offset: Int, atBottom: Boolean = false) =
        sessionDrafts.updateAnchor(sessionId, index, offset, atBottom)

    fun scrollAnchorFor(sessionId: String): ScrollAnchor? = sessionDrafts.anchor(sessionId)

    fun onTrimMemory(level: Int) {
        if (level != ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW &&
            level != ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
        ) {
            return
        }
        val keep = setOfNotNull(mutableState.value.conversationId) + mutableState.value.busySessionIds
        store.evictUnpinned(keep)
        sessionDrafts.retainOnly(keep)
    }

    suspend fun reloadSession(): Result<Unit> = sessionOpener.reloadSession()

    fun newSession(workspaceId: String, initialInput: String = "") {
        rememberDraft()
        sessionOpener.newSession(workspaceId, initialInput)
    }

    fun openConversationById(id: String) {
        rememberDraft()
        sessionOpener.openConversationById(id)
    }

    fun selectWorkspace(workspaceId: String) {
        rememberDraft()
        sessionOpener.selectWorkspace(workspaceId)
    }

    fun createWorkspace(name: String) = workspaceController.create(name)

    fun renameWorkspace(workspaceId: String, name: String) = workspaceController.rename(workspaceId, name)

    fun deleteWorkspace(workspaceId: String) = workspaceController.delete(workspaceId)

    suspend fun loadWorkspaceSessionCount(workspaceId: String): Int = sessionList.sessionCount(workspaceId)

    suspend fun loadSearchSessions(): List<SessionSearchHit> = sessionList.search()

    fun openConversation(id: String) {
        rememberDraft()
        sessionOpener.openConversation(id)
    }

    fun setModel(modelId: String) = modelController.setModel(modelId)

    fun setProvider(providerId: String) = modelController.setProvider(providerId)

    fun setThinkingLevel(level: ThinkingLevel) = modelController.setThinkingLevel(level)

    fun adoptConfiguredModel(config: ModelConfig) = modelController.adoptConfiguredModel(config)

    fun compactContext() {
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
                    try {
                        store.reopen(sessionId, store.peek(sessionId)?.title)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        if (mutableState.value.conversationId == sessionId) {
                            mutableState.update { it.copy(error = chatError(uiStrings, error.message ?: uiStrings.compactionFailed)) }
                        }
                    }
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
        val sessionId = mutableState.value.conversationId ?: return
        store.toggleStep(sessionId, messageId, stepId)
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
                store.stop(id)
                store.evict(id)
            }
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

    override fun onCleared() {
        observationJob?.cancel()
        observedSessionId?.takeIf { it !in busyPinned }?.let(store::unpin)
        observedSessionId = null
        super.onCleared()
    }

    private fun observeConversation(sessionId: String?) {
        if (sessionId == observedSessionId) return
        val previous = observedSessionId
        observedSessionId = sessionId
        observationJob?.cancel()
        observationJob = null
        if (sessionId == null) {
            previous?.takeIf { it !in busyPinned }?.let(store::unpin)
            mutableState.update { it.copy(conversation = ConversationState()) }
            return
        }
        store.pin(sessionId)
        previous?.takeIf { it != sessionId && it !in busyPinned }?.let(store::unpin)
        observationJob = viewModelScope.launch {
            store.observe(sessionId).collect { conversation ->
                mutableState.update { state ->
                    if (state.conversationId != sessionId) {
                        state
                    } else {
                        state.copy(
                            conversation = conversation,
                            switchingConversation = if (conversation.loaded) false else state.switchingConversation,
                            importingAttachment = if (conversation.loaded) false else state.importingAttachment,
                        )
                    }
                }
            }
        }
    }

    private fun rememberDraft() {
        val current = mutableState.value
        val sessionId = current.conversationId ?: return
        sessionDrafts.remember(sessionId, current)
    }

    private fun TurnNotice?.isInterruptedNotice(): Boolean =
        this is TurnNotice.InterruptedQuestions || this is TurnNotice.InterruptedRun

    private fun finishRunError(sessionId: String, message: String?) {
        retryController.finishRunError(sessionId, message, uiStrings)
    }

    private suspend fun refreshSessions() = sessionList.refresh()

    private fun applySessionModel(model: SessionModelSnapshot?) = modelController.applySessionModel(model)
}
