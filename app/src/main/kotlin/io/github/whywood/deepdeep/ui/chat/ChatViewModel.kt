package io.github.whywood.deepdeep.ui.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.whywood.deepdeep.data.AttachmentImport
import io.github.whywood.deepdeep.data.AttachmentKind
import io.github.whywood.deepdeep.data.AttachmentRef
import io.github.whywood.deepdeep.data.AttachmentStore
import io.github.whywood.deepdeep.data.ImageNormalizer
import io.github.whywood.deepdeep.data.NormalizedImage
import io.github.whywood.deepdeep.data.encodeAttachmentBlock
import io.github.whywood.deepdeep.runtime.AgentRuntime
import io.github.whywood.deepdeep.runtime.AgentRuntimeHost
import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.AssistantStep
import io.github.whywood.deepdeep.runtime.CompactionOutcome
import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.runtime.InterruptedQuestion
import io.github.whywood.deepdeep.runtime.ImagePart
import io.github.whywood.deepdeep.runtime.InputQueueKind
import io.github.whywood.deepdeep.runtime.InterruptedRun
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.runtime.ModelConfig
import io.github.whywood.deepdeep.runtime.ModelSettings
import io.github.whywood.deepdeep.runtime.ProviderDirectory
import io.github.whywood.deepdeep.runtime.QueuedInput
import io.github.whywood.deepdeep.runtime.RunStatus
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import io.github.whywood.deepdeep.runtime.SessionModelSnapshot
import io.github.whywood.deepdeep.runtime.SessionRuntimeEvent
import io.github.whywood.deepdeep.runtime.SkillExpander
import io.github.whywood.deepdeep.runtime.SkillSummary
import io.github.whywood.deepdeep.runtime.ThinkingLevel
import io.github.whywood.deepdeep.runtime.TurnNotice
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

private const val STREAM_DELTA_BATCH_MS = 16L
private const val BUSY_RECONCILE_DELAY_MS = 500L
private const val STOP_RECONCILE_DELAY_MS = 500L

