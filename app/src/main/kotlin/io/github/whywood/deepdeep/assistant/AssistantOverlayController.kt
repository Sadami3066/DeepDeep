package io.github.whywood.deepdeep.assistant

import android.net.Uri
import io.github.whywood.deepdeep.conversation.ConversationState
import io.github.whywood.deepdeep.conversation.ConversationStore
import io.github.whywood.deepdeep.conversation.ConversationStrings
import io.github.whywood.deepdeep.core.resolveAttachmentRoot
import io.github.whywood.deepdeep.data.AttachmentImport
import io.github.whywood.deepdeep.data.AttachmentKind
import io.github.whywood.deepdeep.data.AttachmentRef
import io.github.whywood.deepdeep.data.AttachmentStore
import io.github.whywood.deepdeep.data.ImageNormalizer
import io.github.whywood.deepdeep.data.NormalizedImage
import io.github.whywood.deepdeep.data.encodeAttachmentBlock
import io.github.whywood.deepdeep.runtime.AgentRuntime
import io.github.whywood.deepdeep.runtime.AssistantRole
import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.runtime.ImagePart
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.runtime.ModelConfig
import io.github.whywood.deepdeep.runtime.ModelOption
import io.github.whywood.deepdeep.runtime.ProviderDirectory
import io.github.whywood.deepdeep.runtime.ProviderSummary
import io.github.whywood.deepdeep.runtime.RuntimeWorkspace
import io.github.whywood.deepdeep.runtime.SessionModelSnapshot
import io.github.whywood.deepdeep.runtime.ThinkingLevel
import io.github.whywood.deepdeep.ui.common.chat.state.AttachmentImportFailure
import io.github.whywood.deepdeep.ui.common.chat.state.AttachmentImporter
import io.github.whywood.deepdeep.ui.common.chat.state.MAX_ATTACHMENTS
import io.github.whywood.deepdeep.ui.common.chat.state.SessionDraftStore
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext



enum class AssistantOverlayError {
    StartFailed,
    SendFailed,
    SwitchFailed,
    AttachmentFailed,
    AttachmentLimit,
    AttachmentTooLarge,
}

data class AssistantOverlayState(
    val conversation: ConversationState = ConversationState(),
    val sessionId: String? = null,
    val sessionTitle: String = "",
    val conversationCwd: String = "",
    val workspaceId: String = "",
    val workspaces: List<RuntimeWorkspace> = emptyList(),
    val allSessions: List<ConversationSummary> = emptyList(),
    val providers: List<ProviderSummary> = emptyList(),
    val providerId: String = "",
    val modelId: String = "",
    val modelLabel: String = "",
    val models: List<ModelOption> = emptyList(),
    val thinkingLevel: ThinkingLevel = ThinkingLevel.OFF,
    val thinkingLevels: List<ThinkingLevel> = listOf(ThinkingLevel.OFF),
    val attachments: List<AttachmentRef> = emptyList(),
    val importingAttachment: Boolean = false,
    val error: AssistantOverlayError? = null,
)

