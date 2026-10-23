package io.github.whywood.deepdeep.runtime

import dev.pi.ai.RunSignal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuestionCenterTest {
    private fun question(id: String): UserQuestion = UserQuestion(
        id = id,
        header = "Header",
        question = "Question $id?",
        options = listOf(QuestionOption("A"), QuestionOption("B", "second")),
        multiSelect = false,
    )

    @Test
    fun answerCompletesTheSuspendedAsk() = runTest {
        val center = QuestionCenter { true }
        var outcome: QuestionOutcome? = null
        val job = launch {
            outcome = center.ask("call-1", listOf(question("q1")))
        }
        runCurrent()
        assertEquals(1, center.pending.value.size)
        val pending = center.pending.value.single()
        assertEquals("call-1", pending.toolCallId)
        assertEquals(listOf(question("q1")), pending.questions)

        val answers = listOf(QuestionAnswer(questionId = "q1", selected = listOf("A")))
        center.answer(pending.requestId, answers)
        job.join()

        val answered = outcome as QuestionOutcome.Answered
        assertEquals(answers, answered.answers)
        assertTrue(center.pending.value.isEmpty())
    }

    @Test
    fun cancelCompletesTheSuspendedAsk() = runTest {
        val center = QuestionCenter { true }
        var outcome: QuestionOutcome? = null
        val job = launch {
            outcome = center.ask("call-1", listOf(question("q1")))
        }
        runCurrent()
        center.cancel(center.pending.value.single().requestId)
        job.join()
        assertSame(QuestionOutcome.Cancelled, outcome)
        assertTrue(center.pending.value.isEmpty())
    }

    @Test
    fun askReturnsUnavailableWhenNotForeground() = runTest {
        val center = QuestionCenter { false }
        assertSame(QuestionOutcome.Unavailable, center.ask("call-1", listOf(question("q1"))))
        assertTrue(center.pending.value.isEmpty())
    }

    @Test
    fun abortSignalCancelsThePendingQuestion() = runTest {
        val center = QuestionCenter { true }
        val signal = RunSignal()
        var outcome: QuestionOutcome? = null
        val job = launch {
            outcome = center.ask("call-1", listOf(question("q1")), signal)
        }
        runCurrent()
        assertEquals(1, center.pending.value.size)

        signal.abort("user stopped the run")
        job.join()

        assertSame(QuestionOutcome.Cancelled, outcome)
        assertTrue(center.pending.value.isEmpty())
    }

    @Test
    fun pendingQuestionsAreFifo() = runTest {
        val center = QuestionCenter { true }
        var first: QuestionOutcome? = null
        var second: QuestionOutcome? = null
        val firstJob = launch {
            first = center.ask("call-1", listOf(question("q1")))
        }
        runCurrent()
        val secondJob = launch {
            second = center.ask("call-2", listOf(question("q2")))
        }
        runCurrent()

        val pending = center.pending.value
        assertEquals(2, pending.size)
        assertEquals("call-1", pending[0].toolCallId)
        assertEquals("call-2", pending[1].toolCallId)

        center.answer(pending[0].requestId, listOf(QuestionAnswer("q1", selected = listOf("A"))))
        firstJob.join()
        assertEquals(1, center.pending.value.size)
        assertEquals("call-2", center.pending.value.single().toolCallId)

        center.answer(pending[1].requestId, listOf(QuestionAnswer("q2", custom = "custom")))
        secondJob.join()
        assertTrue(center.pending.value.isEmpty())
    }

    @Test
    fun unknownRequestIdsAreIgnored() = runTest {
        val center = QuestionCenter { true }
        center.answer("unknown", emptyList())
        center.cancel("unknown")
        assertTrue(center.pending.value.isEmpty())
    }

    @Test
    fun skippedAnswersAreKeptInTheOutcome() = runTest {
        val center = QuestionCenter { true }
        val questions = listOf(question("q1"), question("q2"))
        var outcome: QuestionOutcome? = null
        val job = launch {
            outcome = center.ask("call-1", questions)
        }
        runCurrent()
        val answers = listOf(
            QuestionAnswer(questionId = "q1", selected = listOf("B")),
            QuestionAnswer(questionId = "q2", skipped = true),
        )
        center.answer(center.pending.value.single().requestId, answers)
        job.join()
        assertEquals(answers, (outcome as QuestionOutcome.Answered).answers)
    }
}