class ChatViewModel(
    private val runtime: AgentRuntime,
    private val providers: ProviderDirectory,
    private val modelConfigStore: ModelSettings,
    private val attachmentStore: AttachmentStore,
    private val prepareRuntime: suspend () -> Unit,
    private val autoRetry: Flow<Boolean> = flowOf(false),
    private val uiStrings: ChatUiStrings = ChatUiStrings.fallback(),
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

    private val conversationCache = mutableMapOf<String, ConversationState>()
    private val sessionOpenJobs = mutableMapOf<String, Job>()
    private val reconcileJobs = mutableMapOf<String, Job>()
    private val pendingDeltas = mutableListOf<RuntimeEvent>()
    private var pendingDeltaJob: Job? = null
    private var pendingDeltaRunId: String? = null
    private var autoRetryEnabled = false
    private val autoRetryAttempts = mutableMapOf<String, Int>()
    private val autoRetryTokens = mutableMapOf<String, Any>()
    private val autoRetryJobs = mutableMapOf<String, Job>()
    private val autoRetryRetiredRunIds = mutableMapOf<String, String>()
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
                    autoRetryJobs[sessionId]?.isActive != true
                ) {
                    scheduleReconcile(sessionId, BUSY_RECONCILE_DELAY_MS)
                }
                previousBusy = ids
            }
        }
        viewModelScope.launch {
            autoRetry.collect { autoRetryEnabled = it }
        }
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) {
                    prepareRuntime()
                    runtime.ensureStarted()
                }
                val config = providers.resolveConfig(modelConfigStore.read())
                modelConfigStore.save(config)
                if (mutableState.value.providerId.isBlank()) applySessionModel(config.toSnapshot())
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
        if (current.switchingConversation) return
        val sessionId = current.conversationId ?: return
        val root = current.conversationCwd.takeIf { it.isNotBlank() }?.let(::File)
        if (root == null) {
            mutableState.update { it.copy(error = uiStrings.preparingSession) }
            return
        }
        if (current.attachments.size >= MAX_ATTACHMENTS) {
            mutableState.update { it.copy(error = uiStrings.maxAttachments) }
            return
        }
        launchAttachmentImport(sessionId, root) { attachmentStore.import(uri, root) }
    }

    internal fun launchAttachmentImport(
        sessionId: String,
        root: File,
        load: suspend () -> AttachmentImport,
    ) {
        viewModelScope.launch {
            val result = withContext(ioDispatcher) { load() }
            if (mutableState.value.conversationId != sessionId) {
                if (result is AttachmentImport.Imported) {
                    runCatching { attachmentStore.file(result.ref, root).delete() }
                }
                return@launch
            }
            when (result) {
                is AttachmentImport.Imported -> mutableState.update {
                    if (it.attachments.size >= MAX_ATTACHMENTS) {
                        it.copy(error = uiStrings.maxAttachments)
                    } else {
                        it.copy(attachments = it.attachments + result.ref, error = null)
                    }
                }

                is AttachmentImport.TooLarge -> mutableState.update { it.copy(error = uiStrings.attachmentTooLarge) }
                is AttachmentImport.InsufficientSpace -> mutableState.update { it.copy(error = uiStrings.insufficientSpace) }
                is AttachmentImport.UnsupportedImage -> mutableState.update { it.copy(error = uiStrings.unsupportedImage) }
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
            prompt = buildPrompt(current.quote, current.attachments, text),
            imageRefs = imageRefs(current.attachments),
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
                input = mergeDroppedInput(state.input, dropped),
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
        val root = attachmentRoot(current)
        val imageRefs = imageRefs(current.attachments)
        if (imageRefs.isNotEmpty() && root == null) {
            mutableState.update { it.copy(error = uiStrings.preparingSession) }
            return
        }
        val prompt = buildPrompt(current.quote, current.attachments, text)
        mutableState.update {
            it.copy(input = "", quote = null, attachments = emptyList(), error = null)
        }
        cacheCurrentSession()
        viewModelScope.launch {
            val images = normalizeAttachments(sessionId, imageRefs, root)
            try {
                sessionOpenJobs[sessionId]?.join()
                when (kind) {
                    InputQueueKind.STEER -> runtime.steer(sessionId, prompt, images)
                    InputQueueKind.FOLLOW_UP -> runtime.followUp(sessionId, prompt, images)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(error = userFacingError(error.message ?: uiStrings.requestFailed))
                }
            }
        }
    }

    fun retry(messageId: Long) {
        flushPendingDeltas()
        val current = mutableState.value
        if (!current.ready || !current.historyLoaded) return
        if (current.sending || current.switchingConversation) return
        val sessionId = current.conversationId ?: return
        val index = current.messages.indexOfLast { it.id == messageId }
        if (index < 0 || index != current.messages.lastIndex) return
        val failed = current.messages[index]
        if (failed.role != AssistantRole.ASSISTANT || failed.status != MessageStatus.FAILED) return
        clearAutoRetry(sessionId)
        beginRetry(sessionId)
    }

    private fun beginRetry(sessionId: String) {
        val now = System.currentTimeMillis()
        if (mutableState.value.conversationId == sessionId) {
            mutableState.update { state ->
                state.copy(
                    messages = markRetryingTail(state.messages, now),
                    sending = true,
                    activity = uiStrings.thinking,
                    activityStartedAt = now,
                    error = null,
                )
            }
            cacheCurrentSession()
        } else {
            val cached = conversationCache[sessionId] ?: return
            conversationCache[sessionId] = cached.copy(
                messages = markRetryingTail(cached.messages, now),
                sending = true,
                activity = uiStrings.thinking,
                activityStartedAt = now,
            )
        }
        autoRetryJobs.remove(sessionId)
        autoRetryTokens.remove(sessionId)
        autoRetryRetiredRunIds.remove(sessionId)
        viewModelScope.launch {
            try {
                sessionOpenJobs[sessionId]?.join()
                runtime.retryLastTurn(sessionId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                finishRunError(sessionId, error.message ?: uiStrings.requestFailed)
            }
        }
    }

    private fun markRetryingTail(messages: List<AssistantMessage>, now: Long): List<AssistantMessage> {
        val index = messages.indexOfLast {
            it.role == AssistantRole.ASSISTANT && it.status == MessageStatus.FAILED
        }
        if (index < 0) {
            return messages + AssistantMessage(
                id = (messages.maxOfOrNull { it.id } ?: -1L) + 1,
                role = AssistantRole.ASSISTANT,
                text = "",
                streaming = true,
                status = MessageStatus.RUNNING,
                startedAt = now,
            )
        }
        val retrying = messages[index].copy(
            streaming = true,
            retrying = true,
            runId = "",
            finishedAt = null,
        )
        return messages.take(index) + retrying
    }

    private fun maybeAutoRetry(sessionId: String, failure: RuntimeEvent.RunFailed) {
        if (!autoRetryEnabled) return
        if (!uiStrings.canAutoRetry(uiStrings.runErrorKind(failure.message))) return
        val attempt = (autoRetryAttempts[sessionId] ?: 0) + 1
        if (attempt > MAX_AUTO_RETRIES) {
            clearAutoRetry(sessionId)
            return
        }
        autoRetryAttempts[sessionId] = attempt
        autoRetryRetiredRunIds[sessionId] = failure.runId
        autoRetryJobs.remove(sessionId)?.cancel()
        val token = Any()
        autoRetryTokens[sessionId] = token
        autoRetryJobs[sessionId] = viewModelScope.launch {
            val label = uiStrings.autoRetrying(attempt, MAX_AUTO_RETRIES)
            if (mutableState.value.conversationId == sessionId) {
                mutableState.update {
                    it.copy(sending = true, activity = label, activityStartedAt = System.currentTimeMillis())
                }
            } else {
                conversationCache[sessionId]?.let { cached ->
                    conversationCache[sessionId] = cached.copy(
                        sending = true,
                        activity = label,
                        activityStartedAt = System.currentTimeMillis(),
                    )
                }
            }
            delay(AUTO_RETRY_DELAY_MS)
            if (autoRetryTokens[sessionId] !== token) return@launch
            if (runtime.busySessionIds.value.contains(sessionId)) return@launch
            beginRetry(sessionId)
        }
    }

    private fun dismissInterruptedNotice(sessionId: String) {
        interruptedQuestionNoticeSessions -= sessionId
        interruptedRunNoticeSessions -= sessionId
        if (mutableState.value.conversationId == sessionId) {
            mutableState.update { state ->
                state.copy(messages = state.messages.filterNot { message -> message.notice.isInterruptedNotice() })
            }
        }
        conversationCache[sessionId]?.let { cached ->
            conversationCache[sessionId] = cached.copy(
                messages = cached.messages.filterNot { message -> message.notice.isInterruptedNotice() },
            )
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

    private fun clearAutoRetry(sessionId: String) {
        autoRetryAttempts.remove(sessionId)
        autoRetryTokens.remove(sessionId)
        autoRetryRetiredRunIds.remove(sessionId)
        autoRetryJobs.remove(sessionId)?.cancel()
    }

    private fun startTurn(
        prompt: String,
        titleSeed: String = prompt,
        imageRefs: List<AttachmentRef> = emptyList(),
    ) {
        val current = mutableState.value
        val sessionId = current.conversationId ?: return
        val root = attachmentRoot(current)
        if (imageRefs.isNotEmpty() && root == null) {
            mutableState.update { it.copy(error = uiStrings.preparingSession) }
            return
        }
        clearAutoRetry(sessionId)
        dismissInterruptedNotice(sessionId)
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
                conversationTitle = if (
                    it.conversationTitle.isBlank() || it.conversationTitle == uiStrings.newChatTitle
                ) {
                    titleFrom(titleSeed)
                } else {
                    it.conversationTitle
                },
            )
        }
        cacheCurrentSession()
        viewModelScope.launch {
            val images = normalizeAttachments(sessionId, imageRefs, root)
            try {
                sessionOpenJobs[sessionId]?.join()
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
        mutableState.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            val expansion = withContext(ioDispatcher) {
                skillExpander?.expand(current.conversationCwd, name, args)
            }
            if (mutableState.value.conversationId != sessionId) return@launch
            if (expansion == null) {
                mutableState.update { it.copy(sending = false, error = uiStrings.skillMissing(name)) }
                return@launch
            }
            startTurn(
                prompt = buildPrompt(current.quote, current.attachments, expansion.prompt),
                titleSeed = args.trim().ifBlank { expansion.name },
                imageRefs = imageRefs(current.attachments),
            )
        }
    }

    private fun expandInlineAndSend(mentions: SkillMentions) {
        val current = mutableState.value
        val sessionId = current.conversationId ?: return
        mutableState.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            val expansions = withContext(ioDispatcher) {
                mentions.names.map { name -> skillExpander?.expand(current.conversationCwd, name, "") }
            }
            if (mutableState.value.conversationId != sessionId) return@launch
            val missing = mentions.names.getOrNull(expansions.indexOfFirst { it == null })
            if (missing != null) {
                mutableState.update { it.copy(sending = false, error = uiStrings.skillMissing(missing)) }
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
                prompt = buildPrompt(current.quote, current.attachments, prompt),
                titleSeed = mentions.text.trim().ifBlank { expansions.firstOrNull()?.name.orEmpty() },
                imageRefs = imageRefs(current.attachments),
            )
        }
    }

    fun stop() {
        flushPendingDeltas()
        val sessionId = mutableState.value.conversationId ?: return
        clearAutoRetry(sessionId)
        val dropped = runtime.stop(sessionId)
        if (mutableState.value.conversationId == sessionId) {
            mutableState.update { state ->
                state.copy(
                    input = mergeDroppedInput(state.input, dropped),
                    queuedInputs = emptyList(),
                )
            }
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

    private fun scheduleReconcile(sessionId: String, delayMs: Long) {
        if (reconcileJobs[sessionId]?.isActive == true) return
        reconcileJobs[sessionId] = viewModelScope.launch {
            try {
                if (delayMs > 0) delay(delayMs)
                reconcile(sessionId)
            } finally {
                reconcileJobs.remove(sessionId)
            }
        }
    }

    private suspend fun reconcile(sessionId: String) {
        runtime.activeRuns.value[sessionId]?.let { active ->
            val current = mutableState.value
            if (current.conversationId != sessionId || !current.sending) return
            val conversation = current.conversationState()
            if (conversation.messages.any { it.runId == active.runId }) return
            val adopted = conversation.adoptActiveRun(active.runId, uiStrings.thinking, active.startedAt)
            mutableState.update { it.applyConversation(adopted) }
            cacheCurrentSession()
            return
        }
        val current = mutableState.value
        val isCurrent = current.conversationId == sessionId
        if (isCurrent && !current.sending) return
        if (autoRetryJobs[sessionId]?.isActive == true) return
        val history = withContext(ioDispatcher) { runtime.history(sessionId) }
        val sessionModel = withContext(ioDispatcher) { runtime.sessionModel(sessionId) }
        val settled = history
            .map { message ->
                message.error?.let { raw -> message.copy(error = uiStrings.runError(raw)) } ?: message
            }
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
            mutableState.update {
                it.copy(
                    messages = settled,
                    sending = false,
                    activity = null,
                    activityStartedAt = null,
                    historyLoaded = true,
                    conversationTitle = it.conversationTitle.ifBlank { titleFrom(settled) },
                )
            }
            applySessionModel(sessionModel)
            cacheCurrentSession()
        } else {
            val cached = conversationCache[sessionId] ?: return
            conversationCache[sessionId] = cached.copy(
                messages = settled,
                sending = false,
                activity = null,
                activityStartedAt = null,
                loaded = true,
                sessionModel = sessionModel ?: cached.sessionModel,
            )
        }
    }

    fun compactContext() {
        flushPendingDeltas()
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

    fun newSession(workspaceId: String, initialInput: String = "") {
        viewModelScope.launch {
            cacheCurrentSession()
            createSession(workspaceId, initialInput)
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
        val current = mutableState.value
        if (current.sending || current.switchingConversation) return
        val option = current.models.firstOrNull { it.id == modelId } ?: return
        val providerId = current.providerId.ifBlank { option.providerId }
        val thinking = current.thinkingLevel.takeIf { it in option.thinkingLevels } ?: ThinkingLevel.OFF
        changeSessionModel(SessionModelSnapshot(providerId, option.id, thinking), thinkingOnly = false)
    }

    fun setProvider(providerId: String) {
        val current = mutableState.value
        if (current.sending || current.switchingConversation) return
        if (providerId == current.providerId) return
        val options = providers.models(providerId)
        val model = options.firstOrNull { it.id == current.modelId } ?: options.firstOrNull() ?: return
        val thinking = current.thinkingLevel.takeIf { it in model.thinkingLevels } ?: ThinkingLevel.OFF
        changeSessionModel(SessionModelSnapshot(providerId, model.id, thinking), thinkingOnly = false)
    }

    fun setThinkingLevel(level: ThinkingLevel) {
        val current = mutableState.value
        if (current.sending || current.switchingConversation) return
        val model = current.sessionModel() ?: return
        if (model.thinkingLevel == level) return
        changeSessionModel(model.copy(thinkingLevel = level), thinkingOnly = true)
    }

    private fun changeSessionModel(model: SessionModelSnapshot, thinkingOnly: Boolean) {
        val sessionId = mutableState.value.conversationId ?: return
        applySessionModel(model)
        cacheCurrentSession()
        viewModelScope.launch {
            try {
                if (thinkingOnly) {
                    runtime.setSessionThinkingLevel(sessionId, model.thinkingLevel)
                } else {
                    runtime.setSessionModel(sessionId, model.providerId, model.modelId, model.thinkingLevel)
                }
                modelConfigStore.save(ModelConfig(model.providerId, model.modelId, model.thinkingLevel))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val fallback = if (thinkingOnly) uiStrings.switchThinkingFailed else uiStrings.switchModelFailed
                mutableState.update { it.copy(error = userFacingError(error.message ?: fallback)) }
            }
            val truth = withContext(ioDispatcher) { runtime.sessionModel(sessionId) }
            if (truth != null && mutableState.value.conversationId == sessionId) {
                applySessionModel(truth)
                cacheCurrentSession()
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
                usage = cached?.usage,
                compacting = false,
                historyLoaded = cached?.loaded == true,
                switchingConversation = cached == null,
                input = "",
                quote = null,
                attachments = emptyList(),
                error = null,
                queuedInputs = emptyList(),
                importingAttachment = false,
            )
        }
        applySessionModel(cached?.sessionModel)
        openSession(summary)
    }

    private fun ConversationState.withCachedInterruptedNotices(sessionId: String): ConversationState {
        if (sessionId !in interruptedQuestionNoticeSessions && sessionId !in interruptedRunNoticeSessions) return this
        val cached = conversationCache[sessionId] ?: return this
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

    private fun openSession(summary: ConversationSummary) {
        if (sessionOpenJobs.containsKey(summary.id)) return
        val job = viewModelScope.launch {
            try {
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
                val localized = history.map { message ->
                    message.error?.let { raw -> message.copy(error = uiStrings.runError(raw)) } ?: message
                }
                val title = summary.title.ifBlank { titleFrom(localized) }
                val base = ConversationState(
                    messages = localized,
                    title = title,
                    loaded = true,
                    usage = usage,
                    sessionModel = sessionModel,
                )
                val snapshot = if (activeRun == null) {
                    base
                } else {
                    base.adoptActiveRun(activeRun.runId, uiStrings.thinking, activeRun.startedAt)
                }
                val resolved = snapshot.withCachedInterruptedNotices(summary.id)
                    .withInterruptedNotice(summary.id, interruptedQuestionNoticeSessions) {
                        interruptedQuestions(summary.id).size.takeIf { count -> count > 0 }
                            ?.let { count -> TurnNotice.InterruptedQuestions(count) }
                    }
                    .withInterruptedNotice(summary.id, interruptedRunNoticeSessions) {
                        interruptedRuns(summary.id).size.takeIf { count -> count > 0 }
                            ?.let { count -> TurnNotice.InterruptedRun(count) }
                    }
                conversationCache[summary.id] = resolved
                if (mutableState.value.conversationId == summary.id) {
                    mutableState.update {
                        it.copy(
                            messages = resolved.messages,
                            conversationTitle = resolved.title,
                            sending = resolved.sending,
                            activity = resolved.activity,
                            activityStartedAt = resolved.activityStartedAt,
                            usage = resolved.usage,
                            historyLoaded = true,
                            switchingConversation = false,
                            queuedInputs = queuedInputs,
                            importingAttachment = false,
                        )
                    }
                    applySessionModel(resolved.sessionModel)
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

    private fun createSession(workspaceId: String, initialInput: String = "") {
        viewModelScope.launch {
            try {
                val opened = withContext(ioDispatcher) { runtime.newSession(workspaceId) }
                val projectCwd = opened.cwd
                val sessionModel = withContext(ioDispatcher) { runtime.sessionModel(opened.sessionId) }
                conversationCache[opened.sessionId] = ConversationState(sessionModel = sessionModel)
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
                        usage = null,
                        compacting = false,
                        historyLoaded = true,
                        switchingConversation = false,
                        input = initialInput,
                        quote = null,
                        attachments = emptyList(),
                        error = null,
                        queuedInputs = emptyList(),
                        importingAttachment = false,
                    )
                }
                applySessionModel(sessionModel)
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
            usage = current.usage,
            sessionModel = current.sessionModel(),
        )
    }

    private suspend fun refreshUsage(sessionId: String) {
        val usage = withContext(ioDispatcher) { runtime.usage(sessionId) } ?: return
        if (mutableState.value.conversationId == sessionId) {
            mutableState.update { it.copy(usage = usage) }
            cacheCurrentSession()
        } else {
            conversationCache[sessionId]?.let { cached ->
                conversationCache[sessionId] = cached.copy(usage = usage)
            }
        }
    }

    private suspend fun reloadHistoryAfterCompaction(sessionId: String) {
        if (mutableState.value.conversationId != sessionId) return
        val current = mutableState.value
        if (current.sending) return
        val history = withContext(ioDispatcher) { runtime.history(sessionId) }
        val localized = history.map { message ->
            message.error?.let { raw -> message.copy(error = uiStrings.runError(raw)) } ?: message
        }
        var nextId = (localized.maxOfOrNull { it.id } ?: -1L) + 1
        val notices = current.messages.takeLastWhile { it.notice != null }.map { it.copy(id = nextId++) }
        val next = localized + notices
        mutableState.update {
            it.copy(
                messages = next,
                historyLoaded = true,
                conversationTitle = it.conversationTitle.ifBlank { titleFrom(next) },
            )
        }
        cacheCurrentSession()
    }

    private fun onSessionEvent(scoped: SessionRuntimeEvent) {
        val event = scoped.event
        if (event is RuntimeEvent.QueueChanged) {
            flushPendingDeltas()
            if (scoped.sessionId == mutableState.value.conversationId) {
                mutableState.update { it.copy(queuedInputs = queueInputs(event.steering, event.followUp)) }
            }
            return
        }
        if (event is RuntimeEvent.QueuedInputsDropped) {
            flushPendingDeltas()
            if (scoped.sessionId == mutableState.value.conversationId) {
                mutableState.update { state ->
                    state.copy(
                        input = mergeDroppedInput(state.input, event.texts),
                        queuedInputs = emptyList(),
                    )
                }
            }
            return
        }
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
        if (event is RuntimeEvent.ActivityChanged && autoRetryRetiredRunIds[scoped.sessionId] == event.runId) {
            return
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
        when (event) {
            is RuntimeEvent.RunFailed -> maybeAutoRetry(scoped.sessionId, event)
            is RuntimeEvent.RunFinished -> if (event.status != RunStatus.FAILED) clearAutoRetry(scoped.sessionId)
            else -> Unit
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
        usage = usage,
        sessionModel = sessionModel(),
    )

    private fun ChatUiState.applyConversation(state: ConversationState): ChatUiState = copy(
        messages = state.messages,
        conversationTitle = state.title,
        sending = state.sending,
        activity = state.activity,
        activityStartedAt = state.activityStartedAt,
        usage = state.usage,
    )

    private fun finishRunError(sessionId: String, message: String) {
        if (mutableState.value.conversationId != sessionId) {
            conversationCache[sessionId]?.let { cached ->
                conversationCache[sessionId] = cached.finishWithError("", message, uiStrings)
            }
            return
        }
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

    private fun applySessionModel(model: SessionModelSnapshot?) {
        if (model == null) return
        val options = providers.models(model.providerId)
        val option = options.firstOrNull { it.id == model.modelId }
        mutableState.update {
            it.copy(
                providerId = model.providerId,
                modelId = model.modelId,
                modelLabel = option?.displayName ?: model.modelId,
                thinkingLevel = model.thinkingLevel,
                models = options,
            )
        }
    }

    private fun ChatUiState.sessionModel(): SessionModelSnapshot? =
        providerId.takeIf { it.isNotBlank() }
            ?.let { provider -> SessionModelSnapshot(provider, modelId, thinkingLevel) }

    private fun ModelConfig.toSnapshot(): SessionModelSnapshot =
        SessionModelSnapshot(providerId, modelId, thinkingLevel)

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

    private fun attachmentRoot(state: ChatUiState): File? =
        state.conversationCwd.takeIf { it.isNotBlank() }?.let(::File)

    private fun imageRefs(attachments: List<AttachmentRef>): List<AttachmentRef> =
        attachments.filter { it.kind == AttachmentKind.IMAGE }

    private suspend fun normalizeAttachments(
        sessionId: String,
        refs: List<AttachmentRef>,
        root: File?,
    ): List<ImagePart> {
        if (refs.isEmpty() || root == null) return emptyList()
        if (mutableState.value.conversationId == sessionId) {
            mutableState.update { it.copy(importingAttachment = true) }
        }
        var failed = false
        val parts = try {
            withContext(ioDispatcher) {
                refs.mapNotNull { ref ->
                    val normalized = runCatching {
                        normalizeImage(attachmentStore.file(ref, root), ref.mime)
                    }.getOrNull()
                    if (normalized == null) {
                        failed = true
                        null
                    } else {
                        ImagePart(normalized.data, normalized.mimeType)
                    }
                }
            }
        } finally {
            if (mutableState.value.conversationId == sessionId) {
                mutableState.update { it.copy(importingAttachment = false) }
            }
        }
        if (failed && mutableState.value.conversationId == sessionId) {
            mutableState.update { it.copy(error = it.error ?: uiStrings.imageFailed) }
        }
        return parts
    }

    private fun queueInputs(steering: List<String>, followUp: List<String>): List<QueuedInput> =
        steering.map { QueuedInput(InputQueueKind.STEER, it) } +
            followUp.map { QueuedInput(InputQueueKind.FOLLOW_UP, it) }

    private fun mergeDroppedInput(input: String, dropped: List<String>): String {
        val texts = dropped.map { it.trim() }.filter { it.isNotEmpty() }
        if (texts.isEmpty()) return input
        val merged = texts.joinToString("\n\n")
        return if (input.isBlank()) merged else input.trimEnd() + "\n\n" + merged
    }

    private companion object {
        const val MAX_ATTACHMENTS = 20
        const val LEGACY_NEW_TITLE = "新对话"
        const val MAX_AUTO_RETRIES = 5
        const val AUTO_RETRY_DELAY_MS = 2_000L
    }
}
