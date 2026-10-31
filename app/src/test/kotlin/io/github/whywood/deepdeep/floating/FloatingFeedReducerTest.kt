package io.github.whywood.deepdeep.floating

import io.github.whywood.deepdeep.runtime.PendingQuestion
import io.github.whywood.deepdeep.runtime.QuestionOption
import io.github.whywood.deepdeep.runtime.RunStatus
import io.github.whywood.deepdeep.runtime.RuntimeActivity
import io.github.whywood.deepdeep.runtime.UserQuestion
import io.github.whywood.deepdeep.ui.chat.PresentedItem
import io.github.whywood.deepdeep.ui.chat.PresentedItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingFeedReducerTest {
    @Test
    fun questionCardOutranksCompleted() {
        val state = baseState().copy(
            questions = listOf(pending("req-1")),
            completed = listOf(completion("session-a", 1_000L, RunStatus.DONE)),
        )
        assertEquals(
            FloatingEventCard.Question::class.java,
            floatingEventCard(state, 1_500L)?.javaClass,
        )
    }

    @Test
    fun completedCardOutranksOlderCompleted() {
        val state = baseState().copy(
            completed = listOf(
                completion("session-b", 2_000L, RunStatus.DONE),
                completion("session-a", 1_000L, RunStatus.DONE),
            ),
        )
        val card = floatingEventCard(state, 2_500L) as FloatingEventCard.Completed
        assertEquals("session-b", card.completion.sessionId)
    }

    @Test
    fun dismissedCardFallsThroughToNextCandidate() {
        val question = pending("req-1")
        val state = baseState().copy(
            questions = listOf(question),
            completed = listOf(completion("session-a", 1_000L, RunStatus.DONE)),
        )
        val dismissed = reduceFloatingFeed(
            state,
            FloatingFeedEvent.DismissCard("question:${question.requestId}"),
            1_100L,
        )
        assertTrue(floatingEventCard(dismissed, 1_200L) is FloatingEventCard.Completed)
    }

    @Test
    fun dismissedOnlyCardLeavesNoCard() {
        val question = pending("req-1")
        val state = reduceFloatingFeed(
            baseState().copy(questions = listOf(question)),
            FloatingFeedEvent.DismissCard("question:${question.requestId}"),
            1_100L,
        )
        assertNull(floatingEventCard(state, 1_200L))
    }

    @Test
    fun pendingCountCountsQuestionsAndCompleted() {
        val state = baseState().copy(
            questions = listOf(pending("req-1"), pending("req-2")),
            completed = listOf(
                completion("session-a", 1_000L, RunStatus.DONE),
                completion("session-b", 1_500L, RunStatus.FAILED),
            ),
        )
        assertEquals(4, floatingPendingCount(state))
        val removed = reduceFloatingFeed(
            state,
            FloatingFeedEvent.RemoveCompleted(state.completed.first().key),
            1_600L,
        )
        assertEquals(3, floatingPendingCount(removed))
    }

    @Test
    fun completedCardExpiresAfterSixSeconds() {
        val state = reduceFloatingFeed(
            baseState(),
            FloatingFeedEvent.Completed("session-a", "会话", RunStatus.DONE, "好了", null),
            1_000L,
        )
        assertTrue(floatingEventCard(state, 6_999L) is FloatingEventCard.Completed)
        assertNull(floatingEventCard(state, 7_000L))
        assertEquals(1, state.completed.size)
    }

    @Test
    fun completedEntryPersistsUntilRemoved() {
        val state = reduceFloatingFeed(
            baseState(),
            FloatingFeedEvent.Completed("session-a", "会话", RunStatus.STOPPED, "", null),
            1_000L,
        )
        val removed = reduceFloatingFeed(
            state,
            FloatingFeedEvent.RemoveCompleted(state.completed.single().key),
            60_000L,
        )
        assertTrue(removed.completed.isEmpty())
        assertEquals(0, floatingPendingCount(removed))
    }

    @Test
    fun completedWithDeliveryCarriesPresentItems() {
        val items = listOf(item(PresentedItemType.FILE, "/tmp/report.md", title = "季度报告"))
        val delivered = reduceFloatingFeed(
            baseState(),
            FloatingFeedEvent.Delivered("session-a", items),
            500L,
        )
        assertEquals(items, delivered.stagedDeliveries["session-a"])

        val completed = reduceFloatingFeed(
            delivered,
            FloatingFeedEvent.Completed("session-a", "会话", RunStatus.DONE, "好了", null),
            1_000L,
        )
        val entry = completed.completed.single()
        assertTrue(entry.hasDelivery)
        assertEquals(items, entry.items)
        assertEquals("季度报告", entry.deliveryLabel)
        assertTrue(completed.stagedDeliveries.isEmpty())
    }

    @Test
    fun deliveredWithoutCompletionCreatesNoEntry() {
        val state = reduceFloatingFeed(
            baseState(),
            FloatingFeedEvent.Delivered(
                "session-a",
                listOf(item(PresentedItemType.FILE, "/tmp/report.md")),
            ),
            1_000L,
        )
        assertTrue(state.completed.isEmpty())
        assertEquals(0, floatingPendingCount(state))
    }

    @Test
    fun sameSessionCompletionReplacesOlderEntry() {
        val first = reduceFloatingFeed(
            baseState(),
            FloatingFeedEvent.Completed("session-a", "会话", RunStatus.DONE, "第一次", null),
            1_000L,
        )
        val second = reduceFloatingFeed(
            first,
            FloatingFeedEvent.Completed("session-a", "会话", RunStatus.DONE, "第二次", null),
            2_000L,
        )
        val entry = second.completed.single()
        assertEquals("第二次", entry.text)
        assertEquals(2_000L, entry.finishedAt)
    }

    @Test
    fun completedListKeepsNewestFirstAndDropsOverflow() {
        var state = baseState()
        repeat(FLOATING_COMPLETED_LIMIT + 3) { index ->
            state = reduceFloatingFeed(
                state,
                FloatingFeedEvent.Completed("session-$index", "会话", RunStatus.DONE, "", null),
                1_000L + index,
            )
        }
        assertEquals(FLOATING_COMPLETED_LIMIT, state.completed.size)
        assertEquals(
            "session-${FLOATING_COMPLETED_LIMIT + 2}",
            state.completed.first().sessionId,
        )
        assertEquals("session-3", state.completed.last().sessionId)
    }

    @Test
    fun runningSessionCompletionIsHidden() {
        val state = reduceFloatingFeed(
            baseState(),
            FloatingFeedEvent.Completed("session-a", "会话", RunStatus.DONE, "好了", null),
            1_000L,
        ).copy(runs = listOf(run("session-a", "会话", 1_000L)))
        assertTrue(floatingVisibleCompletions(state).isEmpty())
        assertEquals(0, floatingPendingCount(state))
        assertNull(floatingEventCard(state, 1_100L))
    }

    @Test
    fun runsChangedSortsByStartAndKeepsSessionTitles() {
        val state = reduceFloatingFeed(
            baseState(),
            FloatingFeedEvent.RunsChanged(
                listOf(
                    run("session-b", "第二个", 2_000L),
                    run("session-a", "第一个", 1_000L),
                ),
            ),
            3_000L,
        )
        assertEquals(listOf("session-a", "session-b"), state.runs.map { it.sessionId })
        assertEquals("第一个", state.sessionTitles["session-a"])
        assertEquals(3_000L, state.lastEventAt)
    }

    @Test
    fun repeatedRunsSnapshotKeepsLastEventTimestamp() {
        val runs = listOf(run("session-a", "会话", 1_000L))
        val first = reduceFloatingFeed(baseState(), FloatingFeedEvent.RunsChanged(runs), 2_000L)
        val second = reduceFloatingFeed(first, FloatingFeedEvent.RunsChanged(runs), 5_000L)
        assertEquals(2_000L, second.lastEventAt)
    }

    @Test
    fun questionsChangedOnlyBumpsTimestampWhenPendingSetChanges() {
        val first = reduceFloatingFeed(
            baseState(),
            FloatingFeedEvent.QuestionsChanged(listOf(pending("req-1"))),
            2_000L,
        )
        val same = reduceFloatingFeed(
            first,
            FloatingFeedEvent.QuestionsChanged(listOf(pending("req-1"))),
            5_000L,
        )
        assertEquals(2_000L, same.lastEventAt)
        val changed = reduceFloatingFeed(
            first,
            FloatingFeedEvent.QuestionsChanged(emptyList()),
            6_000L,
        )
        assertEquals(6_000L, changed.lastEventAt)
    }

    @Test
    fun whaleMoodFollowsRunThenPendingThenIdleThenSleepy() {
        val running = baseState().copy(
            runs = listOf(run("session-a", "会话", 1_000L)),
            questions = listOf(pending("req-1")),
            lastEventAt = 1_000L,
        )
        assertEquals(FloatingWhaleMood.THINKING, floatingWhaleMood(running, 2_000L))
        val waiting = baseState().copy(
            questions = listOf(pending("req-1")),
            lastEventAt = 1_000L,
        )
        assertEquals(FloatingWhaleMood.WAVE, floatingWhaleMood(waiting, 2_000L))
        val completed = baseState().copy(
            completed = listOf(completion("session-a", 1_000L, RunStatus.DONE)),
            lastEventAt = 1_000L,
        )
        assertEquals(FloatingWhaleMood.WAVE, floatingWhaleMood(completed, 2_000L))
        val idle = baseState().copy(lastEventAt = 1_000L)
        assertEquals(FloatingWhaleMood.IDLE, floatingWhaleMood(idle, 1_000L + FLOATING_SLEEPY_AFTER_MS - 1))
        assertEquals(FloatingWhaleMood.SLEEPY, floatingWhaleMood(idle, 1_000L + FLOATING_SLEEPY_AFTER_MS))
    }

    @Test
    fun inlineOptionsOnlyForSingleChoiceQuestions() {
        val singleChoice = pending("req-1")
        assertTrue(floatingInlineOptions(singleChoice)?.isNotEmpty() == true)
        val multiSelect = pending(
            "req-2",
            questions = listOf(question("q", options = listOf(QuestionOption("A"), QuestionOption("B")), multiSelect = true)),
        )
        assertNull(floatingInlineOptions(multiSelect))
        val freeText = pending("req-3", questions = listOf(question("q", options = emptyList())))
        assertNull(floatingInlineOptions(freeText))
        val multiQuestion = pending(
            "req-4",
            questions = listOf(question("q1"), question("q2")),
        )
        assertNull(floatingInlineOptions(multiQuestion))
    }

    @Test
    fun elapsedSplitsHoursMinutesAndSeconds() {
        assertEquals(FloatingElapsed(0, 0, 0), floatingElapsed(-500L))
        assertEquals(FloatingElapsed(0, 0, 59), floatingElapsed(59_999L))
        assertEquals(FloatingElapsed(0, 1, 1), floatingElapsed(61_000L))
        assertEquals(FloatingElapsed(2, 3, 4), floatingElapsed(7_384_000L))
    }

    @Test
    fun deliveryLabelPrefersTitleThenName() {
        val titled = item(PresentedItemType.FILE, "/tmp/report.md", title = "季度报告")
        assertEquals("季度报告", deliveryItemLabel(titled))
        val untitled = item(PresentedItemType.FILE, "/tmp/report.md")
        assertEquals("report.md", deliveryItemLabel(untitled))
        val entry = completion("session-a", 1_000L, RunStatus.DONE).copy(items = listOf(untitled))
        assertEquals("report.md", entry.deliveryLabel)
        assertFalse(entry.key.isBlank())
    }

    private fun baseState(): FloatingFeedState = FloatingFeedState(lastEventAt = 1_000L)

    private fun run(sessionId: String, title: String, startedAt: Long): FloatingRun = FloatingRun(
        sessionId = sessionId,
        title = title,
        activity = RuntimeActivity.Thinking,
        startedAt = startedAt,
    )

    private fun pending(
        requestId: String,
        sessionId: String = "session-a",
        questions: List<UserQuestion> = listOf(question("question-$requestId")),
    ): PendingQuestion = PendingQuestion(
        requestId = requestId,
        sessionId = sessionId,
        toolCallId = "call-$requestId",
        questions = questions,
    )

    private fun question(
        id: String,
        options: List<QuestionOption> = listOf(QuestionOption("是"), QuestionOption("否")),
        multiSelect: Boolean = false,
    ): UserQuestion = UserQuestion(
        id = id,
        header = "确认",
        question = "要继续吗？",
        options = options,
        multiSelect = multiSelect,
    )

    private fun completion(sessionId: String, finishedAt: Long, status: RunStatus): FloatingCompletion =
        FloatingCompletion(
            sessionId = sessionId,
            title = "会话",
            status = status,
            text = "完成了",
            error = null,
            finishedAt = finishedAt,
        )

    private fun item(
        type: PresentedItemType,
        path: String,
        title: String? = null,
    ): PresentedItem = PresentedItem(
        type = type,
        name = path.substringAfterLast('/'),
        path = path,
        title = title,
    )
}
