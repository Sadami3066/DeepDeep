package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.sdk.coding.session.SessionEvent
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val titleNamer: SessionTitleNamer? = null,
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
        titleNamer: SessionTitleNamer? = null,
        sessionCleaner: ((String) -> Unit)? = null,
        sessionArchived: ((String) -> Unit)? = null,
        runLedger: RunLedger = RunLedger(null),
        keepHandleAlive: (String) -> Boolean = { true },
    ) : this(
        homeDir, workspacesDir, { cwd -> engineFactory(cwd) }, modelConfigStore,
        CatalogSessionDirectory(catalog), WorkspaceStoreDirectory(workspaceStore),
        providers, backgroundScope, goalDriver, titleNamer, sessionCleaner, sessionArchived, runLedger, keepHandleAlive,
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

    private val sessionClosureBuffer = MutableSharedFlow<SessionClosure>(extraBufferCapacity = 32)
    val sessionClosures: SharedFlow<SessionClosure> = sessionClosureBuffer.asSharedFlow()

    private val busyState = MutableStateFlow<Set<String>>(emptySet())
    override val busySessionIds: StateFlow<Set<String>> = busyState.asStateFlow()

    private val activeRunState = MutableStateFlow<Map<String, ActiveRunSnapshot>>(emptyMap())
    override val activeRuns: StateFlow<Map<String, ActiveRunSnapshot>> = activeRunState.asStateFlow()

    override val interruptedRuns: StateFlow<List<InterruptedRun>> = runLedger.interruptedRuns

    private val mutex = Mutex()
    private val commands = SessionCommandPlane()
    private val handles = ConcurrentHashMap<String, RuntimeHandle>()
    private val mcp = RuntimeMcp(mutex, handles)
    private val runs: RuntimeRuns = RuntimeRuns(
        mutex = mutex,
        handles = handles,
        commands = commands,
        eventsBuffer = eventsBuffer,
        busyState = busyState,
        activeRunState = activeRunState,
        backgroundScope = backgroundScope,
        titleNamer = titleNamer,
        runLedger = runLedger,
        goalDriver = goalDriver,
        handleFor = ::handleFor,
        sweepIdleHandles = ::sweepIdleHandles,
        reconfigureSession = ::reconfigureSession,
    )
    private val translator: RuntimeEventTranslator = RuntimeEventTranslator(
        sdkEvents = sdkEventsBuffer,
        emit = runs::emit,
        emitActivity = runs::emitActivity,
        emitTerminal = runs::emitTerminal,
        emitUsage = runs::emitUsage,
        admitQueuedInput = runs::admitQueuedInput,
    )
    private val pool: RuntimePool = RuntimePool(
        mutex = mutex,
        handles = handles,
        commands = commands,
        catalog = catalog,
        visibleCwds = ::visibleCwds,
        engineFactory = engineFactory,
        modelSettings = modelSettings,
        keepHandleAlive = keepHandleAlive,
        translator = translator,
        mcp = mcp,
        publishBusy = runs::publishBusy,
        publishActiveRun = runs::publishActiveRun,
        clearActiveRun = runs::clearActiveRun,
        emitListChanged = runs::emitListChanged,
        onSessionClosed = { sessionId, reason ->
            sessionClosureBuffer.tryEmit(SessionClosure(sessionId, reason))
        },
    )
    init {
        backgroundScope.launch {
            while (isActive) {
                delay(HandleBudget.SWEEP_INTERVAL_MS)
                try {
                    sweepIdleHandles()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    logWarn(TAG, "idle handle sweep failed", error)
                }
            }
        }
    }
    private val workspaceAdmin = RuntimeWorkspaces(
        mutex = mutex,
        homeDir = homeDir,
        workspacesDir = workspacesDir,
        directories = workspaces,
        catalog = catalog,
        emitListChanged = runs::emitListChanged,
        emitRemoved = runs::emitSessionRemoved,
        stopTurn = runs::stop,
        detachForget = pool::detachForget,
        sessionCleaner = sessionCleaner,
    )
    private val models = RuntimeModels(
        mutex = mutex,
        handles = handles,
        modelSettings = modelSettings,
        providers = providers,
        emit = runs::emit,
    )
    private val sessionsAdmin = RuntimeSessions(
        mutex = mutex,
        handles = handles,
        catalog = catalog,
        visibleCwds = ::visibleCwds,
        workspaceFor = ::workspaceFor,
        emitListChanged = runs::emitListChanged,
        emitSessionRemoved = runs::emitSessionRemoved,
        detachForget = pool::detachForget,
        sessionCleaner = sessionCleaner,
        sessionArchived = sessionArchived,
    )
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

    override suspend fun workspaces(): List<RuntimeWorkspace> = workspaceAdmin.list()

    override suspend fun createWorkspace(name: String, directory: String?): RuntimeWorkspace =
        workspaceAdmin.create(name, directory)

    override suspend fun renameWorkspace(id: String, name: String) = workspaceAdmin.rename(id, name)

    override suspend fun deleteWorkspace(id: String) = workspaceAdmin.delete(id)

    override suspend fun allSessions(): List<ConversationSummary> = sessionsAdmin.all()

    suspend fun refreshSessions() {
        allSessions()
        runs.emitListChanged()
    }

    override suspend fun workspaceSessionCount(workspaceId: String): Int =
        sessionsAdmin.workspaceCount(workspaceId)

    override suspend fun newSession(workspaceId: String): OpenedSession =
        pool.newSession(workspaceFor(workspaceId).cwd)

    override suspend fun openSession(filePath: String, cwd: String): OpenedSession =
        pool.openSession(filePath, cwd)

    override suspend fun closeSession(sessionId: String) = pool.close(sessionId)

    override suspend fun reloadSession(sessionId: String): String = pool.reload(sessionId)

    suspend fun reloadMcp() = mcp.reload()

    suspend fun reconnectMcp(server: String) = mcp.reconnect(server)

    override suspend fun send(sessionId: String, text: String, images: List<ImagePart>) =
        runs.send(sessionId, text, images)

    override suspend fun steer(sessionId: String, text: String, images: List<ImagePart>) =
        runs.steer(sessionId, text, images)

    override suspend fun followUp(sessionId: String, text: String, images: List<ImagePart>) =
        runs.followUp(sessionId, text, images)

    override fun queuedInputs(sessionId: String): List<QueuedInput> = runs.queuedInputs(sessionId)

    override fun clearQueuedInputs(sessionId: String): List<String> = runs.clearQueuedInputs(sessionId)

    override fun removeQueuedInput(sessionId: String, kind: InputQueueKind, text: String): Boolean =
        runs.removeQueuedInput(sessionId, kind, text)

    override suspend fun retryLastTurn(sessionId: String) = runs.retryLastTurn(sessionId)

    override suspend fun deliverReminder(sessionId: String, text: String, reminder: String?) =
        runs.deliverReminder(sessionId, text, reminder)

    override suspend fun deliverPluginTurn(sessionId: String, text: String, followUp: Boolean): PluginTurnResult =
        runs.deliverPluginTurn(sessionId, text, followUp)

    suspend fun deliverBackgroundTurn(
        sessionId: String,
        notice: TurnNotice?,
        prompt: String,
        source: TurnSource,
    ) = runs.deliverBackgroundTurn(sessionId, notice, prompt, source)

    override suspend fun activeRunId(sessionId: String): String? = runs.activeRunId(sessionId)

    fun activeTurnSource(sessionId: String): TurnSource? = runs.activeTurnSource(sessionId)

    override fun stop(sessionId: String): List<String> = runs.stop(sessionId)

    override suspend fun history(sessionId: String): List<AssistantMessage> = models.history(sessionId)

    override suspend fun sessionTitle(sessionId: String): String? = sessionsAdmin.title(sessionId)

    override suspend fun sessions(workspaceId: String): List<ConversationSummary> =
        sessionsAdmin.list(workspaceId)

    override suspend fun archivedSessions(): List<ConversationSummary> = sessionsAdmin.archived()

    override suspend fun renameSession(id: String, title: String) = sessionsAdmin.rename(id, title)

    override suspend fun setSessionPinned(id: String, pinned: Boolean) = sessionsAdmin.setPinned(id, pinned)

    override suspend fun setSessionArchived(id: String, archived: Boolean) =
        sessionsAdmin.setArchived(id, archived)

    override suspend fun deleteSession(id: String) = sessionsAdmin.delete(id)

    override suspend fun sessionModel(sessionId: String): SessionModelSnapshot? = models.session(sessionId)

    override suspend fun setSessionModel(
        sessionId: String,
        providerId: String,
        modelId: String,
        thinkingLevel: ThinkingLevel,
    ) = models.setSession(sessionId, providerId, modelId, thinkingLevel)

    override suspend fun setSessionThinkingLevel(sessionId: String, thinkingLevel: ThinkingLevel) =
        models.setThinking(sessionId, thinkingLevel)

    override suspend fun currentConfig(): ModelConfig = models.currentConfig()

    override suspend fun usage(sessionId: String): SessionUsage? = models.usage(sessionId)

    override suspend fun compact(sessionId: String): CompactionOutcome = models.compact(sessionId)

    suspend fun reconfigure() = pool.reconfigureAll()

    suspend fun postPluginMessage(
        sessionId: String,
        customType: String,
        payloadJson: String,
        text: String,
        display: Boolean = true,
    ): Boolean = runs.postPluginMessage(sessionId, customType, payloadJson, text, display)

    private suspend fun handleFor(sessionId: String): RuntimeHandle = pool.handleFor(sessionId)

    private suspend fun sweepIdleHandles() = pool.sweepIdleHandles()

    private suspend fun reconfigureSession(sessionId: String) = pool.reconfigureSession(sessionId)

    private suspend fun workspaceFor(workspaceId: String): RuntimeWorkspace = workspaceAdmin.find(workspaceId)

    private suspend fun visibleCwds(): List<String> = workspaceAdmin.visibleCwds()

    companion object {
        const val HOME_ID = "home"
        const val HOME_NAME = "Home"
    }
}

internal const val TAG = "DeepDeepRuntime"

data class SessionSdkEvent(
    val sessionId: String,
    val event: SessionEvent,
)
