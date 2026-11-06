package io.github.whywood.deepdeep.runtime

import android.util.Log
import dev.pi.agent.AgentEvent
import dev.pi.agent.AgentMessage
import dev.pi.agent.asAssistantMessage
import dev.pi.agent.asLlmMessage
import dev.pi.ai.BlockContent
import dev.pi.ai.PlainTextContent
import dev.pi.ai.StopReason
import dev.pi.ai.TextContent
import dev.pi.ai.TextDeltaEvent
import dev.pi.ai.ThinkingDeltaEvent
import dev.pi.ai.UserMessage
import dev.pi.coding.session.QueueBehavior
import dev.pi.coding.session.SessionEvent
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AgentRuntimeHost internal constructor(
    private val homeDir: File,
    private val workspacesDir: File,
    private val engineFactory: suspend (File) -> SessionEngine,
    private val modelSettings: ModelSettings,
    private val catalog: SessionDirectory,
    private val workspaces: WorkspaceDirectory,
    private val providers: ProviderDirectory,
    private val backgroundScope: CoroutineScope,
    private val goalDriver: GoalDriver?,
    private val sessionCleaner: ((String) -> Unit)?,
    private val sessionArchived: ((String) -> Unit)?,
    private val runLedger: RunLedger = RunLedger(null),
    private val keepHandleAlive: (String) -> Boolean = { true },
) : AgentRuntime {
    constructor(
        homeDir: File,
        workspacesDir: File,
        engineFactory: suspend (File) -> PiSessionEngine,
        modelConfigStore: ModelConfigStore,
        catalog: SessionCatalog,
        workspaceStore: WorkspaceStore,
        providers: ProviderCatalog,
        backgroundScope: CoroutineScope,
        goalDriver: GoalDriver? = null,
        sessionCleaner: ((String) -> Unit)? = null,
        sessionArchived: ((String) -> Unit)? = null,
        runLedger: RunLedger = RunLedger(null),
        keepHandleAlive: (String) -> Boolean = { true },
    ) : this(
        homeDir, workspacesDir, { cwd -> engineFactory(cwd) }, modelConfigStore,
        CatalogSessionDirectory(catalog), WorkspaceStoreDirectory(workspaceStore),
        providers, backgroundScope, goalDriver, sessionCleaner, sessionArchived, runLedger, keepHandleAlive,
    )

    private val eventsBuffer = MutableSharedFlow<SessionRuntimeEvent>(
        extraBufferCapacity = 512,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )
    override val events: SharedFlow<SessionRuntimeEvent> = eventsBuffer.asSharedFlow()

    private val sdkEventsBuffer = MutableSharedFlow<SessionSdkEvent>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val sdkEvents: SharedFlow<SessionSdkEvent> = sdkEventsBuffer.asSharedFlow()

    private val busyState = MutableStateFlow<Set<String>>(emptySet())
    override val busySessionIds: StateFlow<Set<String>> = busyState.asStateFlow()

    private val activeRunState = MutableStateFlow<Map<String, ActiveRunSnapshot>>(emptyMap())
    override val activeRuns: StateFlow<Map<String, ActiveRunSnapshot>> = activeRunState.asStateFlow()

    override val interruptedRuns: StateFlow<List<InterruptedRun>> = runLedger.interruptedRuns

    private val mutex = Mutex()
    private val commands = SessionCommandPlane()
    private val handles = ConcurrentHashMap<String, RuntimeHandle>()
    private val mcp = RuntimeMcp(mutex, handles)
    private val closingSessions = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private val closingFiles = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private var lastOpenedSessionId: String? = null
    private var started = false

    override suspend fun start() = mutex.withLock {
        if (started) return@withLock
        homeDir.mkdirs()
        workspacesDir.mkdirs()
        if (handles.isEmpty()) {
            try {
                runLedger.loadLeftovers()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                logWarn(TAG, "run ledger load failed", error)
            }
        }
        started = true
    }

    override suspend fun dismissInterruptedRun(sessionId: String) {
        runLedger.dismiss(sessionId)
    }

    override suspend fun ensureStarted() {
        if (!started) start()
    }

    override suspend fun workspaces(): List<RuntimeWorkspace> = buildList {
        add(homeWorkspace())
        addAll(workspaces.read().filter { it.id != HOME_ID })
    }

    override suspend fun createWorkspace(name: String): RuntimeWorkspace = mutex.withLock {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "工作区名称不能为空" }
        val id = UUID.randomUUID().toString().replace("-", "").take(12)
        val directory = File(workspacesDir, id).apply { mkdirs() }
        val workspace = RuntimeWorkspace(
            id = id,
            name = trimmed,
            cwd = directory.absolutePath,
            createdAt = System.currentTimeMillis(),
        )
        workspaces.add(workspace)
        emitListChanged()
        workspace
    }

    override suspend fun renameWorkspace(id: String, name: String) {
        if (id == HOME_ID) return
        val workspace = workspaces.read().firstOrNull { it.id == id } ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        workspaces.add(workspace.copy(name = trimmed))
        emitListChanged()
    }

    override suspend fun deleteWorkspace(id: String) {
        if (id == HOME_ID) return
        val workspace = workspaces.read().firstOrNull { it.id == id } ?: return
        val sessions = catalog.list(workspace.cwd)
        sessions.forEach { session ->
            runCatching { stop(session.id) }
            mutex.withLock { detach(session.id, forget = true) }
            sessionCleaner?.invoke(session.id)
        }
        sessions.forEach { session ->
            catalog.delete(session.filePath)
            catalog.removeMeta(session.id)
            eventsBuffer.emit(SessionRuntimeEvent(session.id, RuntimeEvent.SessionRemoved()))
        }
        runCatching { File(workspace.cwd).deleteRecursively() }
        workspaces.remove(id)
        emitListChanged()
    }

    override suspend fun allSessions(): List<ConversationSummary> =
        catalog.listAll(visibleCwds()).filterNot { it.archived }

    override suspend fun workspaceSessionCount(workspaceId: String): Int =
        catalog.list(workspaceFor(workspaceId).cwd).size

    override suspend fun newSession(workspaceId: String): OpenedSession {
        val opened = mutex.withLock {
            val workspace = workspaceFor(workspaceId)
            val handle = attach(workspace.cwd, null)
            handle.touch()
            lastOpenedSessionId = handle.session.sessionId
            emitListChanged()
            OpenedSession(handle.session.sessionId, handle.session.sessionFile, workspace.cwd)
        }
        sweepIdleHandles()
        return opened
    }

    override suspend fun openSession(filePath: String, cwd: String): OpenedSession {
        val opened = mutex.withLock {
            closingFiles[filePath]?.await()
            handles.values.firstOrNull { it.session.sessionFile == filePath }?.let { existing ->
                existing.touch()
                lastOpenedSessionId = existing.session.sessionId
                OpenedSession(existing.session.sessionId, filePath, existing.cwd)
            } ?: run {
                val handle = attach(cwd, filePath)
                handle.touch()
                lastOpenedSessionId = handle.session.sessionId
                OpenedSession(handle.session.sessionId, handle.session.sessionFile, cwd)
            }
        }
        sweepIdleHandles()
        return opened
    }

    override suspend fun closeSession(sessionId: String) = mutex.withLock {
        detach(sessionId)
    }

    override suspend fun reloadSession(sessionId: String): String {
        val reopenedId = mutex.withLock {
            val handle = handles[sessionId] ?: error("Agent session is not ready")
            check(!handle.state.busy) { "Agent session is busy" }
            val file = handle.session.sessionFile ?: error("Agent session has no file")
            val cwd = handle.cwd
            val selected = lastOpenedSessionId == sessionId
            val lastActiveAt = handle.lastActiveAt
            detach(sessionId)
            val reopened = attach(cwd, file)
            reopened.lastActiveAt = lastActiveAt
            if (selected) lastOpenedSessionId = reopened.session.sessionId
            emitListChanged()
            reopened.session.sessionId
        }
        sweepIdleHandles()
        return reopenedId
    }

    suspend fun reloadMcp() = mcp.reload()

    suspend fun reconnectMcp(server: String) = mcp.reconnect(server)

    override suspend fun send(sessionId: String, text: String, images: List<ImagePart>) {
        val handle = handleFor(sessionId)
        val start = begin(handle, TurnSource.USER) ?: error("Agent session is busy")
        runTurn(handle, start.runId, text, images)
    }

    override suspend fun steer(sessionId: String, text: String, images: List<ImagePart>) {
        submitQueuedInput(sessionId, InputQueueKind.STEER, text, images)
    }

    override suspend fun followUp(sessionId: String, text: String, images: List<ImagePart>) {
        submitQueuedInput(sessionId, InputQueueKind.FOLLOW_UP, text, images)
    }

    override fun queuedInputs(sessionId: String): List<QueuedInput> {
        val handle = handles[sessionId] ?: return emptyList()
        val snapshot = handle.pending.snapshot()
        return snapshot.steering.map { QueuedInput(InputQueueKind.STEER, it) } +
            snapshot.followUp.map { QueuedInput(InputQueueKind.FOLLOW_UP, it) }
    }

    override fun clearQueuedInputs(sessionId: String): List<String> {
        val handle = handles[sessionId] ?: return emptyList()
        return clearQueuedInputs(handle)
    }

    private suspend fun submitQueuedInput(
        sessionId: String,
        kind: InputQueueKind,
        text: String,
        images: List<ImagePart>,
    ) {
        val handle = handleFor(sessionId)
        val start = begin(handle, TurnSource.USER)
        if (start != null) {
            runTurn(handle, start.runId, text, images)
            return
        }
        handle.pending.enqueue(kind, text)
        emitQueueChanged(handle, handle.state.runId.orEmpty())
        try {
            handle.session.prompt(text, images, kind.behavior())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            handle.pending.discard(kind, text)
            emitQueueChanged(handle, handle.state.runId.orEmpty())
            throw error
        }
    }

    override suspend fun retryLastTurn(sessionId: String) {
        val handle = handleFor(sessionId)
        val start = begin(handle, TurnSource.RETRY) ?: error("Agent session is busy")
        if (!handle.session.canRetryLastAssistant()) {
            release(handle, start.runId)
            error("Nothing to retry")
        }
        try {
            emit(handle, RuntimeEvent.RunStarted(start.runId, System.currentTimeMillis()))
            emitActivity(handle, start.runId, RuntimeActivity.Thinking)
            logInfo(TAG, "retry session=${handle.session.sessionId} run=${start.runId}")
            handle.session.retryLastAssistant()
            if (!handle.state.stopRequested) {
                val status = if (handle.state.terminalEmitted) RunStatus.FAILED else finishRun(handle, start.runId)
                maybeContinueGoal(handle, status)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (!handle.state.stopRequested) {
                emitTerminal(
                    handle = handle,
                    runId = start.runId,
                    text = handle.session.lastAssistantText().orEmpty(),
                    status = RunStatus.FAILED,
                    message = error.message,
                )
                maybeContinueGoal(handle, RunStatus.FAILED)
            }
        } finally {
            release(handle, start.runId)
        }
    }

    override suspend fun deliverReminder(sessionId: String, text: String, reminder: String?) {
        val handle = handleFor(sessionId)
        when (val delivery = commands.deliver(sessionId, TurnSource.REMINDER) { handle.session.isBusy }) {
            is SessionCommandPlane.Delivery.Started -> {
                val start = delivery.start
                afterBegin(handle, TurnSource.REMINDER)
                emit(handle, RuntimeEvent.TurnInjected(start.runId, text, reminder))
                emitListChanged()
                backgroundScope.launch { runTurn(handle, start.runId, text) }
            }

            is SessionCommandPlane.Delivery.Injected -> {
                handle.touch()
                emit(handle, RuntimeEvent.TurnInjected(delivery.runId, text, reminder))
                handle.session.prompt(text, behavior = QueueBehavior.FollowUp)
                emitListChanged()
            }

            null -> error("Agent session is busy")
        }
    }

    suspend fun deliverBackgroundTurn(
        sessionId: String,
        notice: TurnNotice?,
        prompt: String,
        source: TurnSource,
    ) {
        val handle = mutex.withLock { handles[sessionId] } ?: return
        when (val delivery = commands.deliver(sessionId, source) { handle.session.isBusy }) {
            is SessionCommandPlane.Delivery.Started -> {
                val start = delivery.start
                afterBegin(handle, source)
                emit(handle, BackgroundTurnStarted(start.runId, notice))
                backgroundScope.launch { runTurn(handle, start.runId, prompt, setTitle = false) }
            }

            is SessionCommandPlane.Delivery.Injected -> {
                handle.touch()
                if (notice != null) emit(handle, NoticeEvent(delivery.runId, notice))
                handle.session.prompt(prompt, behavior = QueueBehavior.FollowUp)
            }

            null -> Unit
        }
    }

    override suspend fun activeRunId(sessionId: String): String? = activeRunState.value[sessionId]?.runId

    fun activeTurnSource(sessionId: String): TurnSource? =
        if (activeRunState.value[sessionId] == null) null else commands.snapshot(sessionId)?.turnSource

    private suspend fun handleFor(sessionId: String): RuntimeHandle {
        mutex.withLock { handles[sessionId] }?.let { handle ->
            handle.touch()
            return handle
        }
        closingSessions[sessionId]?.await()
        val summary = HandleBudget.reconnectTarget(catalog.listAll(visibleCwds()), sessionId)
            ?: error("Agent session is not ready")
        val handle = mutex.withLock {
            closingSessions[sessionId]?.await()
            handles[sessionId] ?: attach(summary.cwd, summary.filePath)
        }
        sweepIdleHandles()
        return handle
    }

    private suspend fun begin(handle: RuntimeHandle, source: TurnSource): SessionCommandPlane.Start? {
        val start = commands.begin(handle.session.sessionId, source) ?: return null
        afterBegin(handle, source)
        return start
    }

    private suspend fun afterBegin(handle: RuntimeHandle, source: TurnSource) {
        handle.touch()
        publishBusy()
        publishActiveRun(handle)
        recordRunStart(handle, source)
        goalDriver?.noteTurn(handle.session.sessionId, source)
    }

    private suspend fun recordRunStart(handle: RuntimeHandle, source: TurnSource) {
        val sessionId = handle.session.sessionId
        val snapshot = commands.snapshot(sessionId) ?: return
        val runId = snapshot.runId ?: return
        try {
            runLedger.begin(sessionId, runId, snapshot.runStartedAt, source)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            logWarn(TAG, "run ledger begin failed session=$sessionId", error)
        }
    }

    private fun forgetRunRecord(runId: String) {
        backgroundScope.launch {
            try {
                runLedger.clear(runId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                logWarn(TAG, "run ledger clear failed run=$runId", error)
            }
        }
    }

    private suspend fun release(handle: RuntimeHandle, runId: String) {
        val finish = commands.finish(handle.session.sessionId, runId)
        if (!finish.released) return
        val droppedInputs = dropPendingInputs(handle, runId)
        if (droppedInputs.isNotEmpty()) {
            emit(handle, RuntimeEvent.QueuedInputsDropped(droppedInputs, runId))
        }
        handle.touch()
        publishBusy()
        publishActiveRun(handle)
        emitActivity(handle, runId, null)
        emit(handle, RuntimeEvent.SessionListChanged())
        try {
            runLedger.clear(runId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            logWarn(TAG, "run ledger clear failed run=$runId", error)
        }
        backgroundScope.launch { sweepIdleHandles() }
        if (finish.reconfigure) {
            val sessionId = handle.session.sessionId
            backgroundScope.launch {
                runCatching { reconfigureSession(sessionId) }
                    .onFailure { logError(TAG, "deferred reconfigure failed session=$sessionId", it) }
            }
        }
    }

    private suspend fun runTurn(
        handle: RuntimeHandle,
        runId: String,
        text: String,
        images: List<ImagePart> = emptyList(),
        setTitle: Boolean = true,
    ) {
        try {
            if (setTitle) ensureTitle(handle, text)
            val prompt = if (handle.state.turnSource == TurnSource.USER) text else ""
            emit(handle, RuntimeEvent.RunStarted(runId, System.currentTimeMillis(), prompt))
            emitActivity(handle, runId, RuntimeActivity.Thinking)
            logInfo(TAG, "run session=${handle.session.sessionId} run=$runId chars=${text.length}")
            handle.session.prompt(text, images)
            if (!handle.state.stopRequested) {
                val status = if (handle.state.terminalEmitted) RunStatus.FAILED else finishRun(handle, runId)
                maybeContinueGoal(handle, status)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (!handle.state.stopRequested) {
                emitTerminal(
                    handle = handle,
                    runId = runId,
                    text = handle.session.lastAssistantText().orEmpty(),
                    status = RunStatus.FAILED,
                    message = error.message,
                )
                maybeContinueGoal(handle, RunStatus.FAILED)
            }
        } finally {
            release(handle, runId)
        }
    }

    override fun stop(sessionId: String): List<String> {
        val handle = handles[sessionId] ?: return emptyList()
        val droppedInputs = clearQueuedInputs(handle)
        val stopped = commands.stop(sessionId) { stop ->
            val text = runCatching { handle.session.lastAssistantText().orEmpty() }.getOrDefault("")
            emit(
                handle,
                RuntimeEvent.RunFinished(
                    runId = stop.runId,
                    text = text,
                    status = RunStatus.STOPPED,
                    finishedAt = System.currentTimeMillis(),
                ),
            )
        } ?: return droppedInputs
        forgetRunRecord(stopped.runId)
        handle.session.abort()
        goalDriver?.let { driver -> backgroundScope.launch { driver.pauseOnStop(sessionId) } }
        return droppedInputs
    }

    override suspend fun history(sessionId: String): List<AssistantMessage> {
        val loaded = mutex.withLock {
            handles[sessionId]?.let { handle -> handle to handle.session.history() }
        } ?: return emptyList()
        emitUsage(loaded.first)
        return loaded.second
    }

    override suspend fun sessionTitle(sessionId: String): String? = mutex.withLock {
        handles[sessionId]?.session?.sessionName()
            ?: catalog.find(visibleCwds(), sessionId)?.title
    }

    override suspend fun sessions(workspaceId: String): List<ConversationSummary> {
        val workspace = workspaceFor(workspaceId)
        return catalog.list(workspace.cwd).filterNot { it.archived }
    }

    override suspend fun archivedSessions(): List<ConversationSummary> =
        catalog.listAll(visibleCwds()).filter { it.archived }

    override suspend fun renameSession(id: String, title: String) {
        catalog.rename(id, title)
        emitListChanged()
    }

    override suspend fun setSessionPinned(id: String, pinned: Boolean) {
        catalog.setPinned(id, pinned)
        emitListChanged()
    }

    override suspend fun setSessionArchived(id: String, archived: Boolean) {
        catalog.setArchived(id, archived)
        if (archived) sessionArchived?.invoke(id)
        eventsBuffer.emit(SessionRuntimeEvent(id, RuntimeEvent.SessionArchived(archived = archived)))
        emitListChanged()
    }

    override suspend fun deleteSession(id: String) {
        val summary = catalog.find(visibleCwds(), id)
        mutex.withLock { detach(id, forget = true) }
        sessionCleaner?.invoke(id)
        if (summary != null) {
            catalog.delete(summary.filePath)
            catalog.removeMeta(id)
        }
        eventsBuffer.emit(SessionRuntimeEvent(id, RuntimeEvent.SessionRemoved()))
        emitListChanged()
    }

    override suspend fun sessionModel(sessionId: String): SessionModelSnapshot? = mutex.withLock {
        val handle = handles[sessionId] ?: return@withLock null
        runCatching {
            SessionModelSnapshot(
                providerId = handle.session.model.provider,
                modelId = handle.session.model.id,
                thinkingLevel = thinkingLevelOf(handle.session.thinkingLevel),
            )
        }.getOrNull()
    }

    override suspend fun setSessionModel(
        sessionId: String,
        providerId: String,
        modelId: String,
        thinkingLevel: ThinkingLevel,
    ) {
        val handle = mutex.withLock { handles[sessionId] } ?: return
        val config = providers.resolveConfig(ModelConfig(providerId, modelId, thinkingLevel))
        val model = providers.sdkModel(config.providerId, config.modelId)
            ?: error("Model not found: ${config.providerId}/${config.modelId}")
        handle.session.setModel(model, config.thinkingLevel.runtimeValue())
        emitUsage(handle)
    }

    override suspend fun setSessionThinkingLevel(sessionId: String, thinkingLevel: ThinkingLevel) {
        val handle = mutex.withLock { handles[sessionId] } ?: return
        handle.session.setThinkingLevel(thinkingLevel.runtimeValue())
    }

    override suspend fun currentConfig(): ModelConfig = modelSettings.read()

    override suspend fun usage(sessionId: String): SessionUsage? {
        val handle = mutex.withLock { handles[sessionId] } ?: return null
        return handle.session.usage()
    }

    override suspend fun compact(sessionId: String): CompactionOutcome {
        val handle = mutex.withLock { handles[sessionId] } ?: return CompactionOutcome.Unavailable
        if (handle.state.busy || handle.session.isStreaming) {
            emitUsage(handle)
            return CompactionOutcome.Busy
        }
        val outcome = try {
            val result = handle.session.compact()
            if (result == null) {
                CompactionOutcome.Empty
            } else {
                CompactionOutcome.Done(result.tokensBefore, result.estimatedTokensAfter)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            CompactionOutcome.Failed(error.message)
        }
        emitUsage(handle)
        return outcome
    }

    internal suspend fun emitUsage(handle: RuntimeHandle, runId: String = handle.state.runId.orEmpty()) {
        val current = usage(handle.session.sessionId) ?: return
        emit(handle, UsageChanged(runId, current))
    }

    suspend fun reconfigure() = withContext(Dispatchers.IO) {
        val sessionIds = mutex.withLock { handles.keys.toList() }
        for (sessionId in sessionIds) {
            runCatching { reconfigureSession(sessionId) }
                .onFailure { logError(TAG, "reconfigure failed session=$sessionId", it) }
        }
        emitListChanged()
    }

    private suspend fun reconfigureSession(sessionId: String) {
        while (true) {
            val token = commands.claimReconfigure(sessionId)
            if (token == 0L) return
            var again = false
            var reattached = false
            try {
                mutex.withLock {
                    if (!commands.ownsReconfigure(sessionId, token)) return@withLock
                    val handle = handles[sessionId] ?: return@withLock
                    if (!isPersistedSession(handle)) return@withLock
                    val cwd = handle.cwd
                    val file = handle.session.sessionFile
                    val selected = lastOpenedSessionId == sessionId
                    val lastActiveAt = handle.lastActiveAt
                    detach(sessionId, keepReconfigure = true)
                    val reopened = attach(cwd, file)
                    reopened.lastActiveAt = lastActiveAt
                    if (selected) lastOpenedSessionId = reopened.session.sessionId
                    reattached = true
                }
            } finally {
                again = commands.releaseReconfigure(sessionId, token)
            }
            if (reattached) sweepIdleHandles()
            if (!again) return
        }
    }

    private fun isPersistedSession(handle: RuntimeHandle): Boolean {
        val path = handle.session.sessionFile ?: return false
        val file = File(path)
        return file.isFile && file.length() > 0L
    }

    private fun homeWorkspace(): RuntimeWorkspace = RuntimeWorkspace(
        id = HOME_ID,
        name = HOME_NAME,
        cwd = homeDir.absolutePath,
        isHome = true,
    )

    private suspend fun workspaceFor(workspaceId: String): RuntimeWorkspace {
        if (workspaceId == HOME_ID || workspaceId.isBlank()) return homeWorkspace()
        return workspaces.read().firstOrNull { it.id == workspaceId } ?: homeWorkspace()
    }

    private suspend fun visibleCwds(): List<String> =
        listOf(homeDir.absolutePath) + workspaces.read().map { it.cwd }

    private suspend fun attach(cwd: String, file: String?): RuntimeHandle {
        val existing = handles.values.firstOrNull { it.cwd == cwd && it.session.sessionFile == file }
        if (existing != null && file != null) return existing
        val engine = engineFactory(File(cwd))
        val config = modelSettings.read()
        val session = engine.create(file, config)
        val handle = RuntimeHandle(
            engine = engine,
            session = session,
            cwd = cwd,
            state = commands.attach(session.sessionId),
        )
        handles[session.sessionId] = handle
        handle.unsubscribe = session.subscribe { event -> onSessionEvent(handle, event) }
        mcp.logState(handle, "attach")
        return handle
    }

    private suspend fun detach(
        sessionId: String,
        forget: Boolean = false,
        keepReconfigure: Boolean = false,
    ) {
        val handle = detachLocked(sessionId, forget, keepReconfigure) ?: return
        closeHandle(handle)
        publishBusy()
        activeRunState.update { it - sessionId }
    }

    private fun detachLocked(
        sessionId: String,
        forget: Boolean = false,
        keepReconfigure: Boolean = false,
    ): RuntimeHandle? {
        if (lastOpenedSessionId == sessionId) lastOpenedSessionId = null
        val handle = handles.remove(sessionId)
        commands.detach(sessionId, keepReconfigure)
        if (forget) commands.forget(sessionId)
        return handle
    }

    private suspend fun closeHandle(handle: RuntimeHandle) {
        handle.unsubscribe?.invoke()
        handle.unsubscribe = null
        handle.session.close()
    }

    internal suspend fun sweepIdleHandles() {
        val victims = mutex.withLock { evictionVictimsLocked() }
        for (sessionId in victims) {
            val gate = CompletableDeferred<Unit>()
            val handle = mutex.withLock {
                val current = handles[sessionId] ?: return@withLock null
                if (!evictableLocked(sessionId, current)) return@withLock null
                val removed = detachLocked(sessionId) ?: return@withLock null
                closingSessions[sessionId] = gate
                removed.session.sessionFile?.let { closingFiles[it] = gate }
                removed
            } ?: continue
            val file = handle.session.sessionFile
            try {
                closeHandle(handle)
            } finally {
                closingSessions.remove(sessionId)
                if (file != null) closingFiles.remove(file)
                gate.complete(Unit)
                publishBusy()
                activeRunState.update { it - sessionId }
            }
        }
    }

    private fun evictionVictimsLocked(): List<String> {
        if (handles.size <= HandleBudget.MAX_HANDLES) return emptyList()
        val now = System.currentTimeMillis()
        val entries = handles.map { (sessionId, handle) -> budgetEntryLocked(sessionId, handle, now) }
        return HandleBudget.decideEvictions(entries, handles.size, now)
    }

    private fun evictableLocked(sessionId: String, handle: RuntimeHandle): Boolean {
        val entry = budgetEntryLocked(sessionId, handle, System.currentTimeMillis())
        if (entry.busy || entry.reconfiguring || entry.keepAlive || entry.selected) return false
        return System.currentTimeMillis() - handle.lastActiveAt > HandleBudget.IDLE_TTL_MS
    }

    private fun budgetEntryLocked(sessionId: String, handle: RuntimeHandle, now: Long): HandleBudget.Entry {
        val snapshot = commands.snapshot(sessionId)
        return HandleBudget.Entry(
            sessionId = sessionId,
            lastActiveAt = handle.lastActiveAt,
            busy = snapshot?.busy == true || snapshot?.runId != null,
            reconfiguring = snapshot?.reconfiguring == true,
            keepAlive = keepAliveSafely(sessionId),
            selected = sessionId == lastOpenedSessionId,
        )
    }

    private fun keepAliveSafely(sessionId: String): Boolean =
        try {
            keepHandleAlive(sessionId)
        } catch (_: Exception) {
            true
        }

    private fun publishBusy() {
        busyState.value = commands.busySessionIds()
    }

    private suspend fun ensureTitle(handle: RuntimeHandle, text: String) {
        if (handle.session.sessionName() != null) return
        handle.session.appendTitle(handle.engine.titleFor(text))
        emitListChanged()
    }

    private fun emit(handle: RuntimeHandle, event: RuntimeEvent) {
        val scoped = SessionRuntimeEvent(handle.session.sessionId, event)
        if (eventsBuffer.tryEmit(scoped)) return
        if (event is RuntimeEvent.TextDelta || event is RuntimeEvent.ReasoningDelta) {
            logWarn(TAG, "drop delta: event buffer full session=${scoped.sessionId} run=${event.runId}")
            return
        }
        logWarn(TAG, "event buffer full, deferring ${event::class.simpleName} session=${scoped.sessionId}")
        backgroundScope.launch { eventsBuffer.emit(scoped) }
    }

    private fun emitActivity(handle: RuntimeHandle, runId: String, activity: RuntimeActivity?) {
        if (handle.activity == activity) return
        handle.activity = activity
        emit(handle, RuntimeEvent.ActivityChanged(runId, activity))
    }

    private suspend fun emitListChanged() {
        eventsBuffer.emit(SessionRuntimeEvent("", RuntimeEvent.SessionListChanged()))
    }

    private fun admitQueuedInput(handle: RuntimeHandle, runId: String, message: AgentMessage) {
        val text = message.userText() ?: return
        handle.pending.admit(text) ?: return
        emit(handle, RuntimeEvent.UserInputAdmitted(runId, text))
        emitQueueChanged(handle, runId)
    }

    private fun emitQueueChanged(handle: RuntimeHandle, runId: String) {
        val snapshot = handle.pending.snapshot()
        emit(handle, RuntimeEvent.QueueChanged(runId, snapshot.steering, snapshot.followUp))
    }

    private fun dropPendingInputs(handle: RuntimeHandle, runId: String = handle.state.runId.orEmpty()): List<String> {
        val dropped = handle.pending.drain()
        if (dropped.isEmpty()) return emptyList()
        runCatching { handle.session.clearQueues() }
        emitQueueChanged(handle, runId)
        return dropped.map { it.text }
    }

    private fun clearQueuedInputs(handle: RuntimeHandle): List<String> {
        val dropped = handle.pending.drain()
        runCatching { handle.session.clearQueues() }
        if (dropped.isNotEmpty()) emitQueueChanged(handle, handle.state.runId.orEmpty())
        return dropped.map { it.text }
    }

    private fun publishActiveRun(handle: RuntimeHandle) {
        val sessionId = handle.session.sessionId
        val snapshot = commands.snapshot(sessionId)
        activeRunState.update { current ->
            val runId = snapshot?.runId
            if (runId == null) {
                current - sessionId
            } else {
                current + (sessionId to ActiveRunSnapshot(runId, snapshot.runStartedAt))
            }
        }
    }

    private suspend fun finishRun(handle: RuntimeHandle, runId: String): RunStatus {
        val last = handle.session.messages.asReversed().firstNotNullOfOrNull { it.asAssistantMessage() }
        val status = when (last?.stopReason) {
            StopReason.Error -> RunStatus.FAILED
            StopReason.Aborted -> RunStatus.STOPPED
            else -> RunStatus.DONE
        }
        emitTerminal(
            handle = handle,
            runId = runId,
            text = handle.session.lastAssistantText().orEmpty(),
            status = status,
            message = last?.errorMessage,
        )
        return status
    }

    private suspend fun maybeContinueGoal(handle: RuntimeHandle, status: RunStatus) {
        if (handle.state.stopRequested) return
        val driver = goalDriver ?: return
        val sessionId = handle.session.sessionId
        val epoch = commands.snapshot(sessionId)?.epoch ?: return
        val source = handle.state.turnSource
        val round = driver.nextRound(sessionId, source, status == RunStatus.DONE) ?: return
        backgroundScope.launch {
            delay(GOAL_CONTINUE_DELAY_MS)
            val start = commands.beginIfEpoch(sessionId, TurnSource.GOAL, epoch) ?: return@launch
            afterBegin(handle, TurnSource.GOAL)
            val committed = try {
                driver.commitRound(sessionId, round.goalId, round.revision)
            } catch (cancelled: CancellationException) {
                release(handle, start.runId)
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (committed == null || handle.state.stopRequested) {
                release(handle, start.runId)
                return@launch
            }
            emit(
                handle,
                BackgroundTurnStarted(
                    start.runId,
                    TurnNotice.GoalRound(committed.round, committed.maxRounds, committed.objective),
                ),
            )
            runTurn(handle, start.runId, committed.prompt, setTitle = false)
        }
    }

    private suspend fun emitTerminal(
        handle: RuntimeHandle,
        runId: String,
        text: String,
        status: RunStatus,
        message: String? = null,
    ) {
        if (!commands.claimTerminal(handle.session.sessionId, runId)) return
        if (status == RunStatus.FAILED) {
            emit(handle, RuntimeEvent.RunFailed(runId, message.orEmpty()))
        } else {
            emit(handle, RuntimeEvent.RunFinished(runId, text, status, System.currentTimeMillis()))
        }
        emitUsage(handle, runId)
    }

    suspend fun postPluginMessage(
        sessionId: String,
        customType: String,
        payloadJson: String,
        text: String,
        display: Boolean = true,
    ): Boolean {
        val handle = mutex.withLock { handles[sessionId] } ?: return false
        return runCatching {
            handle.session.appendCustomMessage(customType, payloadJson, text, display)
            emit(
                handle,
                RuntimeEvent.PluginCustomMessage(
                    runId = handle.state.runId.orEmpty(),
                    customType = customType,
                    payload = payloadJson,
                    text = text,
                    display = display,
                ),
            )
            true
        }.getOrDefault(false)
    }

    private suspend fun onSessionEvent(handle: RuntimeHandle, event: SessionEvent) {
        sdkEventsBuffer.tryEmit(SessionSdkEvent(handle.session.sessionId, event))
        val runId = handle.state.runId.orEmpty()
        val active = runId.isNotEmpty()
        when (event) {
            is SessionEvent.AgentEventReceived -> if (active) onAgentEvent(handle, runId, event.event)
            is SessionEvent.RetryScheduled -> if (active) {
                emitActivity(handle, runId, RuntimeActivity.Retrying(event.attempt))
            }

            is SessionEvent.RetrySucceeded -> if (active) {
                emitActivity(handle, runId, RuntimeActivity.Thinking)
            }

            is SessionEvent.RetryExhausted -> if (active) {
                emitTerminal(
                    handle = handle,
                    runId = runId,
                    text = handle.session.lastAssistantText().orEmpty(),
                    status = RunStatus.FAILED,
                    message = event.errorMessage.orEmpty(),
                )
            }

            is SessionEvent.CompactionStarted -> if (active) {
                emitActivity(handle, runId, RuntimeActivity.Compacting)
            }

            is SessionEvent.CompactionFinished -> {
                if (active) emitActivity(handle, runId, RuntimeActivity.Thinking)
                emit(handle, NoticeEvent(runId, TurnNotice.Compacted(event.tokensBefore, event.tokensAfter)))
                emitUsage(handle, runId)
            }

            is SessionEvent.CompactionFailed -> if (active) {
                emitActivity(handle, runId, RuntimeActivity.CompactionFailed)
            }

            is SessionEvent.SessionError -> if (active) {
                emitTerminal(
                    handle = handle,
                    runId = runId,
                    text = handle.session.lastAssistantText().orEmpty(),
                    status = RunStatus.FAILED,
                    message = event.message,
                )
            }

            is SessionEvent.McpServerStatusChanged -> logInfo(
                TAG,
                "mcp status ${event.name}=${event.status.state.name}/${event.status.toolCount}/${event.status.lastError.orEmpty()}",
            )

            else -> Unit
        }
    }

    private suspend fun onAgentEvent(handle: RuntimeHandle, runId: String, event: AgentEvent) {
        when (event) {
            is AgentEvent.MessageStart -> {
                admitQueuedInput(handle, runId, event.message)
                emitActivity(handle, runId, RuntimeActivity.Thinking)
            }

            is AgentEvent.MessageUpdate -> when (val update = event.assistantMessageEvent) {
                is TextDeltaEvent -> {
                    emit(handle, RuntimeEvent.TextDelta(runId, update.delta))
                    emitActivity(handle, runId, RuntimeActivity.Thinking)
                }

                is ThinkingDeltaEvent -> {
                    emit(handle, RuntimeEvent.ReasoningDelta(runId, update.delta))
                    emitActivity(handle, runId, RuntimeActivity.Thinking)
                }

                else -> Unit
            }

            is AgentEvent.ToolExecutionStart -> {
                emit(
                    handle,
                    RuntimeEvent.ToolStarted(
                        runId = runId,
                        callId = event.toolCallId,
                        name = event.toolName,
                        arguments = event.args.toDisplayArguments(),
                        startedAt = System.currentTimeMillis(),
                        rawArguments = event.args,
                    ),
                )
                emitActivity(handle, runId, RuntimeActivity.RunningTool(event.toolName))
            }

            is AgentEvent.ToolExecutionEnd -> {
                val payload = event.result.toToolResultPayload()
                emit(
                    handle,
                    RuntimeEvent.ToolFinished(
                        runId = runId,
                        callId = event.toolCallId,
                        name = event.toolName,
                        result = payload.text(),
                        isError = event.isError,
                        durationMs = event.durationMs,
                        payload = payload,
                    ),
                )
                emitActivity(handle, runId, RuntimeActivity.Thinking)
                emitUsage(handle, runId)
            }

            else -> Unit
        }
    }

    companion object {
        const val HOME_ID = "home"
        const val HOME_NAME = "Home"

        private const val GOAL_CONTINUE_DELAY_MS = 400L
    }
}

private class CatalogSessionDirectory(
    private val catalog: SessionCatalog,
) : SessionDirectory {
    override suspend fun list(cwd: String): List<ConversationSummary> = catalog.list(cwd)

    override suspend fun listAll(cwds: List<String>): List<ConversationSummary> = catalog.listAll(cwds)

    override suspend fun find(cwds: List<String>, sessionId: String): ConversationSummary? = catalog.find(cwds, sessionId)

    override suspend fun delete(filePath: String) = catalog.delete(filePath)

    override suspend fun rename(id: String, title: String) = catalog.rename(id, title)

    override suspend fun setPinned(id: String, pinned: Boolean) = catalog.setPinned(id, pinned)

    override suspend fun setArchived(id: String, archived: Boolean) = catalog.setArchived(id, archived)

    override suspend fun removeMeta(id: String) = catalog.removeMeta(id)
}

private class WorkspaceStoreDirectory(
    private val store: WorkspaceStore,
) : WorkspaceDirectory {
    override suspend fun read(): List<RuntimeWorkspace> = store.read()

    override suspend fun add(workspace: RuntimeWorkspace) = store.add(workspace)

    override suspend fun remove(id: String) = store.remove(id)
}

internal const val TAG = "DeepDeepRuntime"

private fun AgentMessage.userText(): String? {
    val message = asLlmMessage() as? UserMessage ?: return null
    return when (val content = message.content) {
        is PlainTextContent -> content.text
        is BlockContent -> content.blocks.filterIsInstance<TextContent>().joinToString("") { it.text }
    }
}

private fun InputQueueKind.behavior(): QueueBehavior = when (this) {
    InputQueueKind.STEER -> QueueBehavior.Steer
    InputQueueKind.FOLLOW_UP -> QueueBehavior.FollowUp
}

data class SessionSdkEvent(
    val sessionId: String,
    val event: dev.pi.coding.session.SessionEvent,
)
