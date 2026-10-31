package io.github.whywood.deepdeep.floating

import io.github.whywood.deepdeep.runtime.ConversationQueries
import io.github.whywood.deepdeep.runtime.PendingQuestion
import io.github.whywood.deepdeep.runtime.RunStatus
import io.github.whywood.deepdeep.runtime.RuntimeEvent
import io.github.whywood.deepdeep.runtime.SessionRuntimeEvent
import io.github.whywood.deepdeep.service.ActiveRunView
import io.github.whywood.deepdeep.ui.chat.parsePresentedItems
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FloatingFeed(
    private val runtime: ConversationQueries,
    private val activeRuns: StateFlow<Map<String, ActiveRunView>>,
    private val pendingQuestions: StateFlow<List<PendingQuestion>>,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(
        FloatingFeedState(lastEventAt = System.currentTimeMillis()),
    )
    val state: StateFlow<FloatingFeedState> = mutableState.asStateFlow()

    private val jobs = mutableListOf<Job>()

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
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
    }

    fun dismissCard(key: String) {
        dispatch(FloatingFeedEvent.DismissCard(key))
    }

    fun removeCompleted(key: String) {
        dispatch(FloatingFeedEvent.RemoveCompleted(key))
    }

    private suspend fun onRuntimeEvent(event: SessionRuntimeEvent) {
        val sessionId = event.sessionId.takeIf { it.isNotBlank() } ?: return
        when (val runtimeEvent = event.event) {
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
                ),
            )

            is RuntimeEvent.RunFailed -> dispatch(
                FloatingFeedEvent.Completed(
                    sessionId = sessionId,
                    title = resolveTitle(sessionId),
                    status = RunStatus.FAILED,
                    text = "",
                    error = runtimeEvent.message,
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

    private fun dispatch(event: FloatingFeedEvent) {
        mutableState.update { current ->
            reduceFloatingFeed(current, event, System.currentTimeMillis())
        }
    }
}

internal fun ActiveRunView.toFloatingRun(): FloatingRun = FloatingRun(
    sessionId = sessionId,
    title = title,
    activity = activity,
    startedAt = startedAt,
)
