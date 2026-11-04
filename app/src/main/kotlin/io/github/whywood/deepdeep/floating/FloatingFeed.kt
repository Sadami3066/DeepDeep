package io.github.whywood.deepdeep.floating

import io.github.whywood.deepdeep.plugin.PluginSetupNotice
import io.github.whywood.deepdeep.runtime.ConversationQueries
import io.github.whywood.deepdeep.runtime.PendingQuestion
import io.github.whywood.deepdeep.runtime.RunStatus
import io.github.whywood.deepdeep.runtime.RuntimeActivity
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import io.github.whywood.deepdeep.runtime.SessionRuntimeEvent
import io.github.whywood.deepdeep.service.ActiveRunView
import io.github.whywood.deepdeep.ui.chat.parsePresentedItems
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

internal const val FLOATING_TRACKING_TEXT_THROTTLE_MS = 200L

class FloatingFeed(
    private val runtime: ConversationQueries,
    private val activeRuns: StateFlow<Map<String, ActiveRunView>>,
    private val pendingQuestions: StateFlow<List<PendingQuestion>>,
    private val scope: CoroutineScope,
    private val pluginSetup: StateFlow<PluginSetupNotice?> = MutableStateFlow(null),
    private val isForeground: () -> Boolean = { false },
) {
    private val mutableState = MutableStateFlow(
        FloatingFeedState(lastEventAt = System.currentTimeMillis()),
    )
    val state: StateFlow<FloatingFeedState> = mutableState.asStateFlow()

    private val jobs = mutableListOf<Job>()
    private val textLock = Any()
    private var lastText = ""
    private var lastTextSessionId: String? = null
    private var textJob: Job? = null

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
            runtime.events.collect(::onRuntimeEvent)
        }
        jobs += scope.launch {
            pluginSetup.collect { notice ->
                dispatch(FloatingFeedEvent.SetupChanged(notice))
            }
        }
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        textJob?.cancel()
        textJob = null
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

            is RuntimeEvent.ToolStarted -> trackActivity(
                sessionId,
                RuntimeActivity.RunningTool(runtimeEvent.name),
            )

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

    private fun appendTrackedText(sessionId: String, delta: String) {
        if (mutableState.value.subscription?.sessionId != sessionId) return
        synchronized(textLock) {
            if (lastTextSessionId != sessionId) {
                lastTextSessionId = sessionId
                lastText = ""
            }
            lastText += delta
        }
        if (textJob?.isActive == true) return
        textJob = scope.launch {
            delay(FLOATING_TRACKING_TEXT_THROTTLE_MS)
            val subscriptionId = mutableState.value.subscription?.sessionId ?: return@launch
            val text = synchronized(textLock) {
                lastText.takeIf { lastTextSessionId == subscriptionId }
            }
            if (!text.isNullOrEmpty()) {
                dispatch(FloatingFeedEvent.TrackingText(subscriptionId, text))
            }
        }
    }

    private fun resetTrackedText(sessionId: String) {
        if (mutableState.value.subscription?.sessionId != sessionId) return
        synchronized(textLock) {
            if (lastTextSessionId == sessionId) lastText = ""
        }
        dispatch(FloatingFeedEvent.TrackingReset(sessionId))
    }

    private fun dispatch(event: FloatingFeedEvent) {
        val next = mutableState.updateAndGet { current ->
            reduceFloatingFeed(current, event, System.currentTimeMillis())
        }
        val subscriptionId = next.subscription?.sessionId
        synchronized(textLock) {
            if (lastTextSessionId != subscriptionId) {
                lastTextSessionId = subscriptionId
                lastText = ""
            }
        }
    }
}

internal fun ActiveRunView.toFloatingRun(): FloatingRun = FloatingRun(
    sessionId = sessionId,
    title = title,
    activity = activity,
    startedAt = startedAt,
)
