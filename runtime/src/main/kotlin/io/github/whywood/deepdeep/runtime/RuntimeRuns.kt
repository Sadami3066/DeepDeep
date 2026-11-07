package io.github.whywood.deepdeep.runtime

import dev.pi.agent.AgentMessage
import dev.pi.agent.asAssistantMessage
import dev.pi.agent.asLlmMessage
import dev.pi.ai.BlockContent
import dev.pi.ai.PlainTextContent
import dev.pi.ai.StopReason
import dev.pi.ai.TextContent
import dev.pi.ai.UserMessage
import dev.pi.coding.session.QueueBehavior
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

internal class RuntimeRuns(
    private val mutex: Mutex,
    private val handles: ConcurrentHashMap<String, RuntimeHandle>,
    private val commands: SessionCommandPlane,
    private val eventsBuffer: MutableSharedFlow<SessionRuntimeEvent>,
    private val busyState: MutableStateFlow<Set<String>>,
    private val activeRunState: MutableStateFlow<Map<String, ActiveRunSnapshot>>,
    private val backgroundScope: CoroutineScope,
    private val titleNamer: SessionTitleNamer? = null,
    private val runLedger: RunLedger,
    private val goalDriver: GoalDriver?,
    private val handleFor: suspend (String) -> RuntimeHandle,
    private val sweepIdleHandles: suspend () -> Unit,
    private val reconfigureSession: suspend (String) -> Unit,
) {
    suspend fun send(sessionId: String, text: String, images: List<ImagePart>) {
        val handle = handleFor(sessionId)
        val start = begin(handle, TurnSource.USER) ?: error("Agent session is busy")
        runTurn(handle, start.runId, text, images)
    }

    suspend fun steer(sessionId: String, text: String, images: List<ImagePart>) {
        submitQueuedInput(sessionId, InputQueueKind.STEER, text, images)
    }

    suspend fun followUp(sessionId: String, text: String, images: List<ImagePart>) {
        submitQueuedInput(sessionId, InputQueueKind.FOLLOW_UP, text, images)
    }

    fun queuedInputs(sessionId: String): List<QueuedInput> {
        val handle = handles[sessionId] ?: return emptyList()
        val snapshot = handle.pending.snapshot()
        return snapshot.steering.map { QueuedInput(InputQueueKind.STEER, it) } +
            snapshot.followUp.map { QueuedInput(InputQueueKind.FOLLOW_UP, it) }
    }

    fun clearQueuedInputs(sessionId: String): List<String> {
        val handle = handles[sessionId] ?: return emptyList()
        return clearQueuedInputs(handle)
    }

    fun removeQueuedInput(sessionId: String, kind: InputQueueKind, text: String): Boolean {
        val handle = handles[sessionId] ?: return false
        val behavior = when (kind) {
            InputQueueKind.STEER -> QueueBehavior.Steer
            InputQueueKind.FOLLOW_UP -> QueueBehavior.FollowUp
        }
        val removed = runCatching { handle.session.removeQueuedInput(text, behavior) }.getOrDefault(false)
        val discarded = handle.pending.discard(kind, text)
        if (removed || discarded) emitQueueChanged(handle, handle.state.runId.orEmpty())
        return removed || discarded
    }

    suspend fun retryLastTurn(sessionId: String) {
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

    suspend fun deliverReminder(sessionId: String, text: String, reminder: String?) {
        val handle = handleFor(sessionId)
        when (val delivery = commands.deliver(sessionId, TurnSource.REMINDER) { handle.session.isBusy }) {
            is SessionCommandPlane.Delivery.Started -> {
                val start = delivery.start
                afterBegin(handle, TurnSource.REMINDER)
                emit(
                    handle,
                    RuntimeEvent.TurnInjected(start.runId, text, reminder, TurnSource.REMINDER),
                )
                emitListChanged()
                backgroundScope.launch { runTurn(handle, start.runId, text) }
            }

            is SessionCommandPlane.Delivery.Injected -> {
                handle.touch()
                emit(
                    handle,
                    RuntimeEvent.TurnInjected(delivery.runId, text, reminder, TurnSource.REMINDER),
                )
                handle.session.prompt(text, behavior = QueueBehavior.FollowUp)
                emitListChanged()
            }

            null -> error("Agent session is busy")
        }
    }

    suspend fun deliverPluginTurn(sessionId: String, text: String, followUp: Boolean = false): PluginTurnResult {
        val handle = handleFor(sessionId)
        val delivery = commands.deliver(sessionId, TurnSource.PLUGIN) { handle.session.isBusy }
            ?: error("Agent session is busy")
        return when (delivery) {
            is SessionCommandPlane.Delivery.Started -> {
                val start = delivery.start
                afterBegin(handle, TurnSource.PLUGIN)
                emit(
                    handle,
                    RuntimeEvent.TurnInjected(start.runId, text, null, TurnSource.PLUGIN),
                )
                emitListChanged()
                backgroundScope.launch { runTurn(handle, start.runId, text) }
                PluginTurnResult.Started(start.runId)
            }

            is SessionCommandPlane.Delivery.Injected -> {
                handle.touch()
                emit(
                    handle,
                    RuntimeEvent.TurnInjected(delivery.runId, text, null, TurnSource.PLUGIN),
                )
                handle.session.prompt(text, behavior = if (followUp) QueueBehavior.FollowUp else QueueBehavior.Steer)
                emitListChanged()
                PluginTurnResult.Queued(delivery.runId)
            }
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

    fun activeRunId(sessionId: String): String? = activeRunState.value[sessionId]?.runId

    fun activeTurnSource(sessionId: String): TurnSource? =
        if (activeRunState.value[sessionId] == null) null else commands.snapshot(sessionId)?.turnSource

    fun stop(sessionId: String): List<String> {
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

    internal fun publishBusy() {
        busyState.value = commands.busySessionIds()
    }

    internal fun publishActiveRun(handle: RuntimeHandle) {
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

    internal fun clearActiveRun(sessionId: String) {
        activeRunState.update { it - sessionId }
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

    private val titleJobs = ConcurrentHashMap<String, Job>()
    private val titleFirstTexts = ConcurrentHashMap<String, String>()

    private suspend fun ensureTitle(handle: RuntimeHandle, text: String) {
        val namer = titleNamer ?: return
        val sessionId = handle.session.sessionId
        val current = handle.session.sessionName()
        val first = titleFirstTexts[sessionId] ?: run {
            if (current != null) return
            val history = handle.session.history()
            val firstText = history.firstOrNull { it.role == AssistantRole.USER && it.reminder == null }?.text ?: text
            titleFirstTexts[sessionId] = firstText
            firstText
        }
        val fallback = handle.engine.titleFor(first)
        if (current == null) {
            handle.session.appendTitle(fallback)
            emitListChanged()
        } else if (current != fallback) {
            titleFirstTexts.remove(sessionId)
            return
        }
        if (titleJobs[sessionId]?.isActive == true) return
        titleJobs[sessionId] = backgroundScope.launch {
            try {
                val title = withTimeout(15_000) { namer.title(first) } ?: return@launch
                if (handle.session.sessionName() != fallback) return@launch
                handle.session.appendTitle(title)
                titleFirstTexts.remove(sessionId)
                emitListChanged()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                logWarn(TAG, "title naming failed session=$sessionId", error)
            } finally {
                titleJobs.remove(sessionId)
            }
        }
    }

    internal fun emit(handle: RuntimeHandle, event: RuntimeEvent) {
        val scoped = SessionRuntimeEvent(handle.session.sessionId, event)
        if (eventsBuffer.tryEmit(scoped)) return
        if (event is RuntimeEvent.TextDelta || event is RuntimeEvent.ReasoningDelta) {
            logWarn(TAG, "drop delta: event buffer full session=${scoped.sessionId} run=${event.runId}")
            return
        }
        logWarn(TAG, "event buffer full, deferring ${event::class.simpleName} session=${scoped.sessionId}")
        backgroundScope.launch { eventsBuffer.emit(scoped) }
    }

    internal fun emitActivity(handle: RuntimeHandle, runId: String, activity: RuntimeActivity?) {
        if (handle.activity == activity) return
        handle.activity = activity
        emit(handle, RuntimeEvent.ActivityChanged(runId, activity))
    }

    internal suspend fun emitListChanged() {
        eventsBuffer.emit(SessionRuntimeEvent("", RuntimeEvent.SessionListChanged()))
    }

    internal suspend fun emitSessionRemoved(sessionId: String) {
        eventsBuffer.emit(SessionRuntimeEvent(sessionId, RuntimeEvent.SessionRemoved()))
    }

    internal suspend fun emitUsage(handle: RuntimeHandle, runId: String) {
        val current = handle.session.usage() ?: return
        emit(handle, UsageChanged(runId, current))
    }

    internal suspend fun emitTerminal(
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

    internal fun admitQueuedInput(handle: RuntimeHandle, runId: String, message: AgentMessage) {
        val text = message.userText() ?: return
        handle.pending.admit(text) ?: return
        emit(handle, RuntimeEvent.UserInputAdmitted(runId, text))
        emitQueueChanged(handle, runId)
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

    companion object {
        private const val GOAL_CONTINUE_DELAY_MS = 400L
    }
}

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
