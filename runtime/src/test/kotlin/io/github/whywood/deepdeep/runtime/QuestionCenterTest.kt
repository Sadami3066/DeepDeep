package io.github.whywood.deepdeep.runtime

import dev.pi.ai.RunSignal
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
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
        val center = QuestionCenter(isForeground = { true })
        var outcome: QuestionOutcome? = null
        val job = launch {
            outcome = center.ask("session-1", "call-1", listOf(question("q1")))
        }
        runCurrent()
        assertEquals(1, center.pending.value.size)
        val pending = center.pending.value.single()
        assertEquals("session-1", pending.sessionId)
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
        val center = QuestionCenter(isForeground = { true })
        var outcome: QuestionOutcome? = null
        val job = launch {
            outcome = center.ask("session-1", "call-1", listOf(question("q1")))
        }
        runCurrent()
        center.cancel(center.pending.value.single().requestId)
        job.join()
        assertSame(QuestionOutcome.Cancelled, outcome)
        assertTrue(center.pending.value.isEmpty())
    }

    @Test
    fun askReturnsUnavailableWhenNotForeground() = runTest {
        val center = QuestionCenter(isForeground = { false })
        assertSame(QuestionOutcome.Unavailable, center.ask("session-1", "call-1", listOf(question("q1"))))
        assertTrue(center.pending.value.isEmpty())
    }

    @Test
    fun loadPersistedTurnsPendingQuestionsIntoInterruptedOnes() = runTest {
        val directory = Files.createTempDirectory("deepdeep-questions").toFile()
        try {
            val first = QuestionCenter({ true }, QuestionPersistence(directory))
            backgroundScope.launch {
                first.ask("session-1", "call-1", listOf(question("q1")))
            }
            runCurrent()
            backgroundScope.launch {
                first.ask("session-2", "call-2", listOf(question("q2")))
            }
            runCurrent()
            assertEquals(2, first.pending.value.size)

            val second = QuestionCenter({ true }, QuestionPersistence(directory))
            second.loadPersisted()
            assertEquals(2, second.interrupted.value.size)
            val interrupted = second.interrupted.value.first { it.sessionId == "session-1" }
            assertEquals("call-1", interrupted.toolCallId)
            assertEquals(listOf(question("q1")), interrupted.questions)
            val root = Json.parseToJsonElement(File(directory, "pending.json").readText()) as JsonObject
            assertEquals(JsonArray(emptyList()), root["questions"])

            second.dismissInterrupted("session-1")
            assertEquals(listOf("session-2"), second.interrupted.value.map { it.sessionId })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun answeredQuestionsDoNotBecomeInterrupted() = runTest {
        val directory = Files.createTempDirectory("deepdeep-questions").toFile()
        try {
            val center = QuestionCenter({ true }, QuestionPersistence(directory))
            var outcome: QuestionOutcome? = null
            val job = launch {
                outcome = center.ask("session-1", "call-1", listOf(question("q1")))
            }
            runCurrent()
            center.answer(center.pending.value.single().requestId, listOf(QuestionAnswer("q1", selected = listOf("A"))))
            job.join()

            assertTrue(center.interrupted.value.isEmpty())
            val restarted = QuestionCenter({ true }, QuestionPersistence(directory))
            restarted.loadPersisted()
            assertTrue(restarted.interrupted.value.isEmpty())
            assertTrue(outcome is QuestionOutcome.Answered)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun abortSignalCancelsThePendingQuestion() = runTest {
        val center = QuestionCenter(isForeground = { true })
        val signal = RunSignal()
        var outcome: QuestionOutcome? = null
        val job = launch {
            outcome = center.ask("session-1", "call-1", listOf(question("q1")), signal)
        }
        runCurrent()
        assertEquals(1, center.pending.value.size)

        signal.abort("user stopped the run")
        job.join()

        assertSame(QuestionOutcome.Cancelled, outcome)
        assertTrue(center.pending.value.isEmpty())
    }

    @Test
    fun sameSessionQuestionsStayPendingTogether() = runTest {
        val center = QuestionCenter(isForeground = { true })
        var first: QuestionOutcome? = null
        var second: QuestionOutcome? = null
        val firstJob = launch {
            first = center.ask("session-1", "call-1", listOf(question("q1")))
        }
        runCurrent()
        val secondJob = launch {
            second = center.ask("session-1", "call-2", listOf(question("q2")))
        }
        runCurrent()

        val pending = center.pending.value
        assertEquals(listOf("call-1", "call-2"), pending.map { it.toolCallId })

        center.answer(pending[0].requestId, listOf(QuestionAnswer("q1", selected = listOf("A"))))
        firstJob.join()
        assertEquals("A", (first as QuestionOutcome.Answered).answers.single().selected.single())
        assertEquals(listOf("call-2"), center.pending.value.map { it.toolCallId })

        center.answer(
            center.pending.value.single().requestId,
            listOf(QuestionAnswer("q2", custom = "custom")),
        )
        secondJob.join()
        assertEquals("custom", (second as QuestionOutcome.Answered).answers.single().custom)
        assertTrue(center.pending.value.isEmpty())
    }

    @Test
    fun cancelRemovesOnlyTheTargetQuestion() = runTest {
        val center = QuestionCenter(isForeground = { true })
        var first: QuestionOutcome? = null
        var second: QuestionOutcome? = null
        val firstJob = launch {
            first = center.ask("session-1", "call-1", listOf(question("q1")))
        }
        runCurrent()
        val secondJob = launch {
            second = center.ask("session-1", "call-2", listOf(question("q2")))
        }
        runCurrent()

        center.cancel(center.pending.value.first { it.toolCallId == "call-1" }.requestId)
        firstJob.join()
        assertSame(QuestionOutcome.Cancelled, first)
        assertEquals(listOf("call-2"), center.pending.value.map { it.toolCallId })

        center.answer(
            center.pending.value.single().requestId,
            listOf(QuestionAnswer("q2", selected = listOf("B"))),
        )
        secondJob.join()
        assertEquals("B", (second as QuestionOutcome.Answered).answers.single().selected.single())
        assertTrue(center.pending.value.isEmpty())
    }

    @Test
    fun abortCancelsEveryQuestionSharingTheSignal() = runTest {
        val center = QuestionCenter(isForeground = { true })
        val signal = RunSignal()
        var first: QuestionOutcome? = null
        var second: QuestionOutcome? = null
        val firstJob = launch {
            first = center.ask("session-1", "call-1", listOf(question("q1")), signal)
        }
        runCurrent()
        val secondJob = launch {
            second = center.ask("session-1", "call-2", listOf(question("q2")), signal)
        }
        runCurrent()
        assertEquals(2, center.pending.value.size)

        signal.abort("user stopped the run")
        firstJob.join()
        secondJob.join()

        assertSame(QuestionOutcome.Cancelled, first)
        assertSame(QuestionOutcome.Cancelled, second)
        assertTrue(center.pending.value.isEmpty())
    }

    @Test
    fun differentSessionsRunInParallel() = runTest {
        val center = QuestionCenter(isForeground = { true })
        var first: QuestionOutcome? = null
        var second: QuestionOutcome? = null
        val firstJob = launch {
            first = center.ask("session-1", "call-1", listOf(question("q1")))
        }
        val secondJob = launch {
            second = center.ask("session-2", "call-2", listOf(question("q2")))
        }
        runCurrent()

        assertEquals(2, center.pending.value.size)
        val firstPending = center.pending.value.first { it.sessionId == "session-1" }
        val secondPending = center.pending.value.first { it.sessionId == "session-2" }
        center.answer(firstPending.requestId, listOf(QuestionAnswer("q1", selected = listOf("A"))))
        center.answer(secondPending.requestId, listOf(QuestionAnswer("q2", selected = listOf("B"))))
        firstJob.join()
        secondJob.join()

        assertEquals("A", (first as QuestionOutcome.Answered).answers.single().selected.single())
        assertEquals("B", (second as QuestionOutcome.Answered).answers.single().selected.single())
        assertTrue(center.pending.value.isEmpty())
    }

    @Test
    fun unknownRequestIdsAreIgnored() = runTest {
        val center = QuestionCenter(isForeground = { true })
        center.answer("unknown", emptyList())
        center.cancel("unknown")
        assertTrue(center.pending.value.isEmpty())
    }

    @Test
    fun skippedAnswersAreKeptInTheOutcome() = runTest {
        val center = QuestionCenter(isForeground = { true })
        val questions = listOf(question("q1"), question("q2"))
        var outcome: QuestionOutcome? = null
        val job = launch {
            outcome = center.ask("session-1", "call-1", questions)
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
