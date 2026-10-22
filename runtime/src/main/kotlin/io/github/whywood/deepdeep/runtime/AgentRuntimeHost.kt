package io.github.whywood.deepdeep.runtime

import android.util.Log
import dev.pi.agent.AgentEvent
import dev.pi.ai.TextContent
import dev.pi.ai.TextDeltaEvent
import dev.pi.ai.ThinkingDeltaEvent
import dev.pi.coding.session.AgentSession
import dev.pi.coding.session.SessionEvent
import java.util.UUID
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Path.Companion.toPath

class AgentRuntimeHost(
    private val engine: PiSessionEngine,
    private val modelConfigStore: ModelConfigStore,
    private val sessionRepository: SessionRepository,
    private val providers: ProviderCatalog,
    private val shellEnabled: suspend () -> Boolean = { true },
) : AgentRuntime {
    private val eventsBuffer = MutableSharedFlow<RuntimeEvent>(
        extraBufferCapacity = 512,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )
    override val events: SharedFlow<RuntimeEvent> = eventsBuffer.asSharedFlow()

    private val mutex = Mutex()
    private var session: AgentSession? = null
    private var unsubscribe: (() -> Unit)? = null
    private var started = false
    private var stopRequested = false
    private var terminalEmitted = false
    private var currentRunId: String? = null

    @Volatile
    override var busy: Boolean = false
        private set

    override val currentSessionId: String?
        get() = session?.sessionId

    override val currentSessionFile: String?
        get() = session?.sessionFile?.toString()

    override suspend fun start() = mutex.withLock {
        if (started) return@withLock
        started = true
        val recent = sessionRepository.list(includeArchived = true).firstOrNull { !it.archived }
        if (recent != null) {
            openLocked(recent.filePath.toPath())
        } else {
            newLocked()
        }
        eventsBuffer.emit(RuntimeEvent.SessionListChanged())
    }

    override suspend fun ensureStarted() {
        if (!started) start()
    }

    override suspend fun send(text: String) = mutex.withLock {
        val active = session ?: error("Agent session is not ready")
        val runId = UUID.randomUUID().toString()
        currentRunId = runId
        stopRequested = false
        terminalEmitted = false
        busy = true
        try {
            ensureTitle(active, text)
            eventsBuffer.emit(RuntimeEvent.RunStarted(runId, System.currentTimeMillis()))
            eventsBuffer.emit(RuntimeEvent.ActivityChanged(runId, "思考中"))
            Log.i(TAG, "send run=$runId chars=${text.length}")
            active.prompt(text)
            if (!stopRequested && !terminalEmitted) {
                emitTerminal(
                    runId = runId,
                    text = active.getLastAssistantText().orEmpty(),
                    status = RunStatus.DONE,
                )
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (!stopRequested && !terminalEmitted) {
                emitTerminal(runId = runId, text = active.getLastAssistantText().orEmpty(), status = RunStatus.FAILED, message = error.message)
            }
        } finally {
            busy = false
            currentRunId = null
            eventsBuffer.emit(RuntimeEvent.ActivityChanged(runId, null))
        }
    }

    override fun stop() {
        val runId = currentRunId ?: return
        if (stopRequested) return
        stopRequested = true
        if (!terminalEmitted) {
            terminalEmitted = true
            eventsBuffer.tryEmit(
                RuntimeEvent.RunFinished(
                    runId = runId,
                    text = session?.getLastAssistantText().orEmpty(),
                    status = RunStatus.STOPPED,
                    finishedAt = System.currentTimeMillis(),
                ),
            )
        }
        session?.abort()
    }

    override suspend fun newChat() = mutex.withLock {
        detachLocked()
        newLocked()
        eventsBuffer.emit(RuntimeEvent.SessionListChanged())
    }

    override suspend fun open(filePath: String) = mutex.withLock {
        detachLocked()
        openLocked(filePath.toPath())
        eventsBuffer.emit(RuntimeEvent.SessionListChanged())
    }

    override suspend fun history(): List<AssistantMessage> = mutex.withLock {
        session?.let { engine.history(it) }.orEmpty()
    }

    override suspend fun sessions(includeArchived: Boolean): List<ConversationSummary> =
        sessionRepository.list(includeArchived)

    override suspend fun renameSession(id: String, title: String) {
        sessionRepository.rename(id, title)
        eventsBuffer.emit(RuntimeEvent.SessionListChanged())
    }

    override suspend fun setSessionPinned(id: String, pinned: Boolean) {
        sessionRepository.setPinned(id, pinned)
        eventsBuffer.emit(RuntimeEvent.SessionListChanged())
    }

    override suspend fun setSessionArchived(id: String, archived: Boolean) {
        sessionRepository.setArchived(id, archived)
        eventsBuffer.emit(RuntimeEvent.SessionListChanged())
    }

    override suspend fun deleteSession(id: String) = mutex.withLock {
        if (session?.sessionId == id) {
            detachLocked()
            newLocked()
        }
        sessionRepository.delete(id)
        eventsBuffer.emit(RuntimeEvent.SessionListChanged())
    }

    override suspend fun setModel(providerId: String, modelId: String, thinkingLevel: ThinkingLevel) = mutex.withLock {
        val config = providers.resolveConfig(ModelConfig(providerId, modelId, thinkingLevel))
        modelConfigStore.save(config)
        val active = session ?: return@withLock
        val model = providers.sdkModel(config.providerId, config.modelId) ?: return@withLock
        active.setModel(model, config.thinkingLevel.runtimeValue())
    }

    override suspend fun setThinkingLevel(thinkingLevel: ThinkingLevel) = mutex.withLock {
        val config = modelConfigStore.read()
        val updated = providers.resolveConfig(config.copy(thinkingLevel = thinkingLevel))
        modelConfigStore.save(updated)
        session?.setThinkingLevel(updated.thinkingLevel.runtimeValue())
        Unit
    }

    override suspend fun currentConfig(): ModelConfig = modelConfigStore.read()

    suspend fun reconfigure() = mutex.withLock {
        val file = currentSessionFile
        detachLocked()
        if (file != null) openLocked(file.toPath()) else newLocked()
        eventsBuffer.emit(RuntimeEvent.SessionListChanged())
    }

    suspend fun close() = mutex.withLock {
        detachLocked()
        started = false
    }

    private suspend fun newLocked() {
        val config = modelConfigStore.read()
        val created = engine.createSession(null, config, shellEnabled())
        attach(created)
    }

    private suspend fun openLocked(file: okio.Path) {
        val config = modelConfigStore.read()
        val created = engine.createSession(file, config, shellEnabled())
        attach(created)
    }

    private suspend fun attach(created: AgentSession) {
        session = created
        unsubscribe = created.subscribe { event -> handle(event) }
    }

    private suspend fun detachLocked() {
        unsubscribe?.invoke()
        unsubscribe = null
        session?.close()
        session = null
        busy = false
        currentRunId = null
    }

    private suspend fun ensureTitle(active: AgentSession, text: String) {
        if (active.sessionManager.getSessionName() != null) return
        active.sessionManager.appendSessionInfo(engine.titleFor(text))
        eventsBuffer.emit(RuntimeEvent.SessionListChanged())
    }

    private suspend fun emitTerminal(
        runId: String,
        text: String,
        status: RunStatus,
        message: String? = null,
    ) {
        if (terminalEmitted) return
        terminalEmitted = true
        if (status == RunStatus.FAILED) {
            eventsBuffer.emit(RuntimeEvent.RunFailed(runId, message ?: "请求失败，请稍后重试。"))
        } else {
            eventsBuffer.emit(RuntimeEvent.RunFinished(runId, text, status, System.currentTimeMillis()))
        }
    }

    private suspend fun handle(event: SessionEvent) {
        val runId = currentRunId ?: return
        when (event) {
            is SessionEvent.AgentEventReceived -> handleAgentEvent(runId, event.event)
            is SessionEvent.RetryScheduled -> eventsBuffer.emit(
                RuntimeEvent.ActivityChanged(runId, "重试中（第 ${event.attempt} 次）"),
            )

            is SessionEvent.RetryExhausted -> emitTerminal(
                runId = runId,
                text = session?.getLastAssistantText().orEmpty(),
                status = RunStatus.FAILED,
                message = event.errorMessage ?: "重试失败",
            )

            is SessionEvent.CompactionStarted -> eventsBuffer.emit(RuntimeEvent.ActivityChanged(runId, "压缩上下文…"))
            is SessionEvent.CompactionFinished -> eventsBuffer.emit(RuntimeEvent.ActivityChanged(runId, "思考中"))
            is SessionEvent.CompactionFailed -> eventsBuffer.emit(RuntimeEvent.ActivityChanged(runId, "压缩失败"))
            is SessionEvent.SessionError -> emitTerminal(runId, session?.getLastAssistantText().orEmpty(), RunStatus.FAILED, event.message)
            else -> Unit
        }
    }

    private suspend fun handleAgentEvent(runId: String, event: AgentEvent) {
        when (event) {
            is AgentEvent.MessageUpdate -> when (val update = event.assistantMessageEvent) {
                is TextDeltaEvent -> eventsBuffer.emit(RuntimeEvent.TextDelta(runId, update.delta))
                is ThinkingDeltaEvent -> eventsBuffer.emit(RuntimeEvent.ReasoningDelta(runId, update.delta))
                else -> Unit
            }

            is AgentEvent.ToolExecutionStart -> {
                eventsBuffer.emit(
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
                eventsBuffer.emit(RuntimeEvent.ActivityChanged(runId, "运行 ${event.toolName}"))
            }

            is AgentEvent.ToolExecutionEnd -> {
                eventsBuffer.emit(
                    RuntimeEvent.ToolFinished(
                        runId = runId,
                        callId = event.toolCallId,
                        name = event.toolName,
                        result = event.result.content.filterIsInstance<TextContent>().joinToString("") { it.text },
                        isError = event.isError,
                        durationMs = event.durationMs,
                    ),
                )
                eventsBuffer.emit(RuntimeEvent.ActivityChanged(runId, "思考中"))
            }

            else -> Unit
        }
    }
}

private const val TAG = "DeepDeepRuntime"