class AssistantOverlayController(
    private val runtime: AgentRuntime,
    private val store: ConversationStore,
    private val activeSessionId: () -> String?,
    private val homeWorkspaceId: String,
    private val scope: CoroutineScope,
    private val providers: ProviderDirectory,
    private val attachmentStore: AttachmentStore,
    private val homeDir: File,
    private val configuredProviders: () -> Set<String> = { emptySet() },
    private val defaultModel: suspend () -> ModelConfig = { ModelConfig() },
    private val onSessionSelected: (String) -> Unit = {},
    private val initialSessionId: () -> String? = { null },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val normalizeImage: suspend (File, String) -> NormalizedImage? = { file, mime ->
        ImageNormalizer.normalize(file, mime)
    },
) {
    private val importer = AttachmentImporter(attachmentStore, scope, ioDispatcher)
    private val queuedPreparations = mutableMapOf<String, MutableSet<Job>>()
    private val mutableState = MutableStateFlow(AssistantOverlayState())
    val state: StateFlow<AssistantOverlayState> = mutableState.asStateFlow()

    private var startJob: Job? = null
    private var stateJob: Job? = null
    private val openMutex = Mutex()

    fun start() {
        if (startJob != null) return
        startJob = scope.launch {
            runCatching { runtime.ensureStarted() }
            val sessionId = openMutex.withLock {
                val target = openInitialSession()
                if (target != null && runCatching { store.open(target) }.isFailure) null else target
            }
            if (sessionId == null) {
                mutableState.value = AssistantOverlayState(error = AssistantOverlayError.StartFailed)
                startJob = null
                return@launch
            }
            store.pin(sessionId)
            mutableState.value = AssistantOverlayState(
                conversation = store.peek(sessionId) ?: ConversationState(),
                sessionId = sessionId,
            )
            observeConversation(sessionId)
            loadMeta(sessionId)
        }
    }

    fun send(text: String, followUp: Boolean = false): Boolean {
        val current = mutableState.value
        val sessionId = current.sessionId ?: return false
        if (!current.conversation.loaded) return false
        val attachments = current.attachments
        val root = resolveAttachmentRoot(current.conversationCwd, homeDir)
        val prompt = buildPrompt(attachments, text.trim(), root)
        if (prompt.isEmpty()) return false
        val imageRefs = attachments.filter { it.kind == AttachmentKind.IMAGE }
        mutableState.update { it.copy(attachments = emptyList(), error = null) }
        if (isBusy(sessionId, current)) {
            enqueue(sessionId, prompt, followUp, imageRefs, root)
        } else {
            store.send(sessionId, prompt, title = null, prepareImages = { normalizeImages(imageRefs, root) })
        }
        return true
    }

    fun attach(uri: Uri) {
        val current = mutableState.value
        val sessionId = current.sessionId ?: return
        val root = resolveAttachmentRoot(current.conversationCwd, homeDir)
        importAttachment(sessionId, root) { attachmentStore.import(uri, root) }
    }

    fun removeAttachment(id: String) {
        mutableState.update { current ->
            current.copy(attachments = current.attachments.filterNot { attachment -> attachment.id == id })
        }
    }

    internal fun importAttachment(sessionId: String, root: File, load: suspend () -> AttachmentImport) {
        if (mutableState.value.attachments.size >= MAX_ATTACHMENTS) {
            setError(AssistantOverlayError.AttachmentLimit)
            return
        }
        importer.import(
            sessionId = sessionId,
            root = root,
            currentSession = { mutableState.value.sessionId },
            attachmentCount = { mutableState.value.attachments.size },
            load = load,
            onStart = {
                mutableState.update { if (it.sessionId == sessionId) it.copy(importingAttachment = true) else it }
            },
            onImported = { ref ->
                mutableState.update { it.copy(attachments = it.attachments + ref, error = null) }
            },
            onFailure = { failure ->
                setError(
                    when (failure) {
                        AttachmentImportFailure.LIMIT -> AssistantOverlayError.AttachmentLimit
                        AttachmentImportFailure.TOO_LARGE -> AssistantOverlayError.AttachmentTooLarge
                        else -> AssistantOverlayError.AttachmentFailed
                    },
                )
            },
            onFinished = {
                mutableState.update { if (it.sessionId == sessionId) it.copy(importingAttachment = false) else it }
            },
        )
    }

    private suspend fun normalizeImages(refs: List<AttachmentRef>, root: File): List<ImagePart> =
        withContext(ioDispatcher) {
            refs.mapNotNull { ref ->
                try {
                    val file = attachmentStore.file(ref, root)
                    normalizeImage(file, ref.mime)
                        ?.let { image -> ImagePart(image.data, image.mimeType, path = file.absolutePath) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    null
                }
            }
        }

    private fun buildPrompt(attachments: List<AttachmentRef>, body: String, root: File): String {
        if (attachments.isEmpty()) return body
        return (encodeAttachmentBlock(attachments, root) + "\n\n" + body).trim()
    }

    fun stop() {
        mutableState.value.sessionId?.let { sessionId ->
            queuedPreparations.remove(sessionId)?.forEach { it.cancel() }
        }
        mutableState.value.sessionId?.let { store.stop(it) }
    }

    fun retry(messageId: Long) {
        val tail = mutableState.value.conversation.messages.lastOrNull() ?: return
        if (tail.id != messageId) return
        if (tail.role != AssistantRole.ASSISTANT || tail.status != MessageStatus.FAILED) return
        mutableState.value.sessionId?.let { store.retryLast(it) }
    }

    fun toggleStep(messageId: Long, stepId: Long) {
        mutableState.value.sessionId?.let { store.toggleStep(it, messageId, stepId) }
    }

    fun openSession(sessionId: String) {
        scope.launch {
            openMutex.withLock {
                if (sessionId == mutableState.value.sessionId) return@withLock
                val opened = runCatching {
                    runtime.allSessions().firstOrNull { it.id == sessionId }
                        ?.let { runtime.openSession(it.filePath, it.cwd) }
                }.getOrNull()
                val target = opened?.sessionId ?: sessionId
                if (runCatching { store.open(target) }.isFailure) {
                    setError(AssistantOverlayError.StartFailed)
                    return@withLock
                }
                val previous = mutableState.value.sessionId
                store.pin(target)
                previous?.takeIf { it != target }?.let(store::unpin)
                onSessionSelected(target)
                observeConversation(target)
                mutableState.update {
                    it.copy(
                        conversation = store.peek(target) ?: ConversationState(),
                        sessionId = target,
                        sessionTitle = "",
                        attachments = emptyList(),
                        importingAttachment = false,
                        error = null,
                    )
                }
                loadMeta(target)
            }
        }
    }

    fun newSession(workspaceId: String) {
        scope.launch {
            openMutex.withLock {
                val opened = runCatching { runtime.newSession(workspaceId) }.getOrNull()
                if (opened == null) {
                    setError(AssistantOverlayError.StartFailed)
                    return@withLock
                }
                if (runCatching { store.open(opened.sessionId) }.isFailure) {
                    setError(AssistantOverlayError.StartFailed)
                    return@withLock
                }
                val previous = mutableState.value.sessionId
                store.pin(opened.sessionId)
                previous?.takeIf { it != opened.sessionId }?.let(store::unpin)
                onSessionSelected(opened.sessionId)
                observeConversation(opened.sessionId)
                mutableState.update {
                    it.copy(
                        conversation = store.peek(opened.sessionId) ?: ConversationState(),
                        sessionId = opened.sessionId,
                        sessionTitle = "",
                        attachments = emptyList(),
                        importingAttachment = false,
                        error = null,
                    )
                }
                loadMeta(opened.sessionId)
            }
        }
    }

    fun refreshLists() {
        scope.launch {
            val workspaces = runCatching { runtime.workspaces() }.getOrDefault(emptyList())
            val allSessions = runCatching { runtime.allSessions() }.getOrDefault(emptyList())
            mutableState.update { it.copy(workspaces = workspaces, allSessions = allSessions) }
        }
    }

    fun selectProvider(providerId: String) {
        val current = mutableState.value
        val sessionId = current.sessionId ?: return
        if (isBusy(sessionId, current)) return
        val options = runCatching { providers.models(providerId) }.getOrDefault(emptyList())
        if (options.isEmpty()) return
        val model = options.firstOrNull { it.id == current.modelId } ?: options.first()
        val thinking = current.thinkingLevel.takeIf { it in model.thinkingLevels } ?: ThinkingLevel.OFF
        applyModel(sessionId, providerId, model.id, thinking)
    }

    fun selectModel(modelId: String) {
        val current = mutableState.value
        val sessionId = current.sessionId ?: return
        if (isBusy(sessionId, current)) return
        val option = current.models.firstOrNull { it.id == modelId } ?: return
        val providerId = current.providerId.ifBlank { option.providerId }
        val thinking = current.thinkingLevel.takeIf { it in option.thinkingLevels } ?: ThinkingLevel.OFF
        applyModel(sessionId, providerId, option.id, thinking)
    }

    fun selectThinking(level: ThinkingLevel) {
        val current = mutableState.value
        val sessionId = current.sessionId ?: return
        if (isBusy(sessionId, current)) return
        if (level == current.thinkingLevel || level !in current.thinkingLevels) return
        mutableState.update { it.copy(error = null) }
        scope.launch {
            try {
                runtime.setSessionThinkingLevel(sessionId, level)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                setError(AssistantOverlayError.SwitchFailed)
                return@launch
            }
            applyModelTruth(sessionId)
        }
    }

    fun clearQueue(): List<String> {
        val sessionId = mutableState.value.sessionId ?: return emptyList()
        return runCatching { runtime.clearQueuedInputs(sessionId) }.getOrDefault(emptyList())
    }

    fun reset() {
        startJob?.cancel()
        startJob = null
        stateJob?.cancel()
        stateJob = null
        mutableState.value.sessionId?.let(store::unpin)
        mutableState.value = AssistantOverlayState()
    }

    private fun observeConversation(sessionId: String) {
        stateJob?.cancel()
        stateJob = scope.launch {
            var wasSending = store.peek(sessionId)?.sending ?: false
            store.observe(sessionId).collect { conversation ->
                if (mutableState.value.sessionId != sessionId) return@collect
                mutableState.update { it.copy(conversation = conversation) }
                if (wasSending && !conversation.sending) loadMeta(sessionId)
                wasSending = conversation.sending
            }
        }
    }

    private fun loadMeta(sessionId: String) {
        scope.launch {
            val workspaces = runCatching { runtime.workspaces() }.getOrDefault(emptyList())
            val summaries = runCatching { runtime.allSessions() }.getOrDefault(emptyList())
            val summary = summaries.firstOrNull { it.id == sessionId }
            val workspaceId = summary
                ?.let { owner -> workspaces.firstOrNull { it.cwd == owner.cwd }?.id }
                ?: homeWorkspaceId
            val title = runCatching { runtime.sessionTitle(sessionId) }.getOrNull().orEmpty()
            val cwd = summary?.cwd.orEmpty()
            val model = modelSnapshot(sessionId)
            val providerList = usableProviders()
            mutableState.update { current ->
                if (current.sessionId != sessionId) {
                    current
                } else {
                    current.copy(
                        sessionTitle = title,
                        conversationCwd = cwd,
                        workspaceId = workspaceId,
                        workspaces = workspaces,
                        allSessions = summaries,
                        providers = providerList,
                        providerId = model.providerId,
                        modelId = model.modelId,
                        modelLabel = model.modelLabel,
                        models = model.models,
                        thinkingLevel = model.thinkingLevel,
                        thinkingLevels = model.thinkingLevels,
                    )
                }
            }
        }
    }

    private suspend fun modelSnapshot(sessionId: String): ModelView {
        val snapshot = runCatching { runtime.sessionModel(sessionId) }.getOrNull()
            ?: defaultModel().let { SessionModelSnapshot(it.providerId, it.modelId, it.thinkingLevel) }
        val options = runCatching { providers.models(snapshot.providerId) }.getOrDefault(emptyList())
        val option = options.firstOrNull { it.id == snapshot.modelId }
        val levels = option?.thinkingLevels?.takeIf { it.isNotEmpty() } ?: listOf(ThinkingLevel.OFF)
        return ModelView(
            providerId = snapshot.providerId,
            modelId = snapshot.modelId,
            modelLabel = option?.displayName ?: snapshot.modelId,
            models = options,
            thinkingLevel = snapshot.thinkingLevel.takeIf { it in levels } ?: ThinkingLevel.OFF,
            thinkingLevels = levels,
        )
    }

    private fun applyModel(sessionId: String, providerId: String, modelId: String, thinking: ThinkingLevel) {
        mutableState.update { it.copy(error = null) }
        scope.launch {
            try {
                runtime.setSessionModel(sessionId, providerId, modelId, thinking)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                setError(AssistantOverlayError.SwitchFailed)
                return@launch
            }
            applyModelTruth(sessionId)
        }
    }

    private suspend fun applyModelTruth(sessionId: String) {
        val model = modelSnapshot(sessionId)
        mutableState.update { current ->
            if (current.sessionId != sessionId) {
                current
            } else {
                current.copy(
                    providerId = model.providerId,
                    modelId = model.modelId,
                    modelLabel = model.modelLabel,
                    models = model.models,
                    thinkingLevel = model.thinkingLevel,
                    thinkingLevels = model.thinkingLevels,
                )
            }
        }
    }

    private fun enqueue(
        sessionId: String,
        prompt: String,
        followUp: Boolean,
        imageRefs: List<AttachmentRef>,
        root: File,
    ) {
        lateinit var job: Job
        job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val images = normalizeImages(imageRefs, root)
                queuedPreparations[sessionId]?.remove(job)
                if (followUp) {
                    runtime.followUp(sessionId, prompt, images)
                } else {
                    runtime.steer(sessionId, prompt, images)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (mutableState.value.sessionId == sessionId) setError(AssistantOverlayError.SendFailed)
            } finally {
                queuedPreparations[sessionId]?.let { jobs ->
                    jobs.remove(job)
                    if (jobs.isEmpty()) queuedPreparations.remove(sessionId)
                }
            }
        }
        queuedPreparations.getOrPut(sessionId) { mutableSetOf() }.add(job)
        job.start()
    }

    private fun isBusy(sessionId: String, current: AssistantOverlayState): Boolean =
        current.conversation.sending ||
            store.peek(sessionId)?.sending == true ||
            runtime.activeRuns.value.containsKey(sessionId)

    private fun setError(error: AssistantOverlayError) {
        mutableState.update { it.copy(error = error) }
    }

    private fun usableProviders(): List<ProviderSummary> {
        val configured = configuredProviders()
        return if (configured.isEmpty()) {
            providers.summaries
        } else {
            providers.summaries.filter { it.id in configured }
        }
    }

    private suspend fun openInitialSession(): String? {
        val summaries = runCatching { runtime.allSessions() }.getOrDefault(emptyList())
        val activeRun = runtime.activeRuns.value.maxByOrNull { it.value.startedAt }?.key
        val requested = initialSessionId()
            ?.takeIf { id -> id.isNotBlank() && summaries.any { it.id == id } }
        val preferred = initialSessionCandidates(
            requested = requested,
            activeRun = activeRun,
            activeSession = activeSessionId(),
        )
        for (id in preferred) {
            val summary = summaries.firstOrNull { it.id == id }
            if (summary == null) {
                onSessionSelected(id)
                return id
            }
            val opened = runCatching { runtime.openSession(summary.filePath, summary.cwd) }.getOrNull()
            onSessionSelected(opened?.sessionId ?: summary.id)
            return opened?.sessionId ?: summary.id
        }
        val latest = summaries.maxByOrNull { it.updatedAt }
        if (latest != null) {
            val opened = runCatching { runtime.openSession(latest.filePath, latest.cwd) }.getOrNull()
            onSessionSelected(opened?.sessionId ?: latest.id)
            return opened?.sessionId ?: latest.id
        }
        return runCatching { runtime.newSession(homeWorkspaceId).sessionId }.getOrNull()
            ?.also(onSessionSelected)
    }

    private data class ModelView(
        val providerId: String,
        val modelId: String,
        val modelLabel: String,
        val models: List<ModelOption>,
        val thinkingLevel: ThinkingLevel,
        val thinkingLevels: List<ThinkingLevel>,
    )
}

internal fun initialSessionCandidates(
    requested: String?,
    activeRun: String?,
    activeSession: String?,
): List<String> = listOfNotNull(
    requested?.takeIf { it.isNotBlank() },
    activeRun?.takeIf { it.isNotBlank() },
    activeSession?.takeIf { it.isNotBlank() },
).distinct()
