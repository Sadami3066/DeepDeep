package io.github.whywood.deepdeep.conversation

import io.github.whywood.deepdeep.runtime.ActiveRunSnapshot
import io.github.whywood.deepdeep.runtime.AgentRuntime
import io.github.whywood.deepdeep.runtime.AssistantMessage
import io.github.whywood.deepdeep.runtime.ImagePart
import io.github.whywood.deepdeep.runtime.MessageStatus
import io.github.whywood.deepdeep.runtime.RunStatus
import io.github.whywood.deepdeep.runtime.RuntimeActivity
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import io.github.whywood.deepdeep.runtime.SessionRuntimeEvent
import java.util.LinkedHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ActiveRunView(
    val sessionId: String,
    val title: String,
    val activity: RuntimeActivity?,
    val startedAt: Long,
)

data class RunCompletion(
    val sessionId: String,
    val title: String,
    val status: RunStatus,
    val text: String,
    val error: String?,
)

data class ConversationFailure(
    val sessionId: String,
    val message: String,
)

data class DroppedInputs(
    val sessionId: String,
    val texts: List<String>,
)

class ConversationStore(
    private val runtime: AgentRuntime,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    strings: () -> ConversationStrings = { ConversationStrings.fallback() },
    localizeError: ((String?) -> String)? = null,
    private val decorate: suspend (ConversationState, String) -> ConversationState = { state, _ -> state },
    private val isRetrying: (String) -> Boolean = { false },
    private val maxSessions: Int = MAX_SESSIONS,
    private val deltaDelayMs: Long = DELTA_DELAY_MS,
    private val reconcileDelayMs: Long = RECONCILE_DELAY_MS,
) {
    @Volatile
    private var stringsProvider: () -> ConversationStrings = strings

    private val localize: (String?) -> String = localizeError ?: { raw -> stringsProvider().runError(raw) }

    private val lock = Any()
    private val states = LinkedHashMap<String, MutableStateFlow<ConversationState>>(16, 0.75f, true)
    private val pinned = mutableSetOf<String>()
    private val loading = mutableSetOf<String>()
    private val loadingWaiters = mutableMapOf<String, CompletableDeferred<ConversationState>>()
    private val deferred = mutableMapOf<String, MutableList<RuntimeEvent>>()

    private val pendingDeltas = LinkedHashMap<String, MutableList<RuntimeEvent>>()
    private var deltaJob: Job? = null

    private val pendingSends = mutableMapOf<String, Job>()
    private val reconcileJobs = mutableMapOf<String, Job>()

    private val mutableActiveRuns = MutableStateFlow<Map<String, ActiveRunView>>(emptyMap())
    val activeRuns: StateFlow<Map<String, ActiveRunView>> = mutableActiveRuns.asStateFlow()

    private val mutableCompletions = MutableSharedFlow<RunCompletion>(
        extraBufferCapacity = COMPLETION_BUFFER,
    )
    val runCompletions: SharedFlow<RunCompletion> = mutableCompletions.asSharedFlow()

    private val mutableFailures = MutableSharedFlow<ConversationFailure>(
        extraBufferCapacity = FAILURE_BUFFER,
    )
    val failures: SharedFlow<ConversationFailure> = mutableFailures.asSharedFlow()

    private val mutableDroppedInputs = MutableSharedFlow<DroppedInputs>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val droppedInputs: SharedFlow<DroppedInputs> = mutableDroppedInputs.asSharedFlow()

    private val mutableRunFailures = MutableSharedFlow<ConversationFailure>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val runFailures: SharedFlow<ConversationFailure> = mutableRunFailures.asSharedFlow()

    private val mutableLifecycle = MutableSharedFlow<SessionRuntimeEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val lifecycle: SharedFlow<SessionRuntimeEvent> = mutableLifecycle.asSharedFlow()

    private val mutableSessionChanges = MutableSharedFlow<Unit>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val sessionChanges: SharedFlow<Unit> = mutableSessionChanges.asSharedFlow()

    private var retryGate: (String) -> Boolean = isRetrying

    fun setRetryGate(gate: (String) -> Boolean) {
        retryGate = gate
    }

    var onEvict: ((String) -> Unit)? = null

    private val active = LinkedHashMap<String, ActiveRunView>()
    private val seeded = mutableSetOf<String>()
    private val finished = mutableSetOf<String>()

    private var eventJob: Job? = null
    private var busyJob: Job? = null
    private var seedJob: Job? = null

    fun start() {
        if (eventJob != null) return
        eventJob = scope.launch {
            runtime.events.collect(::onEvent)
        }
        busyJob = scope.launch { watchBusy() }
        seedJob = scope.launch { runtime.activeRuns.collect(::seed) }
    }

    fun shutdown() {
        eventJob?.cancel()
        busyJob?.cancel()
        seedJob?.cancel()
        deltaJob?.cancel()
        eventJob = null
        busyJob = null
        seedJob = null
        deltaJob = null
        pendingSends.values.forEach { it.cancel() }
        pendingSends.clear()
        reconcileJobs.values.forEach { it.cancel() }
        reconcileJobs.clear()
    }

    fun updateStrings(value: ConversationStrings) {
        stringsProvider = { value }
    }

    fun observe(sessionId: String): StateFlow<ConversationState> = flowFor(sessionId).asStateFlow()

    fun peek(sessionId: String): ConversationState? = synchronized(lock) { states[sessionId]?.value }

    fun isLoaded(sessionId: String): Boolean = peek(sessionId)?.loaded == true

    fun pin(sessionId: String) {
        synchronized(lock) { pinned += sessionId }
    }

    fun unpin(sessionId: String) {
        synchronized(lock) { pinned -= sessionId }
    }

    suspend fun open(sessionId: String, title: String? = null): ConversationState {
        peek(sessionId)?.takeIf { it.loaded }?.let { loaded ->
            if (!title.isNullOrBlank() && loaded.title.isBlank()) {
                mutate(sessionId) { it.copy(title = title) }
            }
            if (loaded.messages.isNotEmpty() || loaded.sending) {
                val queued = runCatching { runtime.queuedInputs(sessionId) }.getOrDefault(loaded.queuedInputs)
                if (queued != loaded.queuedInputs) mutate(sessionId) { it.copy(queuedInputs = queued) }
                return peek(sessionId) ?: loaded
            }
        }
        val waiter: CompletableDeferred<ConversationState>?
        val owner: Boolean
        synchronized(lock) {
            if (sessionId in loading) {
                waiter = loadingWaiters.getOrPut(sessionId) { CompletableDeferred() }
                owner = false
            } else {
                loading += sessionId
                waiter = null
                owner = true
            }
        }
        if (!owner) return waiter!!.await()
        return try {
            val history = withContext(ioDispatcher) { runtime.history(sessionId) }
            val usage = withContext(ioDispatcher) { runtime.usage(sessionId) }
            val model = withContext(ioDispatcher) { runtime.sessionModel(sessionId) }
            val queued = runtime.queuedInputs(sessionId)
            val localized = history.map { message -> message.withLocalizedError() }
            var seededState = ConversationState(
                messages = localized,
                title = title.orEmpty(),
                loaded = true,
                usage = usage,
                sessionModel = model,
                queuedInputs = queued,
            )
            runtime.activeRuns.value[sessionId]?.let { run ->
                seededState = seededState.adoptActiveRun(run.runId, RuntimeActivity.Thinking, run.startedAt)
            }
            val decorated = decorate(seededState, sessionId)
            val resolved = synchronized(lock) {
                var merged = decorated
                deferred.remove(sessionId)?.forEach { event -> merged = reduce(merged, event, stringsProvider()) }
                loading.remove(sessionId)
                stateFor(sessionId).value = merged
                trimLocked(sessionId)
                loadingWaiters.remove(sessionId)?.complete(merged)
                merged
            }
            resolved
        } catch (cancelled: CancellationException) {
            synchronized(lock) {
                loading.remove(sessionId)
                deferred.remove(sessionId)
                loadingWaiters.remove(sessionId)?.cancel()
            }
            throw cancelled
        } catch (error: Exception) {
            synchronized(lock) {
                loading.remove(sessionId)
                deferred.remove(sessionId)
                loadingWaiters.remove(sessionId)?.completeExceptionally(error)
            }
            throw error
        }
    }

    suspend fun reopen(sessionId: String, title: String? = null): ConversationState {
        synchronized(lock) { states[sessionId]?.value = ConversationState() }
        return open(sessionId, title)
    }

    fun update(sessionId: String, transform: (ConversationState) -> ConversationState) {
        mutate(sessionId, transform)
    }

    fun updateIfPresent(sessionId: String, transform: (ConversationState) -> ConversationState): Boolean =
        synchronized(lock) {
            val flow = states[sessionId] ?: return@synchronized false
            flow.value = transform(flow.value)
            true
        }

    fun put(sessionId: String, state: ConversationState) {
        synchronized(lock) {
            stateFor(sessionId).value = state
            trimLocked(sessionId)
        }
    }

    fun move(from: String, to: String) {
        synchronized(lock) {
            if (from == to) return
            val state = states.remove(from)?.value ?: return
            deferred.remove(from)
            pendingDeltas.remove(from)
            if (to !in states) states[to] = MutableStateFlow(state)
            trimLocked(to)
        }
    }

    fun close(sessionId: String) {
        synchronized(lock) {
            reconcileJobs.remove(sessionId)?.cancel()
            pinned -= sessionId
            states.remove(sessionId)
            deferred.remove(sessionId)
            pendingDeltas.remove(sessionId)
        }
        onEvict?.invoke(sessionId)
    }

    fun evict(sessionId: String) = close(sessionId)

    fun evictUnpinned(keep: Set<String> = emptySet()) {
        val victims = synchronized(lock) {
            val protectedNow = pinned + keep + loading
            val removed = states.keys.filterNot { it in protectedNow }
            removed.forEach { sessionId ->
                states.remove(sessionId)
                deferred.remove(sessionId)
                pendingDeltas.remove(sessionId)
            }
            removed
        }
        victims.forEach { onEvict?.invoke(it) }
    }

    fun send(
        sessionId: String,
        prompt: String,
        title: String? = null,
        prepareImages: suspend () -> List<ImagePart> = { emptyList() },
    ): Boolean {
        val text = prompt.trim()
        if (text.isEmpty()) return false
        val current = peek(sessionId) ?: return false
        if (!current.loaded || current.sending) return false
        mutate(sessionId) { state ->
            val begun = state.beginTurn(text, System.currentTimeMillis())
            if (title != null && (
                    begun.title.isBlank() ||
                        begun.title == stringsProvider().newChatTitle ||
                        begun.title == ConversationStrings.fallback().newChatTitle
                    )
            ) {
                begun.copy(title = title)
            } else {
                begun
            }
        }
        lateinit var job: Job
        job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val images = prepareImages()
                synchronized(lock) {
                    if (pendingSends[sessionId] === job) pendingSends.remove(sessionId)
                }
                runtime.send(sessionId, text, images)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                finishWithError(sessionId, error.message)
            } finally {
                synchronized(lock) {
                    if (pendingSends[sessionId] === job) pendingSends.remove(sessionId)
                }
            }
        }
        synchronized(lock) { pendingSends[sessionId] = job }
        job.start()
        return true
    }

    fun steer(sessionId: String, prompt: String, images: List<ImagePart> = emptyList()) {
        enqueue(sessionId, prompt, images, followUp = false)
    }

    fun followUp(sessionId: String, prompt: String, images: List<ImagePart> = emptyList()) {
        enqueue(sessionId, prompt, images, followUp = true)
    }

    fun stop(sessionId: String): List<String> {
        synchronized(lock) { pendingSends.remove(sessionId)?.cancel() }
        val dropped = runtime.stop(sessionId)
        if (runtime.activeRuns.value[sessionId] == null) {
            scheduleReconcile(sessionId, RECONCILE_DELAY_MS)
        }
        return dropped
    }

    fun retryLast(sessionId: String) {
        mutate(sessionId) { state -> state.markRetryingTail(System.currentTimeMillis()) }
        scope.launch {
            try {
                runtime.retryLastTurn(sessionId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                finishWithError(sessionId, error.message)
            }
        }
    }

    fun clearQueuedInputs(sessionId: String): List<String> = runtime.clearQueuedInputs(sessionId)

    fun toggleStep(sessionId: String, messageId: Long, stepId: Long) {
        mutate(sessionId) { state -> state.toggleStep(messageId, stepId) }
    }

    fun onAppForeground() {
        val sending = synchronized(lock) {
            states.mapNotNull { (sessionId, flow) -> sessionId.takeIf { flow.value.sending } }
        }
        sending.forEach { sessionId -> scheduleReconcile(sessionId, 0L) }
    }

    suspend fun reconcile(sessionId: String) {
        val activeRun = runtime.activeRuns.value[sessionId]
        val current = peek(sessionId)
        if (activeRun != null) {
            if (current == null || !current.sending) return
            if (current.messages.any { it.runId == activeRun.runId }) return
            mutate(sessionId) { state ->
                state.adoptActiveRun(activeRun.runId, RuntimeActivity.Thinking, activeRun.startedAt)
            }
            return
        }
        if (current == null || !current.sending) return
        if (retryGate(sessionId)) return
        val history = withContext(ioDispatcher) { runtime.history(sessionId) }
        val model = withContext(ioDispatcher) { runtime.sessionModel(sessionId) }
        val settled = history.map { message ->
            val localized = message.withLocalizedError()
            if (!localized.streaming && !localized.retrying) {
                localized
            } else {
                localized.copy(
                    streaming = false,
                    retrying = false,
                    status = if (localized.status == MessageStatus.RUNNING) {
                        MessageStatus.DONE
                    } else {
                        localized.status
                    },
                )
            }
        }
        mutate(sessionId) { state ->
            state.copy(
                messages = settled,
                sending = false,
                activity = null,
                activityStartedAt = null,
                loaded = true,
                sessionModel = model ?: state.sessionModel,
            )
        }
    }

    private suspend fun onEvent(scoped: SessionRuntimeEvent) {
        val sessionId = scoped.sessionId
        val event = scoped.event
        if (event is RuntimeEvent.SessionListChanged) {
            flushDeltas()
            refreshTitles()
            mutableSessionChanges.tryEmit(Unit)
            return
        }
        if (event is RuntimeEvent.SessionArchived) {
            mutableSessionChanges.tryEmit(Unit)
        }
        if (sessionId.isBlank()) return
        val buffered = synchronized(lock) {
            if (sessionId in loading) {
                deferred.getOrPut(sessionId) { mutableListOf() } += event
                true
            } else {
                false
            }
        }
        if (buffered) return
        if (event is RuntimeEvent.TextDelta || event is RuntimeEvent.ReasoningDelta) {
            enqueueDelta(sessionId, event)
            return
        }
        flushDeltas()
        if (event is RuntimeEvent.SessionRemoved) {
            removeSession(sessionId)
            mutableSessionChanges.tryEmit(Unit)
            return
        }
        if (event is RuntimeEvent.QueuedInputsDropped) {
            mutableDroppedInputs.tryEmit(DroppedInputs(sessionId, event.texts))
        }
        updateActiveRun(sessionId, event)
        if (event is RuntimeEvent.RunFailed) {
            mutableRunFailures.tryEmit(ConversationFailure(sessionId, event.message))
        }
        if (
            event is RuntimeEvent.RunStarted ||
            event is RuntimeEvent.ActivityChanged ||
            event is RuntimeEvent.RunFinished ||
            event is RuntimeEvent.RunFailed
        ) {
            mutableLifecycle.tryEmit(scoped)
        }
        if (event is RuntimeEvent.ActivityChanged && retryGate(sessionId)) return
        applyEvent(sessionId, event)
    }

    private suspend fun updateActiveRun(sessionId: String, event: RuntimeEvent) {
        when (event) {
            is RuntimeEvent.RunStarted -> {
                val view = ActiveRunView(
                    sessionId = sessionId,
                    title = titleOf(sessionId),
                    activity = RuntimeActivity.Thinking,
                    startedAt = event.startedAt,
                )
                synchronized(lock) {
                    finished -= sessionId
                    seeded -= sessionId
                    active[sessionId] = view
                    publishLocked()
                }
            }

            is RuntimeEvent.ActivityChanged -> synchronized(lock) {
                val current = active[sessionId] ?: return
                active[sessionId] = current.copy(activity = event.activity)
                publishLocked()
            }

            is RuntimeEvent.RunFinished -> {
                val current = synchronized(lock) {
                    finished += sessionId
                    seeded -= sessionId
                    active.remove(sessionId).also { publishLocked() }
                }
                mutableCompletions.emit(
                    RunCompletion(
                        sessionId = sessionId,
                        title = current?.title ?: titleOf(sessionId),
                        status = event.status,
                        text = event.text,
                        error = null,
                    ),
                )
            }

            is RuntimeEvent.RunFailed -> {
                val current = synchronized(lock) {
                    finished += sessionId
                    seeded -= sessionId
                    active.remove(sessionId).also { publishLocked() }
                }
                mutableCompletions.emit(
                    RunCompletion(
                        sessionId = sessionId,
                        title = current?.title ?: titleOf(sessionId),
                        status = RunStatus.FAILED,
                        text = "",
                        error = event.message,
                    ),
                )
            }

            else -> Unit
        }
    }

    private fun applyEvent(sessionId: String, event: RuntimeEvent) {
        mutate(sessionId) { state -> reduce(state, event, stringsProvider()) }
    }

    private fun enqueueDelta(sessionId: String, event: RuntimeEvent) {
        synchronized(lock) {
            val pending = pendingDeltas.getOrPut(sessionId) { mutableListOf() }
            val merged = pending.lastOrNull()?.let { merge(it, event) }
            if (merged != null) pending[pending.lastIndex] = merged else pending += event
            if (deltaJob == null) {
                deltaJob = scope.launch {
                    delay(deltaDelayMs)
                    flushDeltas()
                }
            }
        }
    }

    private fun flushDeltas() {
        val batch = synchronized(lock) {
            deltaJob?.cancel()
            deltaJob = null
            if (pendingDeltas.isEmpty()) return
            val copy = pendingDeltas.mapValues { (_, events) -> events.toList() }
            pendingDeltas.clear()
            copy
        }
        batch.forEach { (sessionId, events) ->
            mutate(sessionId) { state ->
                events.fold(state) { acc, event -> reduce(acc, event, stringsProvider()) }
            }
        }
    }

    private suspend fun refreshTitles() {
        val snapshot = synchronized(lock) { active.toMap() }
        if (snapshot.isEmpty()) return
        val titles = snapshot.mapNotNull { (sessionId, current) ->
            runtime.sessionTitle(sessionId)?.takeIf { it != current.title }?.let { sessionId to it }
        }
        if (titles.isEmpty()) return
        synchronized(lock) {
            var changed = false
            titles.forEach { (sessionId, title) ->
                val current = active[sessionId] ?: return@forEach
                if (current.title != title) {
                    active[sessionId] = current.copy(title = title)
                    changed = true
                }
            }
            if (changed) publishLocked()
        }
    }

    private suspend fun seed(runs: Map<String, ActiveRunSnapshot>) {
        val candidates = synchronized(lock) {
            runs.filterKeys { it !in active && it !in finished }
        }
        val resolved = candidates.map { (sessionId, snapshot) ->
            ActiveRunView(
                sessionId = sessionId,
                title = titleOf(sessionId),
                activity = RuntimeActivity.Thinking,
                startedAt = snapshot.startedAt,
            )
        }
        synchronized(lock) {
            seeded.filter { it !in runs.keys && it in active }.forEach { sessionId ->
                active.remove(sessionId)
                seeded -= sessionId
            }
            resolved.forEach { view ->
                if (view.sessionId in active || view.sessionId in finished) return@forEach
                active[view.sessionId] = view
                seeded += view.sessionId
            }
            publishLocked()
        }
    }

    private suspend fun titleOf(sessionId: String): String =
        peek(sessionId)?.title?.takeIf { it.isNotBlank() }
            ?: runtime.sessionTitle(sessionId)
            ?: stringsProvider().newChatTitle

    private fun publishLocked() {
        mutableActiveRuns.value = LinkedHashMap(active)
    }

    private suspend fun watchBusy() {
        var previous = runtime.busySessionIds.value
        runtime.busySessionIds.collect { ids ->
            val left = previous - ids
            previous = ids
            left.forEach { sessionId ->
                val sending = peek(sessionId)?.sending == true
                if (sending && !retryGate(sessionId)) {
                    scheduleReconcile(sessionId, reconcileDelayMs)
                }
            }
        }
    }

    private fun scheduleReconcile(sessionId: String, delayMs: Long) {
        synchronized(lock) {
            if (reconcileJobs[sessionId]?.isActive == true) return
            val job = scope.launch {
                try {
                    if (delayMs > 0) delay(delayMs)
                    reconcile(sessionId)
                } finally {
                    synchronized(lock) { reconcileJobs.remove(sessionId) }
                }
            }
            reconcileJobs[sessionId] = job
        }
    }

    private fun removeSession(sessionId: String) {
        synchronized(lock) {
            states.remove(sessionId)
            deferred.remove(sessionId)
            pendingDeltas.remove(sessionId)
            pendingSends.remove(sessionId)?.cancel()
            reconcileJobs.remove(sessionId)?.cancel()
            loading.remove(sessionId)
            loadingWaiters.remove(sessionId)?.cancel()
            active.remove(sessionId)
            seeded -= sessionId
            finished -= sessionId
            publishLocked()
        }
    }

    private fun finishWithError(sessionId: String, message: String?) {
        mutate(sessionId) { state ->
            state.finishWithError("", message, stringsProvider(), System.currentTimeMillis())
        }
    }

    private fun enqueue(sessionId: String, prompt: String, images: List<ImagePart>, followUp: Boolean) {
        val text = prompt.trim()
        if (text.isEmpty()) return
        scope.launch {
            try {
                if (followUp) {
                    runtime.followUp(sessionId, text, images)
                } else {
                    runtime.steer(sessionId, text, images)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableFailures.emit(ConversationFailure(sessionId, error.message.orEmpty()))
            }
        }
    }

    private fun mutate(sessionId: String, transform: (ConversationState) -> ConversationState) {
        synchronized(lock) {
            val flow = stateFor(sessionId)
            flow.value = transform(flow.value)
            trimLocked(sessionId)
        }
    }

    private fun flowFor(sessionId: String): MutableStateFlow<ConversationState> = synchronized(lock) {
        stateFor(sessionId)
    }

    private fun stateFor(sessionId: String): MutableStateFlow<ConversationState> =
        states.getOrPut(sessionId) { MutableStateFlow(ConversationState()) }

    private fun trimLocked(protect: String? = null) {
        while (states.size > maxSessions) {
            val protectedNow = pinned + loading + setOfNotNull(protect)
            val victim = states.keys.firstOrNull { it !in protectedNow } ?: return
            states.remove(victim)
            deferred.remove(victim)
            pendingDeltas.remove(victim)
            onEvict?.invoke(victim)
        }
    }

    private fun merge(first: RuntimeEvent, second: RuntimeEvent): RuntimeEvent? = when {
        first is RuntimeEvent.TextDelta && second is RuntimeEvent.TextDelta && first.runId == second.runId ->
            RuntimeEvent.TextDelta(first.runId, first.text + second.text)

        first is RuntimeEvent.ReasoningDelta && second is RuntimeEvent.ReasoningDelta && first.runId == second.runId ->
            RuntimeEvent.ReasoningDelta(first.runId, first.text + second.text)

        else -> null
    }

    private fun AssistantMessage.withLocalizedError(): AssistantMessage =
        rawError?.let { raw -> copy(error = localize(raw)) } ?: this

    private companion object {
        const val MAX_SESSIONS = 8
        const val DELTA_DELAY_MS = 16L
        const val RECONCILE_DELAY_MS = 500L
        const val COMPLETION_BUFFER = 128
        const val FAILURE_BUFFER = 64
    }
}
