package io.github.whywood.deepdeep.floating

import io.github.whywood.deepdeep.runtime.ConversationQueries
import io.github.whywood.deepdeep.runtime.PendingQuestion
import io.github.whywood.deepdeep.runtime.RunStatus
import io.github.whywood.deepdeep.runtime.RuntimeActivity
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import io.github.whywood.deepdeep.runtime.SessionRuntimeEvent
import io.github.whywood.deepdeep.conversation.ActiveRunView
import io.github.whywood.deepdeep.ui.common.chat.parsePresentedItems
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

internal const val FLOATING_TRACKING_TEXT_THROTTLE_MS = 200L
internal const val FLOATING_TRACKED_TEXT_LIMIT = 4096

class FloatingFeed(
    private val runtime: ConversationQueries,
    private val activeRuns: StateFlow<Map<String, ActiveRunView>>,
    private val pendingQuestions: StateFlow<List<PendingQuestion>>,
    private val scope: CoroutineScope,
    private val isForeground: () -> Boolean = { false },
    private val events: kotlinx.coroutines.flow.SharedFlow<SessionRuntimeEvent> = runtime.events,
) {
    private val mutableState = MutableStateFlow(
        FloatingFeedState(lastEventAt = System.currentTimeMillis()),
    )
    val state: StateFlow<FloatingFeedState> = mutableState.asStateFlow()

    private val jobs = mutableListOf<Job>()
    private val textStream = TrackedStream { sessionId, text ->
        dispatch(FloatingFeedEvent.TrackingText(sessionId, text))
    }
    private val reasoningStream = TrackedStream { sessionId, text ->
        dispatch(FloatingFeedEvent.TrackingReasoning(sessionId, text))
    }

    fun start() {
        if (jobs.isNotEmpty()) return
        jobs += scope.launch {
            activeRuns.collect { runs ->
                dispatch(FloatingFeedEvent.RunsChanged(runs.values.map(ActiveRunView::toFloatingRun)))
            }
        }
        jobs += scope.launch {
            pendingQuestions.collect { questions ->
                dispatch(FloatingFeedEvent.QuestionsChanged(questions))
            }
        }
        jobs += scope.launch {
            events.collect(::onRuntimeEvent)
        }
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        textStream.stop()
        reasoningStream.stop()
    }

    fun dismissCard(key: String) {
        dispatch(FloatingFeedEvent.DismissCard(key))
    }

    fun removeCompleted(key: String) {
        dispatch(FloatingFeedEvent.RemoveCompleted(key))
    }

    fun subscribe(sessionId: String) {
        dispatch(FloatingFeedEvent.Subscribe(sessionId))
    }

    private suspend fun onRuntimeEvent(event: SessionRuntimeEvent) {
        val sessionId = event.sessionId.takeIf { it.isNotBlank() } ?: return
        when (val runtimeEvent = event.event) {
            is RuntimeEvent.RunStarted -> resetTrackedText(sessionId)

            is RuntimeEvent.UserInputAdmitted -> resetTrackedText(sessionId)

            is RuntimeEvent.ActivityChanged -> trackActivity(sessionId, runtimeEvent.activity)

            is RuntimeEvent.ToolCallStarted -> Unit

            is RuntimeEvent.ToolStarted -> {
                trackActivity(sessionId, RuntimeActivity.RunningTool(runtimeEvent.name))
                trackTool(sessionId, runtimeEvent.name)
            }

            is RuntimeEvent.ReasoningDelta -> appendTrackedReasoning(sessionId, runtimeEvent.text)

            is RuntimeEvent.TextDelta -> appendTrackedText(sessionId, runtimeEvent.text)

            is RuntimeEvent.ToolFinished -> {
                if (runtimeEvent.isError || runtimeEvent.name != "present") return
                val items = parsePresentedItems(runtimeEvent.result) ?: return
                dispatch(FloatingFeedEvent.Delivered(sessionId = sessionId, items = items))
            }

            is RuntimeEvent.RunFinished -> dispatch(
                FloatingFeedEvent.Completed(
                    sessionId = sessionId,
                    title = resolveTitle(sessionId),
                    status = runtimeEvent.status,
                    text = runtimeEvent.text,
                    error = null,
                    whileForeground = isForeground(),
                ),
            )

            is RuntimeEvent.RunFailed -> dispatch(
                FloatingFeedEvent.Completed(
                    sessionId = sessionId,
                    title = resolveTitle(sessionId),
                    status = RunStatus.FAILED,
                    text = "",
                    error = runtimeEvent.message,
                    whileForeground = isForeground(),
                ),
            )

            else -> Unit
        }
    }

    private suspend fun resolveTitle(sessionId: String): String =
        mutableState.value.sessionTitles[sessionId]?.takeIf { it.isNotBlank() }
            ?: activeRuns.value[sessionId]?.title?.takeIf { it.isNotBlank() }
            ?: runCatching { runtime.sessionTitle(sessionId) }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: ""

    private fun trackActivity(sessionId: String, activity: RuntimeActivity?) {
        if (mutableState.value.subscription?.sessionId != sessionId) return
        dispatch(FloatingFeedEvent.TrackingActivity(sessionId, activity))
    }

    private fun trackTool(sessionId: String, tool: String) {
        if (mutableState.value.subscription?.sessionId != sessionId) return
        dispatch(FloatingFeedEvent.TrackingTool(sessionId, tool))
    }

    private fun appendTrackedText(sessionId: String, delta: String) {
        if (mutableState.value.subscription?.sessionId != sessionId) return
        textStream.append(sessionId, delta)
    }

    private fun appendTrackedReasoning(sessionId: String, delta: String) {
        if (mutableState.value.subscription?.sessionId != sessionId) return
        reasoningStream.append(sessionId, delta)
    }

    private fun resetTrackedText(sessionId: String) {
        if (mutableState.value.subscription?.sessionId != sessionId) return
        textStream.reset(sessionId)
        reasoningStream.reset(sessionId)
        dispatch(FloatingFeedEvent.TrackingReset(sessionId))
    }

    private fun dispatch(event: FloatingFeedEvent) {
        val next = mutableState.updateAndGet { current ->
            reduceFloatingFeed(current, event, System.currentTimeMillis())
        }
        val subscriptionId = next.subscription?.sessionId
        textStream.select(subscriptionId)
        reasoningStream.select(subscriptionId)
    }

    private inner class TrackedStream(
        private val emit: (sessionId: String, text: String) -> Unit,
    ) {
        private val lock = Any()
        private var sessionId: String? = null
        private var buffer = ""
        private var job: Job? = null

        fun append(sessionId: String, delta: String) {
            synchronized(lock) {
                if (this.sessionId != sessionId) {
                    this.sessionId = sessionId
                    buffer = ""
                }
                buffer = (buffer + delta).takeLast(FLOATING_TRACKED_TEXT_LIMIT)
            }
            if (job?.isActive == true) return
            job = scope.launch {
                delay(FLOATING_TRACKING_TEXT_THROTTLE_MS)
                val active = mutableState.value.subscription?.sessionId ?: return@launch
                val text = synchronized(lock) {
                    if (this@TrackedStream.sessionId == active) buffer else null
                }
                if (!text.isNullOrEmpty()) emit(active, text)
            }
        }

        fun reset(sessionId: String) {
            synchronized(lock) {
                if (this.sessionId == sessionId) buffer = ""
            }
        }

        fun select(sessionId: String?) {
            synchronized(lock) {
                if (this.sessionId != sessionId) {
                    this.sessionId = sessionId
                    buffer = ""
                }
            }
        }

        fun stop() {
            job?.cancel()
            job = null
        }
    }
}

internal fun ActiveRunView.toFloatingRun(): FloatingRun = FloatingRun(
    sessionId = sessionId,
    title = title,
    activity = activity,
    startedAt = startedAt,
)
