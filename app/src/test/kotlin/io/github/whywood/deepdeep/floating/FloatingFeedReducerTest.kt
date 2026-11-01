package io.github.whywood.deepdeep.floating

import io.github.whywood.deepdeep.plugin.PluginSetupNotice
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
            floatingEventCard(state)?.javaClass,
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
        val card = floatingEventCard(state) as FloatingEventCard.Completed
        assertEquals("session-b", card.completion.sessionId)
    }

    @Test
    fun dismissedCompletedCardFallsThroughToNextCompleted() {
        val state = baseState().copy(
            completed = listOf(
                completion("session-b", 2_000L, RunStatus.DONE),
                completion("session-a", 1_000L, RunStatus.DONE),
            ),
        )
        val first = floatingEventCard(state) as FloatingEventCard.Completed
        assertEquals("session-b", first.completion.sessionId)
        val dismissed = reduceFloatingFeed(state, FloatingFeedEvent.DismissCard(first.key), 3_000L)
        val next = floatingEventCard(dismissed) as FloatingEventCard.Completed
        assertEquals("session-a", next.completion.sessionId)
    }

    @Test
    fun dismissedCompletedCardLeavesNoCardAndKeepsPanelEntry() {
        val state = reduceFloatingFeed(
            baseState(),
            FloatingFeedEvent.Completed("session-a", "会话", RunStatus.DONE, "好了", null),
            1_000L,
        )
        val key = state.completed.single().key
        val dismissed = reduceFloatingFeed(state, FloatingFeedEvent.DismissCard(key), 1_100L)
        assertNull(floatingEventCard(dismissed))
        assertEquals(1, dismissed.completed.size)
        val dismissedTwice = reduceFloatingFeed(dismissed, FloatingFeedEvent.DismissCard(key), 2_000L)
        assertNull(floatingEventCard(dismissedTwice))
        assertEquals(setOf(key), dismissedTwice.dismissedCardKeys)
        assertEquals(1, floatingPendingCount(dismissedTwice))
    }

    @Test
    fun foregroundCompletionKeepsPanelEntryWithoutCard() {
        val state = reduceFloatingFeed(
            baseState(),
            FloatingFeedEvent.Completed("session-a", "会话", RunStatus.DONE, "好了", null, whileForeground = true),
            1_000L,
        )
        assertEquals(1, state.completed.size)
        assertNull(floatingEventCard(state))
        assertEquals(1, floatingPendingCount(state))
        assertTrue(state.completed.single().key in state.dismissedCardKeys)
    }

    @Test
    fun backgroundCompletionQueuesCard() {
        val state = reduceFloatingFeed(
            baseState(),
            FloatingFeedEvent.Completed("session-a", "会话", RunStatus.DONE, "好了", null),
            1_000L,
        )
        assertTrue(floatingEventCard(state) is FloatingEventCard.Completed)
        assertTrue(state.dismissedCardKeys.isEmpty())
    }

    @Test
    fun eventQueuePutsQuestionsBeforeCompletedInPendingOrder() {
        val state = baseState().copy(
            questions = listOf(pending("req-1"), pending("req-2", sessionId = "session-b")),
            completed = listOf(
                completion("session-c", 2_000L, RunStatus.DONE),
                completion("session-d", 1_000L, RunStatus.DONE),
            ),
        )
        val queue = floatingEventQueue(state)
        assertEquals(4, queue.size)
        assertEquals("req-1", (queue[0] as FloatingEventCard.Question).pending.requestId)
        assertEquals("req-2", (queue[1] as FloatingEventCard.Question).pending.requestId)
        assertEquals("session-c", (queue[2] as FloatingEventCard.Completed).completion.sessionId)
        assertEquals("session-d", (queue[3] as FloatingEventCard.Completed).completion.sessionId)
        assertEquals(queue.first(), floatingEventCard(state))
    }

    @Test
    fun eventQueueSkipsDismissedAndRemovedCards() {
        val first = reduceFloatingFeed(
            baseState(),
            FloatingFeedEvent.Completed("session-b", "会话", RunStatus.DONE, "B", null),
            1_000L,
        )
        var state = reduceFloatingFeed(
            first,
            FloatingFeedEvent.Completed("session-a", "会话", RunStatus.DONE, "A", null),
            2_000L,
        )
        val head = state.completed.first()
        state = reduceFloatingFeed(state, FloatingFeedEvent.DismissCard(head.key), 3_000L)
        val queue = floatingEventQueue(state)
        assertEquals(1, queue.size)
        assertEquals("session-b", (queue.single() as FloatingEventCard.Completed).completion.sessionId)
        val removed = reduceFloatingFeed(
            state,
            FloatingFeedEvent.RemoveCompleted(state.completed.last().key),
            4_000L,
        )
        assertTrue(floatingEventQueue(removed).isEmpty())
    }

    @Test
    fun answeredQuestionLeavesQueueAndNextCardTakesOver() {
        var state = baseState().copy(
            questions = listOf(pending("req-1"), pending("req-2")),
            completed = listOf(completion("session-a", 1_000L, RunStatus.DONE)),
        )
        assertEquals(3, floatingEventQueue(state).size)
        state = reduceFloatingFeed(state, FloatingFeedEvent.QuestionsChanged(emptyList()), 2_000L)
        assertEquals(1, floatingEventQueue(state).size)
        assertTrue(floatingEventCard(state) is FloatingEventCard.Completed)
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
    fun completedCardPersistsUntilDismissed() {
        val state = reduceFloatingFeed(
            baseState(),
            FloatingFeedEvent.Completed("session-a", "会话", RunStatus.DONE, "好了", null),
            1_000L,
        )
        assertTrue(floatingEventCard(state) is FloatingEventCard.Completed)
        assertTrue(floatingEventCard(state.copy(lastEventAt = 0L)) is FloatingEventCard.Completed)
        assertEquals(1, state.completed.size)
    }

    @Test
    fun openingCompletedEntryAdvancesToNextCard() {
        val first = reduceFloatingFeed(
            baseState(),
            FloatingFeedEvent.Completed("session-b", "会话", RunStatus.DONE, "B", null),
            1_000L,
        )
        val state = reduceFloatingFeed(
            first,
            FloatingFeedEvent.Completed("session-a", "会话", RunStatus.DONE, "A", null),
            2_000L,
        )
        val card = floatingEventCard(state) as FloatingEventCard.Completed
        assertEquals("session-a", card.completion.sessionId)
        val removed = reduceFloatingFeed(state, FloatingFeedEvent.RemoveCompleted(card.key), 3_000L)
        val next = floatingEventCard(removed) as FloatingEventCard.Completed
        assertEquals("session-b", next.completion.sessionId)
    }

    @Test
    fun dismissedKeysArePrunedWhenCompletedEntryIsReplaced() {
        val first = reduceFloatingFeed(
            baseState(),
            FloatingFeedEvent.Completed("session-a", "会话", RunStatus.DONE, "第一次", null),
            1_000L,
        )
        val key = first.completed.single().key
        val dismissed = reduceFloatingFeed(first, FloatingFeedEvent.DismissCard(key), 1_100L)
        assertEquals(setOf(key), dismissed.dismissedCardKeys)
        val replaced = reduceFloatingFeed(
            dismissed,
            FloatingFeedEvent.Completed("session-a", "会话", RunStatus.DONE, "第二次", null),
            2_000L,
        )
        assertTrue(replaced.dismissedCardKeys.isEmpty())
        assertTrue(floatingEventCard(replaced) is FloatingEventCard.Completed)
    }

    @Test
    fun dismissedKeysAreCapped() {
        var state = baseState()
        repeat(FLOATING_DISMISSED_LIMIT + 5) { index ->
            state = reduceFloatingFeed(state, FloatingFeedEvent.DismissCard("card-$index"), 1_000L + index)
        }
        assertEquals(FLOATING_DISMISSED_LIMIT, state.dismissedCardKeys.size)
        assertFalse("card-0" in state.dismissedCardKeys)
        assertTrue("card-${FLOATING_DISMISSED_LIMIT + 4}" in state.dismissedCardKeys)
    }

    @Test
    fun answeredQuestionRevealsPendingCompletedCard() {
        val state = baseState().copy(
            questions = listOf(pending("req-1")),
            completed = listOf(completion("session-a", 1_000L, RunStatus.DONE)),
        )
        assertTrue(floatingEventCard(state) is FloatingEventCard.Question)
        val answered = reduceFloatingFeed(state, FloatingFeedEvent.QuestionsChanged(emptyList()), 2_000L)
        assertTrue(floatingEventCard(answered) is FloatingEventCard.Completed)
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
        assertNull(floatingEventCard(state))
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

    @Test
    fun pluginSetupCardAppearsAndClears() {
        val notice = PluginSetupNotice("micloud", "小米云服务", "等待扫码")
        val state = reduceFloatingFeed(baseState(), FloatingFeedEvent.SetupChanged(notice), 2_000L)
        val card = floatingEventCard(state) as FloatingEventCard.PluginSetup
        assertEquals("micloud", card.notice.pluginId)
        assertEquals(FloatingWhaleMood.WAVE, floatingWhaleMood(state, 2_100L))

        val dismissed = reduceFloatingFeed(state, FloatingFeedEvent.DismissCard(card.key), 2_200L)
        assertNull(floatingEventCard(dismissed))

        val cleared = reduceFloatingFeed(dismissed, FloatingFeedEvent.SetupChanged(null), 2_300L)
        assertNull(cleared.setup)
        assertTrue(cleared.dismissedCardKeys.none { it.startsWith(PLUGIN_SETUP_CARD_PREFIX) })
    }

    @Test
    fun pluginSetupCardRanksBelowQuestionAndAboveCompleted() {
        val state = baseState().copy(
            setup = PluginSetupNotice("micloud", "小米云服务", "等待扫码"),
            completed = listOf(completion("session-a", 1_000L, RunStatus.DONE)),
        )
        assertEquals(FloatingEventCard.PluginSetup::class.java, floatingEventCard(state)?.javaClass)

        val withQuestion = state.copy(questions = listOf(pending("req-1")))
        assertEquals(FloatingEventCard.Question::class.java, floatingEventCard(withQuestion)?.javaClass)
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
