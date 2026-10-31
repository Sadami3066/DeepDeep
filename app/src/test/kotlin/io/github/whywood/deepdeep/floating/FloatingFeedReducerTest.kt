package io.github.whywood.deepdeep.floating

import io.github.whywood.deepdeep.runtime.PendingQuestion
import io.github.whywood.deepdeep.runtime.QuestionOption
import io.github.whywood.deepdeep.runtime.RunStatus
import io.github.whywood.deepdeep.runtime.RuntimeActivity
import io.github.whywood.deepdeep.runtime.UserQuestion
import io.github.whywood.deepdeep.ui.chat.PresentedItem
import io.github.whywood.deepdeep.ui.chat.PresentedItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingFeedReducerTest {
    @Test
    fun questionCardOutranksDeliveryAndCompletion() {
        val state = baseState().copy(
            questions = listOf(pending("req-1")),
            deliveries = listOf(delivery("session-a", 1)),
            completions = listOf(completion("session-a", 1_000L, RunStatus.DONE)),
        )
        assertEquals(
            FloatingEventCard.Question::class.java,
            floatingEventCard(state, 1_500L)?.javaClass,
        )
    }

    @Test
    fun deliveryCardOutranksCompletion() {
        val state = baseState().copy(
            deliveries = listOf(delivery("session-a", 1)),
            completions = listOf(completion("session-a", 1_000L, RunStatus.DONE)),
        )
        assertTrue(floatingEventCard(state, 1_500L) is FloatingEventCard.Delivery)
    }

    @Test
    fun dismissedCardFallsThroughToNextCandidate() {
        val question = pending("req-1")
        val state = baseState().copy(
            questions = listOf(question),
            deliveries = listOf(delivery("session-a", 1)),
        )
        val dismissed = reduceFloatingFeed(
            state,
            FloatingFeedEvent.DismissCard("question:${question.requestId}"),
            1_100L,
        )
        assertTrue(floatingEventCard(dismissed, 1_200L) is FloatingEventCard.Delivery)
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
    fun unreadCountsQuestionsAndUnreadDeliveries() {
        val state = baseState().copy(
            questions = listOf(pending("req-1"), pending("req-2")),
            deliveries = listOf(delivery("session-a", 1), delivery("session-b", 2)),
        )
        assertEquals(4, floatingUnreadCount(state))
        val halfRead = reduceFloatingFeed(
            state,
            FloatingFeedEvent.MarkDeliveryRead(state.deliveries.first().key),
            1_100L,
        )
        assertEquals(3, floatingUnreadCount(halfRead))
        assertEquals(2, floatingUnreadCount(reduceFloatingFeed(halfRead, FloatingFeedEvent.MarkDeliveriesRead, 1_200L)))
    }

    @Test
    fun completionCardExpiresAfterSixSeconds() {
        val state = reduceFloatingFeed(
            baseState(),
            FloatingFeedEvent.Completed("session-a", "会话", RunStatus.DONE, "好了", null),
            1_000L,
        )
        assertTrue(floatingEventCard(state, 6_999L) is FloatingEventCard.Completion)
        assertNull(floatingEventCard(state, 7_000L))
        val pruned = reduceFloatingFeed(state, FloatingFeedEvent.Prune(8_000L), 8_000L)
        assertTrue(pruned.completions.isEmpty())
    }

    @Test
    fun sameDeliveryIsStoredOnce() {
        val items = listOf(item(PresentedItemType.FILE, "/tmp/report.md"))
        val first = reduceFloatingFeed(
            baseState(),
            FloatingFeedEvent.Delivered("session-a", "会话", items),
            1_000L,
        )
        val second = reduceFloatingFeed(
            first,
            FloatingFeedEvent.Delivered("session-a", "会话", items),
            2_000L,
        )
        assertEquals(1, second.deliveries.size)
        assertEquals(1_000L, second.deliveries.single().receivedAt)
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
        val unreadDelivery = baseState().copy(
            deliveries = listOf(delivery("session-a", 1)),
            lastEventAt = 1_000L,
        )
        assertEquals(FloatingWhaleMood.WAVE, floatingWhaleMood(unreadDelivery, 2_000L))
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

    private fun delivery(sessionId: String, receivedAt: Long): FloatingDelivery = FloatingDelivery(
        sessionId = sessionId,
        sessionTitle = "会话",
        items = listOf(item(PresentedItemType.FILE, "/tmp/$sessionId.md")),
        receivedAt = receivedAt,
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
