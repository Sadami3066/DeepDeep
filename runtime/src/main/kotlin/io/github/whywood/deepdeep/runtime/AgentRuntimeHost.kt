package io.github.whywood.deepdeep.runtime

import android.util.Log
import dev.pi.agent.AgentEvent
import dev.pi.ai.TextContent
import dev.pi.ai.TextDeltaEvent
import dev.pi.ai.ThinkingDeltaEvent
import dev.pi.coding.session.AgentSession
import dev.pi.coding.session.SessionEvent
import java.io.File
import java.util.UUID
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Path.Companion.toPath

class AgentRuntimeHost(
    private val homeDir: File,
    private val projectsDir: File,
    private val engineFactory: suspend (File) -> PiSessionEngine,
    private val modelConfigStore: ModelConfigStore,
    private val catalog: SessionCatalog,
    private val projectStore: ProjectStore,
    private val providers: ProviderCatalog,
) : AgentRuntime {
    private class Handle(
        val engine: PiSessionEngine,
        val session: AgentSession,
        val cwd: String,
    ) {
        var unsubscribe: (() -> Unit)? = null
        var runId: String? = null
        var stopRequested = false
        var terminalEmitted = false
        var busy = false
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
        projectsDir.mkdirs()
        started = true
    }

    override suspend fun ensureStarted() {
        if (!started) start()
    }

    override suspend fun projects(): List<RuntimeProject> = buildList {
        add(homeProject())
        addAll(projectStore.read().filter { it.id != HOME_ID })
    }

    override suspend fun createProject(name: String): RuntimeProject = mutex.withLock {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "项目名称不能为空" }
        val id = UUID.randomUUID().toString().replace("-", "").take(12)
        val directory = File(projectsDir, id).apply { mkdirs() }
        val project = RuntimeProject(
            id = id,
            name = trimmed,
            cwd = directory.absolutePath,
            createdAt = System.currentTimeMillis(),
        )
        projectStore.add(project)
        eventsBuffer.emit(SessionRuntimeEvent("", RuntimeEvent.SessionListChanged()))
        project
    }

    override suspend fun renameProject(id: String, name: String) {
        if (id == HOME_ID) return
        val project = projectStore.read().firstOrNull { it.id == id } ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        projectStore.add(project.copy(name = trimmed))
        eventsBuffer.emit(SessionRuntimeEvent("", RuntimeEvent.SessionListChanged()))
    }

    override suspend fun deleteProject(id: String) {
        if (id == HOME_ID) return
        projectStore.remove(id)
        eventsBuffer.emit(SessionRuntimeEvent("", RuntimeEvent.SessionListChanged()))
    }

    override suspend fun newSession(projectId: String): OpenedSession = mutex.withLock {
        val project = projectFor(projectId)
        val handle = attach(project.cwd, null)
        eventsBuffer.emit(SessionRuntimeEvent("", RuntimeEvent.SessionListChanged()))
        OpenedSession(handle.session.sessionId, handle.session.sessionFile?.toString(), project.cwd)
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

    override suspend fun send(sessionId: String, text: String) {
        val handle = mutex.withLock { handles[sessionId] } ?: error("Agent session is not ready")
        val runId = UUID.randomUUID().toString()
        handle.runId = runId
        handle.stopRequested = false
        handle.terminalEmitted = false
        handle.busy = true
        publishBusy()
        try {
            ensureTitle(handle, text)
            emit(handle, RuntimeEvent.RunStarted(runId, System.currentTimeMillis()))
            emit(handle, RuntimeEvent.ActivityChanged(runId, RuntimeActivity.Thinking))
            Log.i(TAG, "send session=$sessionId run=$runId chars=${text.length}")
            handle.session.prompt(text)
            if (!handle.stopRequested && !handle.terminalEmitted) {
                emitTerminal(handle, runId, handle.session.getLastAssistantText().orEmpty(), RunStatus.DONE)
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (!handle.stopRequested && !handle.terminalEmitted) {
                emitTerminal(
                    handle = handle,
                    runId = runId,
                    text = handle.session.getLastAssistantText().orEmpty(),
                    status = RunStatus.FAILED,
                    message = error.message,
                )
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
    }

    override suspend fun history(sessionId: String): List<AssistantMessage> = mutex.withLock {
        handles[sessionId]?.let { handle -> handle.engine.history(handle.session) }.orEmpty()
    }

    override suspend fun sessions(projectId: String): List<ConversationSummary> {
        val project = projectFor(projectId)
        return catalog.list(project.cwd).filterNot { it.archived }
    }

    override suspend fun archivedSessions(): List<ConversationSummary> =
        catalog.listAll(allCwds()).filter { it.archived }

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
        eventsBuffer.emit(SessionRuntimeEvent("", RuntimeEvent.SessionListChanged()))
    }

    override suspend fun deleteSession(id: String) {
        val summary = catalog.find(allCwds(), id)
        mutex.withLock { detach(id) }
        if (summary != null) {
            catalog.delete(summary.filePath)
            catalog.removeMeta(id)
        }
        eventsBuffer.emit(SessionRuntimeEvent("", RuntimeEvent.SessionListChanged()))
    }

    override suspend fun setModel(providerId: String, modelId: String, thinkingLevel: ThinkingLevel) {
        val config = providers.resolveConfig(ModelConfig(providerId, modelId, thinkingLevel))
        modelConfigStore.save(config)
        val model = providers.sdkModel(config.providerId, config.modelId) ?: return
        val snapshot = mutex.withLock { handles.values.toList() }
        snapshot.forEach { handle ->
            runCatching { handle.session.setModel(model, config.thinkingLevel.runtimeValue()) }
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

    private fun homeProject(): RuntimeProject = RuntimeProject(
        id = HOME_ID,
        name = HOME_NAME,
        cwd = homeDir.absolutePath,
        isHome = true,
    )

    private suspend fun projectFor(projectId: String): RuntimeProject {
        if (projectId == HOME_ID || projectId.isBlank()) return homeProject()
        return projectStore.read().firstOrNull { it.id == projectId } ?: homeProject()
    }

    private suspend fun allCwds(): List<String> =
        listOf(homeDir.absolutePath) + projectStore.read().map { it.cwd }

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
    }

    private suspend fun onSessionEvent(handle: Handle, event: SessionEvent) {
        val runId = handle.runId ?: return
        when (event) {
            is SessionEvent.AgentEventReceived -> onAgentEvent(handle, runId, event.event)
            is SessionEvent.RetryScheduled -> emit(
                handle,
                RuntimeEvent.ActivityChanged(runId, RuntimeActivity.Retrying(event.attempt)),
            )

            is SessionEvent.RetryExhausted -> emitTerminal(
                handle = handle,
                runId = runId,
                text = handle.session.getLastAssistantText().orEmpty(),
                status = RunStatus.FAILED,
                message = event.errorMessage.orEmpty(),
            )

            is SessionEvent.CompactionStarted -> emit(handle, RuntimeEvent.ActivityChanged(runId, RuntimeActivity.Compacting))
            is SessionEvent.CompactionFinished -> emit(handle, RuntimeEvent.ActivityChanged(runId, RuntimeActivity.Thinking))
            is SessionEvent.CompactionFailed -> emit(handle, RuntimeEvent.ActivityChanged(runId, RuntimeActivity.CompactionFailed))
            is SessionEvent.SessionError -> emitTerminal(
                handle = handle,
                runId = runId,
                text = handle.session.getLastAssistantText().orEmpty(),
                status = RunStatus.FAILED,
                message = event.message,
            )

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
