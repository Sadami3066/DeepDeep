package io.github.whywood.deepdeep.runtime

import android.util.Log
import dev.pi.agent.AgentEvent
import dev.pi.agent.asAssistantMessage
import dev.pi.ai.StopReason
import dev.pi.ai.TextContent
import dev.pi.ai.TextDeltaEvent
import dev.pi.ai.ThinkingDeltaEvent
import dev.pi.coding.compaction.estimateContextTokens
import dev.pi.coding.session.AgentSession
import dev.pi.coding.session.SessionEvent
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Path.Companion.toPath

class AgentRuntimeHost(
    private val homeDir: File,
    private val workspacesDir: File,
    private val engineFactory: suspend (File) -> PiSessionEngine,
    private val modelConfigStore: ModelConfigStore,
    private val catalog: SessionCatalog,
    private val workspaceStore: WorkspaceStore,
    private val providers: ProviderCatalog,
    private val backgroundScope: CoroutineScope,
    private val goalDriver: GoalDriver? = null,
    private val sessionCleaner: ((String) -> Unit)? = null,
    private val sessionArchived: ((String) -> Unit)? = null,
) : AgentRuntime {
    internal class Handle(
        val engine: PiSessionEngine,
        val session: AgentSession,
        val cwd: String,
    ) {
        var unsubscribe: (() -> Unit)? = null
        var runId: String? = null
        var stopRequested = false
        var terminalEmitted = false
        var busy = false
        var turnSource: TurnSource = TurnSource.USER
    }

    private val eventsBuffer = MutableSharedFlow<SessionRuntimeEvent>(
        extraBufferCapacity = 512,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )
    override val events: SharedFlow<SessionRuntimeEvent> = eventsBuffer.asSharedFlow()

    private val busyState = MutableStateFlow<Set<String>>(emptySet())
    override val busySessionIds: StateFlow<Set<String>> = busyState.asStateFlow()

    private val mutex = Mutex()
    private val handles = mutableMapOf<String, Handle>()
    private var started = false

    override suspend fun start() = mutex.withLock {
        if (started) return@withLock
        homeDir.mkdirs()
        workspacesDir.mkdirs()
        started = true
    }

    override suspend fun ensureStarted() {
        if (!started) start()
    }

    override suspend fun workspaces(): List<RuntimeWorkspace> = buildList {
        add(homeWorkspace())
        addAll(workspaceStore.read().filter { it.id != HOME_ID })
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
        workspaceStore.add(workspace)
        eventsBuffer.emit(SessionRuntimeEvent("", RuntimeEvent.SessionListChanged()))
        workspace
    }

    override suspend fun renameWorkspace(id: String, name: String) {
        if (id == HOME_ID) return
        val workspace = workspaceStore.read().firstOrNull { it.id == id } ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        workspaceStore.add(workspace.copy(name = trimmed))
        eventsBuffer.emit(SessionRuntimeEvent("", RuntimeEvent.SessionListChanged()))
    }

    override suspend fun deleteWorkspace(id: String) {
        if (id == HOME_ID) return
        val workspace = workspaceStore.read().firstOrNull { it.id == id } ?: return
        val sessions = catalog.list(workspace.cwd)
        sessions.forEach { session ->
            runCatching { stop(session.id) }
            mutex.withLock { detach(session.id) }
            sessionCleaner?.invoke(session.id)
        }
        sessions.forEach { session ->
            catalog.delete(session.filePath)
            catalog.removeMeta(session.id)
            eventsBuffer.emit(SessionRuntimeEvent(session.id, RuntimeEvent.SessionRemoved()))
        }
        runCatching { File(workspace.cwd).deleteRecursively() }
        workspaceStore.remove(id)
        eventsBuffer.emit(SessionRuntimeEvent("", RuntimeEvent.SessionListChanged()))
    }

    override suspend fun allSessions(): List<ConversationSummary> =
        catalog.listAll(visibleCwds()).filterNot { it.archived }

    override suspend fun workspaceSessionCount(workspaceId: String): Int =
        catalog.list(workspaceFor(workspaceId).cwd).size

    override suspend fun newSession(workspaceId: String): OpenedSession = mutex.withLock {
        val workspace = workspaceFor(workspaceId)
        val handle = attach(workspace.cwd, null)
        eventsBuffer.emit(SessionRuntimeEvent("", RuntimeEvent.SessionListChanged()))
        OpenedSession(handle.session.sessionId, handle.session.sessionFile?.toString(), workspace.cwd)
    }

    override suspend fun openSession(filePath: String, cwd: String): OpenedSession = mutex.withLock {
        handles.values.firstOrNull { it.session.sessionFile?.toString() == filePath }?.let { existing ->
            OpenedSession(existing.session.sessionId, filePath, existing.cwd)
        } ?: run {
            val handle = attach(cwd, filePath)
            OpenedSession(handle.session.sessionId, handle.session.sessionFile?.toString(), cwd)
        }
    }

    override suspend fun closeSession(sessionId: String) = mutex.withLock {
        detach(sessionId)
    }

    override suspend fun reloadSession(sessionId: String): String = mutex.withLock {
        val handle = handles[sessionId] ?: error("Agent session is not ready")
        check(!handle.busy) { "Agent session is busy" }
        val file = handle.session.sessionFile?.toString() ?: error("Agent session has no file")
        val cwd = handle.cwd
        detach(sessionId)
        val reopened = attach(cwd, file)
        eventsBuffer.emit(SessionRuntimeEvent("", RuntimeEvent.SessionListChanged()))
        reopened.session.sessionId
    }

    suspend fun reloadMcp() {
        val targets = mutex.withLock { handles.values.toList() }
        for (handle in targets) {
            runCatching { handle.session.mcp.reload(handle.engine.loadMcpServers()) }
        }
    }

    suspend fun reconnectMcp(server: String) {
        val targets = mutex.withLock { handles.values.toList() }
        for (handle in targets) {
            runCatching { handle.session.mcp.reconnect(server) }
        }
    }

    override suspend fun send(sessionId: String, text: String) {
        val handle = mutex.withLock { handles[sessionId] } ?: error("Agent session is not ready")
        runTurn(handle, beginRun(handle, TurnSource.USER), text)
    }

    override suspend fun retryLastTurn(sessionId: String) {
        val handle = mutex.withLock { handles[sessionId] } ?: error("Agent session is not ready")
        check(!handle.busy) { "Agent session is busy" }
        if (!handle.session.canRetryLastAssistant()) error("Nothing to retry")
        val runId = beginRun(handle, TurnSource.RETRY)
        try {
            emit(handle, RuntimeEvent.RunStarted(runId, System.currentTimeMillis()))
            emit(handle, RuntimeEvent.ActivityChanged(runId, RuntimeActivity.Thinking))
            Log.i(TAG, "retry session=${handle.session.sessionId} run=$runId")
            handle.session.retryLastAssistant()
            if (!handle.stopRequested) {
                val status = if (handle.terminalEmitted) RunStatus.FAILED else finishRun(handle, runId)
                maybeContinueGoal(handle, status)
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (!handle.stopRequested) {
                emitTerminal(
                    handle = handle,
                    runId = runId,
                    text = handle.session.getLastAssistantText().orEmpty(),
                    status = RunStatus.FAILED,
                    message = error.message,
                )
                maybeContinueGoal(handle, RunStatus.FAILED)
            }
        } finally {
            handle.busy = false
            if (handle.runId == runId) handle.runId = null
            publishBusy()
            emit(handle, RuntimeEvent.ActivityChanged(runId, null))
        }
    }

    override suspend fun deliverReminder(sessionId: String, text: String, reminder: String?) {
        val handle = mutex.withLock { handles[sessionId] } ?: error("Agent session is not ready")
        if (handle.busy || handle.session.isStreaming) {
            val runId = handle.runId.orEmpty()
            emit(handle, RuntimeEvent.TurnInjected(runId, text, reminder))
            handle.session.followUp(text)
            eventsBuffer.emit(SessionRuntimeEvent("", RuntimeEvent.SessionListChanged()))
            return
        }
        val runId = beginRun(handle, TurnSource.REMINDER)
        emit(handle, RuntimeEvent.TurnInjected(runId, text, reminder))
        eventsBuffer.emit(SessionRuntimeEvent("", RuntimeEvent.SessionListChanged()))
        backgroundScope.launch { runTurn(handle, runId, text) }
    }

    suspend fun deliverBackgroundTurn(
        sessionId: String,
        notice: TurnNotice?,
        prompt: String,
        source: TurnSource,
    ) {
        val handle = mutex.withLock { handles[sessionId] } ?: return
        if (handle.busy || handle.session.isStreaming) {
            if (notice != null) emit(handle, NoticeEvent(handle.runId.orEmpty(), notice))
            handle.session.followUp(prompt)
            return
        }
        val runId = beginRun(handle, source)
        emit(handle, BackgroundTurnStarted(runId, notice))
        backgroundScope.launch { runTurn(handle, runId, prompt, setTitle = false) }
    }

    override suspend fun activeRunId(sessionId: String): String? = mutex.withLock {
        handles[sessionId]?.runId
    }

    private suspend fun beginRun(handle: Handle, source: TurnSource = TurnSource.USER): String {
        val runId = UUID.randomUUID().toString()
        handle.turnSource = source
        handle.runId = runId
        handle.stopRequested = false
        handle.terminalEmitted = false
        handle.busy = true
        publishBusy()
        goalDriver?.noteTurn(handle.session.sessionId, source)
        return runId
    }

    private suspend fun runTurn(
        handle: Handle,
        runId: String,
        text: String,
        setTitle: Boolean = true,
    ) {
        try {
            if (setTitle) ensureTitle(handle, text)
            emit(handle, RuntimeEvent.RunStarted(runId, System.currentTimeMillis()))
            emit(handle, RuntimeEvent.ActivityChanged(runId, RuntimeActivity.Thinking))
            Log.i(TAG, "run session=${handle.session.sessionId} run=$runId chars=${text.length}")
            handle.session.prompt(text)
            if (!handle.stopRequested) {
                val status = if (handle.terminalEmitted) RunStatus.FAILED else finishRun(handle, runId)
                maybeContinueGoal(handle, status)
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (!handle.stopRequested) {
                emitTerminal(
                    handle = handle,
                    runId = runId,
                    text = handle.session.getLastAssistantText().orEmpty(),
                    status = RunStatus.FAILED,
                    message = error.message,
                )
                maybeContinueGoal(handle, RunStatus.FAILED)
            }
        } finally {
            handle.busy = false
            if (handle.runId == runId) handle.runId = null
            publishBusy()
            emit(handle, RuntimeEvent.ActivityChanged(runId, null))
        }
    }

    override fun stop(sessionId: String) {
        val handle = handles[sessionId] ?: return
        val runId = handle.runId ?: return
        if (handle.stopRequested) return
        handle.stopRequested = true
        if (!handle.terminalEmitted) {
            handle.terminalEmitted = true
            emitNow(
                handle,
                RuntimeEvent.RunFinished(
                    runId = runId,
                    text = handle.session.getLastAssistantText().orEmpty(),
                    status = RunStatus.STOPPED,
                    finishedAt = System.currentTimeMillis(),
                ),
            )
        }
        handle.session.abort()
        goalDriver?.let { driver -> backgroundScope.launch { driver.pauseOnStop(sessionId) } }
    }

    override suspend fun history(sessionId: String): List<AssistantMessage> {
        val loaded = mutex.withLock {
            handles[sessionId]?.let { handle -> handle to handle.engine.history(handle.session) }
        } ?: return emptyList()
        emitUsage(loaded.first)
        return loaded.second
    }

    override suspend fun sessionTitle(sessionId: String): String? = mutex.withLock {
        handles[sessionId]?.session?.sessionManager?.getSessionName()
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
        eventsBuffer.emit(SessionRuntimeEvent("", RuntimeEvent.SessionListChanged()))
    }

    override suspend fun setSessionPinned(id: String, pinned: Boolean) {
        catalog.setPinned(id, pinned)
        eventsBuffer.emit(SessionRuntimeEvent("", RuntimeEvent.SessionListChanged()))
    }

    override suspend fun setSessionArchived(id: String, archived: Boolean) {
        catalog.setArchived(id, archived)
        if (archived) sessionArchived?.invoke(id)
        eventsBuffer.emit(SessionRuntimeEvent(id, RuntimeEvent.SessionArchived(archived = archived)))
        eventsBuffer.emit(SessionRuntimeEvent("", RuntimeEvent.SessionListChanged()))
    }

    override suspend fun deleteSession(id: String) {
        val summary = catalog.find(visibleCwds(), id)
        mutex.withLock { detach(id) }
        sessionCleaner?.invoke(id)
        if (summary != null) {
            catalog.delete(summary.filePath)
            catalog.removeMeta(id)
        }
        eventsBuffer.emit(SessionRuntimeEvent(id, RuntimeEvent.SessionRemoved()))
        eventsBuffer.emit(SessionRuntimeEvent("", RuntimeEvent.SessionListChanged()))
    }

    override suspend fun setModel(providerId: String, modelId: String, thinkingLevel: ThinkingLevel) {
        val config = providers.resolveConfig(ModelConfig(providerId, modelId, thinkingLevel))
        modelConfigStore.save(config)
        val model = providers.sdkModel(config.providerId, config.modelId) ?: return
        val snapshot = mutex.withLock { handles.values.toList() }
        snapshot.forEach { handle ->
            if (runCatching { handle.session.setModel(model, config.thinkingLevel.runtimeValue()) }.isSuccess) {
                emitUsage(handle)
            }
        }
    }

    override suspend fun setThinkingLevel(thinkingLevel: ThinkingLevel) {
        val config = modelConfigStore.read()
        val updated = providers.resolveConfig(config.copy(thinkingLevel = thinkingLevel))
        modelConfigStore.save(updated)
        val snapshot = mutex.withLock { handles.values.toList() }
        snapshot.forEach { handle ->
            runCatching { handle.session.setThinkingLevel(updated.thinkingLevel.runtimeValue()) }
        }
    }

    override suspend fun currentConfig(): ModelConfig = modelConfigStore.read()

    override suspend fun usage(sessionId: String): SessionUsage? {
        val handle = mutex.withLock { handles[sessionId] } ?: return null
        return runCatching {
            val stats = handle.session.sessionManager.getSessionStats()
            val contextTokens = estimateContextTokens(
                handle.session.sessionManager.buildSessionContext().messages,
            ).tokens
            SessionUsage(
                contextTokens = contextTokens,
                contextWindow = handle.session.model.contextWindow,
                inputTokens = stats.inputTokens,
                outputTokens = stats.outputTokens,
                cacheReadTokens = stats.cacheReadTokens,
                cacheWriteTokens = stats.cacheWriteTokens,
                cost = stats.totalCost,
            )
        }.getOrNull()
    }

    override suspend fun compact(sessionId: String): CompactionOutcome {
        val handle = mutex.withLock { handles[sessionId] } ?: return CompactionOutcome.Unavailable
        if (handle.busy || handle.session.isStreaming) {
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
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            CompactionOutcome.Failed(error.message)
        }
        emitUsage(handle)
        return outcome
    }

    internal suspend fun emitUsage(handle: Handle, runId: String = handle.runId.orEmpty()) {
        val current = usage(handle.session.sessionId) ?: return
        emit(handle, UsageChanged(runId, current))
    }

    suspend fun reconfigure() {
        val snapshot = mutex.withLock {
            handles.values.map { handle ->
                Triple(handle.session.sessionId, handle.cwd, handle.session.sessionFile?.toString())
            }
        }
        mutex.withLock {
            snapshot.forEach { (sessionId, _, _) -> detach(sessionId) }
            snapshot.forEach { (_, cwd, file) -> attach(cwd, file) }
        }
        eventsBuffer.emit(SessionRuntimeEvent("", RuntimeEvent.SessionListChanged()))
    }

    private fun homeWorkspace(): RuntimeWorkspace = RuntimeWorkspace(
        id = HOME_ID,
        name = HOME_NAME,
        cwd = homeDir.absolutePath,
        isHome = true,
    )

    private suspend fun workspaceFor(workspaceId: String): RuntimeWorkspace {
        if (workspaceId == HOME_ID || workspaceId.isBlank()) return homeWorkspace()
        return workspaceStore.read().firstOrNull { it.id == workspaceId } ?: homeWorkspace()
    }

    private suspend fun visibleCwds(): List<String> =
        listOf(homeDir.absolutePath) + workspaceStore.read().map { it.cwd }

    private suspend fun attach(cwd: String, file: String?): Handle {
        val existing = handles.values.firstOrNull { it.cwd == cwd && it.session.sessionFile?.toString() == file }
        if (existing != null && file != null) return existing
        val engine = engineFactory(File(cwd))
        val config = modelConfigStore.read()
        val session = engine.createSession(file?.toPath(), config)
        val handle = Handle(engine = engine, session = session, cwd = cwd)
        handles[session.sessionId] = handle
        handle.unsubscribe = session.subscribe { event -> onSessionEvent(handle, event) }
        return handle
    }

    private suspend fun detach(sessionId: String) {
        val handle = handles.remove(sessionId) ?: return
        handle.busy = false
        handle.unsubscribe?.invoke()
        handle.unsubscribe = null
        handle.session.close()
        publishBusy()
    }

    private fun publishBusy() {
        busyState.value = handles.values.filter { it.busy }.mapTo(mutableSetOf()) { it.session.sessionId }
    }

    private suspend fun ensureTitle(handle: Handle, text: String) {
        if (handle.session.sessionManager.getSessionName() != null) return
        handle.session.sessionManager.appendSessionInfo(handle.engine.titleFor(text))
        eventsBuffer.emit(SessionRuntimeEvent("", RuntimeEvent.SessionListChanged()))
    }

    private fun emit(handle: Handle, event: RuntimeEvent) {
        emitNow(handle, event)
    }

    private fun emitNow(handle: Handle, event: RuntimeEvent) {
        eventsBuffer.tryEmit(SessionRuntimeEvent(handle.session.sessionId, event))
    }

    private suspend fun finishRun(handle: Handle, runId: String): RunStatus {
        val last = handle.session.messages.asReversed().firstNotNullOfOrNull { it.asAssistantMessage() }
        val status = when (last?.stopReason) {
            StopReason.Error -> RunStatus.FAILED
            StopReason.Aborted -> RunStatus.STOPPED
            else -> RunStatus.DONE
        }
        emitTerminal(
            handle = handle,
            runId = runId,
            text = handle.session.getLastAssistantText().orEmpty(),
            status = status,
            message = last?.errorMessage,
        )
        return status
    }

    private suspend fun maybeContinueGoal(handle: Handle, status: RunStatus) {
        if (handle.stopRequested) return
        val driver = goalDriver ?: return
        val sessionId = handle.session.sessionId
        val round = driver.nextRound(sessionId, handle.turnSource, status == RunStatus.DONE) ?: return
        backgroundScope.launch {
            delay(400)
            if (!handle.busy) {
                deliverBackgroundTurn(
                    sessionId = sessionId,
                    notice = TurnNotice.GoalRound(round.round, round.maxRounds, round.objective),
                    prompt = round.prompt,
                    source = TurnSource.GOAL,
                )
            }
        }
    }

    private suspend fun emitTerminal(
        handle: Handle,
        runId: String,
        text: String,
        status: RunStatus,
        message: String? = null,
    ) {
        if (handle.terminalEmitted) return
        handle.terminalEmitted = true
        if (status == RunStatus.FAILED) {
            emit(handle, RuntimeEvent.RunFailed(runId, message.orEmpty()))
        } else {
            emit(handle, RuntimeEvent.RunFinished(runId, text, status, System.currentTimeMillis()))
        }
        emitUsage(handle, runId)
    }

    private suspend fun onSessionEvent(handle: Handle, event: SessionEvent) {
        val runId = handle.runId.orEmpty()
        val active = runId.isNotEmpty()
        when (event) {
            is SessionEvent.AgentEventReceived -> if (active) onAgentEvent(handle, runId, event.event)
            is SessionEvent.RetryScheduled -> if (active) {
                emit(handle, RuntimeEvent.ActivityChanged(runId, RuntimeActivity.Retrying(event.attempt)))
            }

            is SessionEvent.RetryExhausted -> if (active) {
                emitTerminal(
                    handle = handle,
                    runId = runId,
                    text = handle.session.getLastAssistantText().orEmpty(),
                    status = RunStatus.FAILED,
                    message = event.errorMessage.orEmpty(),
                )
            }

            is SessionEvent.CompactionStarted -> if (active) {
                emit(handle, RuntimeEvent.ActivityChanged(runId, RuntimeActivity.Compacting))
            }

            is SessionEvent.CompactionFinished -> {
                if (active) emit(handle, RuntimeEvent.ActivityChanged(runId, RuntimeActivity.Thinking))
                emit(handle, NoticeEvent(runId, TurnNotice.Compacted(event.tokensBefore, event.tokensAfter)))
                emitUsage(handle, runId)
            }

            is SessionEvent.CompactionFailed -> if (active) {
                emit(handle, RuntimeEvent.ActivityChanged(runId, RuntimeActivity.CompactionFailed))
            }

            is SessionEvent.SessionError -> if (active) {
                emitTerminal(
                    handle = handle,
                    runId = runId,
                    text = handle.session.getLastAssistantText().orEmpty(),
                    status = RunStatus.FAILED,
                    message = event.message,
                )
            }

            else -> Unit
        }
    }

    private suspend fun onAgentEvent(handle: Handle, runId: String, event: AgentEvent) {
        when (event) {
            is AgentEvent.MessageUpdate -> when (val update = event.assistantMessageEvent) {
                is TextDeltaEvent -> emit(handle, RuntimeEvent.TextDelta(runId, update.delta))
                is ThinkingDeltaEvent -> emit(handle, RuntimeEvent.ReasoningDelta(runId, update.delta))
                else -> Unit
            }

            is AgentEvent.ToolExecutionStart -> {
                emit(
                    handle,
                    RuntimeEvent.ToolStarted(
                        runId = runId,
                        callId = event.toolCallId,
                        name = event.toolName,
                        arguments = event.args.entries.associate { (key, value) ->
                            key to value.toString().trim('"')
                        },
                        startedAt = System.currentTimeMillis(),
                    ),
                )
                emit(handle, RuntimeEvent.ActivityChanged(runId, RuntimeActivity.RunningTool(event.toolName)))
            }

            is AgentEvent.ToolExecutionEnd -> {
                emit(
                    handle,
                    RuntimeEvent.ToolFinished(
                        runId = runId,
                        callId = event.toolCallId,
                        name = event.toolName,
                        result = event.result.content.filterIsInstance<TextContent>().joinToString("") { it.text },
                        isError = event.isError,
                        durationMs = event.durationMs,
                    ),
                )
                emit(handle, RuntimeEvent.ActivityChanged(runId, RuntimeActivity.Thinking))
                emitUsage(handle, runId)
            }

            else -> Unit
        }
    }

    companion object {
        const val HOME_ID = "home"
        const val HOME_NAME = "Home"
    }
}

private const val TAG = "DeepDeepRuntime"
