package io.github.whywood.deepdeep.runtime

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GoalServiceTest {
    @Test
    fun createGoalUsesDefaults() = runTest {
        val service = GoalService()
        val view = service.create("session-1", "  Ship the release  ")

        assertNotNull(view)
        val goal = view!!.goal
        assertEquals(12, goal.id.length)
        assertTrue(goal.id.all { it in "0123456789abcdef" })
        assertEquals(1, goal.revision)
        assertEquals("Ship the release", goal.objective)
        assertEquals(GoalPhase.ACTIVE, goal.phase)
        assertEquals(0, goal.roundsStarted)
        assertEquals(0, goal.maxRounds)
        assertNull(goal.blockedReason)
        assertTrue(view.armed)
        assertTrue(goal.createdAt > 0)
        assertEquals(view, service.goals.value["session-1"])
        assertNull(service.create("session-1", "   "))
    }

    @Test
    fun maxRoundsAreClampedToOneThroughFifty() = runTest {
        val service = GoalService()

        assertEquals(0, service.create("low", "x", 0)!!.goal.maxRounds)
        assertEquals(50, service.create("high", "x", 100)!!.goal.maxRounds)
        assertEquals(3, service.create("exact", "x", 3)!!.goal.maxRounds)
        assertEquals(0, service.create("default", "x", null)!!.goal.maxRounds)
    }

    @Test
    fun mutationsIncrementRevision() = runTest {
        val service = GoalService()

        var view = service.create("session-1", "Draft")!!
        assertEquals(1, view.goal.revision)

        view = service.edit("session-1", "  Final objective  ")!!
        assertEquals(2, view.goal.revision)
        assertEquals("Final objective", view.goal.objective)
        assertTrue(view.armed)

        view = service.pause("session-1")!!
        assertEquals(3, view.goal.revision)
        assertEquals(GoalPhase.PAUSED, view.goal.phase)
        assertFalse(view.armed)

        view = service.resume("session-1")!!
        assertEquals(4, view.goal.revision)
        assertEquals(GoalPhase.ACTIVE, view.goal.phase)
        assertTrue(view.armed)

        view = service.complete("session-1")!!
        assertEquals(5, view.goal.revision)
        assertEquals(GoalPhase.COMPLETE, view.goal.phase)
        assertFalse(view.armed)

        assertEquals(view, service.goals.value["session-1"])
        assertNull(service.resume("session-1"))
        assertNull(service.complete("session-1"))
        assertNull(service.edit("missing", "x"))
        assertNull(service.pause("missing"))
        assertNull(service.resume("missing"))
        assertNull(service.complete("missing"))
    }

    @Test
    fun editRejectsBlankObjectiveWithoutChangingGoal() = runTest {
        val service = GoalService()
        val created = service.create("session-1", "Original")!!

        assertNull(service.edit("session-1", "   "))

        assertEquals(created, service.goals.value["session-1"])
    }

    @Test
    fun clearRemovesGoalAndSessionState() = runTest {
        val service = GoalService()
        service.create("session-1", "Temporary")
        service.noteTurn("session-1", TurnSource.USER)

        service.clear("session-1")

        assertNull(service.goals.value["session-1"])
        service.clear("session-1")

        service.create("session-2", "Also temporary")
        service.noteTurn("session-2", TurnSource.USER)
        service.clearSession("session-2")

        assertNull(service.goals.value["session-2"])
        assertNull(service.currentTurnSource("session-2"))
    }

    @Test
    fun nextRoundProposesWithoutCountingUntilCommitted() = runTest {
        val service = GoalService()
        val created = service.create("session-1", "Keep going")!!.goal
        service.noteTurn("session-1", TurnSource.GOAL)

        val first = service.nextRound("session-1", TurnSource.GOAL, true)!!
        assertEquals(1, first.round)
        assertEquals(created.id, first.goalId)
        assertEquals(created.revision.toLong(), first.revision)
        assertEquals(0, service.goals.value.getValue("session-1").goal.roundsStarted)
        assertEquals(created.revision, service.goals.value.getValue("session-1").goal.revision)

        val duplicate = service.nextRound("session-1", TurnSource.GOAL, true)!!
        assertEquals(first, duplicate)
        assertEquals(0, service.goals.value.getValue("session-1").goal.roundsStarted)

        val committed = service.commitRound("session-1", first.goalId, first.revision)!!
        assertEquals(1, committed.round)
        assertEquals(2L, committed.revision)
        assertEquals(1, service.goals.value.getValue("session-1").goal.roundsStarted)
        assertEquals(2, service.goals.value.getValue("session-1").goal.revision)

        assertNull(service.commitRound("session-1", first.goalId, first.revision))
        assertEquals(1, service.goals.value.getValue("session-1").goal.roundsStarted)
    }

    @Test
    fun commitRoundRejectsStaleGoalIdOrRevision() = runTest {
        val service = GoalService()
        service.create("session-1", "Keep going")
        val proposal = service.nextRound("session-1", TurnSource.USER, true)!!

        assertNull(service.commitRound("session-1", "missing-goal", proposal.revision))
        assertNull(service.commitRound("session-1", proposal.goalId, proposal.revision + 1))
        assertNull(service.commitRound("missing", proposal.goalId, proposal.revision))
        assertEquals(0, service.goals.value.getValue("session-1").goal.roundsStarted)
    }

    @Test
    fun pauseClearAndEditInvalidateProposals() = runTest {
        val service = GoalService()

        service.create("paused", "Pause me")
        val pausedProposal = service.nextRound("paused", TurnSource.USER, true)!!
        service.pause("paused")
        assertNull(service.commitRound("paused", pausedProposal.goalId, pausedProposal.revision))
        assertEquals(0, service.goals.value.getValue("paused").goal.roundsStarted)

        service.create("edited", "Edit me")
        val editedProposal = service.nextRound("edited", TurnSource.USER, true)!!
        service.edit("edited", "Edited objective")
        assertNull(service.commitRound("edited", editedProposal.goalId, editedProposal.revision))
        assertEquals(0, service.goals.value.getValue("edited").goal.roundsStarted)

        service.create("cleared", "Clear me")
        val clearedProposal = service.nextRound("cleared", TurnSource.USER, true)!!
        service.clear("cleared")
        assertNull(service.commitRound("cleared", clearedProposal.goalId, clearedProposal.revision))
        assertNull(service.goals.value["cleared"])
    }

    @Test
    fun roundLimitBlocksGoal() = runTest {
        val service = GoalService()
        service.create("session-1", "Finish the task", 2)

        val firstProposal = service.nextRound("session-1", TurnSource.USER, true)!!
        assertEquals(1, firstProposal.round)
        assertEquals(2, firstProposal.maxRounds)
        assertEquals(1L, firstProposal.revision)
        assertEquals(0, service.goals.value.getValue("session-1").goal.roundsStarted)
        val first = service.commitRound("session-1", firstProposal.goalId, firstProposal.revision)!!
        assertEquals(1, first.round)
        assertEquals(
            "<goal_round>\nObjective: Finish the task\nRound: 1/2\n" +
                "Continue working toward the objective. Call goal with action=complete when it is achieved, or action=blocked with a concrete reason when the same blocker persisted for at least 3 rounds.\n" +
                "</goal_round>",
            first.prompt,
        )

        val secondProposal = service.nextRound("session-1", TurnSource.USER, true)!!
        assertEquals(2L, secondProposal.revision)
        val second = service.commitRound("session-1", secondProposal.goalId, secondProposal.revision)!!
        assertEquals(2, second.round)
        assertNull(service.nextRound("session-1", TurnSource.USER, true))

        val view = service.goals.value.getValue("session-1")
        assertEquals(GoalPhase.BLOCKED, view.goal.phase)
        assertEquals(GoalService.ROUND_LIMIT_REASON, view.goal.blockedReason)
        assertEquals(2, view.goal.roundsStarted)
        assertEquals(4, view.goal.revision)
        assertFalse(view.armed)
        assertNull(service.resume("session-1"))
        assertNull(service.nextRound("session-1", TurnSource.USER, true))
    }

    @Test
    fun failedGoalRoundPausesAndDisarms() = runTest {
        val service = GoalService()
        service.create("session-1", "Keep improving")
        service.noteTurn("session-1", TurnSource.GOAL)
        val proposal = service.nextRound("session-1", TurnSource.GOAL, true)!!
        assertNotNull(service.commitRound("session-1", proposal.goalId, proposal.revision))

        assertNull(service.nextRound("session-1", TurnSource.GOAL, false))

        val view = service.goals.value.getValue("session-1")
        assertEquals(GoalPhase.PAUSED, view.goal.phase)
        assertFalse(view.armed)
        assertEquals(3, view.goal.revision)
        assertNull(service.nextRound("session-1", TurnSource.USER, true))

        val resumed = service.resume("session-1")!!
        assertEquals(GoalPhase.ACTIVE, resumed.goal.phase)
        assertTrue(resumed.armed)
        val next = service.nextRound("session-1", TurnSource.USER, true)!!
        assertNotNull(service.commitRound("session-1", next.goalId, next.revision))
    }

    @Test
    fun blockingRequiresThreeStartedRounds() = runTest {
        val service = GoalService()
        service.create("session-1", "Resolve the blocker")
        service.noteTurn("session-1", TurnSource.GOAL)

        assertNull(service.block("session-1", "stuck"))
        assertEquals(GoalPhase.ACTIVE, service.goals.value.getValue("session-1").goal.phase)

        assertNotNull(service.commit(service.nextRound("session-1", TurnSource.GOAL, true)!!))
        assertNotNull(service.commit(service.nextRound("session-1", TurnSource.GOAL, true)!!))
        assertNull(service.block("session-1", "stuck"))
        assertEquals(GoalPhase.ACTIVE, service.goals.value.getValue("session-1").goal.phase)

        assertNotNull(service.commit(service.nextRound("session-1", TurnSource.GOAL, true)!!))
        val blocked = service.block("session-1", "  The dependency is permanently unavailable  ")!!

        assertEquals(GoalPhase.BLOCKED, blocked.goal.phase)
        assertEquals("The dependency is permanently unavailable", blocked.goal.blockedReason)
        assertEquals(3, blocked.goal.roundsStarted)
        assertEquals(5, blocked.goal.revision)
        assertFalse(blocked.armed)
        assertNull(service.nextRound("session-1", TurnSource.GOAL, true))
    }

    @Test
    fun pauseOnStopPausesActiveGoal() = runTest {
        val service = GoalService()
        service.create("session-1", "Long running work")

        service.pauseOnStop("session-1")

        val view = service.goals.value.getValue("session-1")
        assertEquals(GoalPhase.PAUSED, view.goal.phase)
        assertEquals(2, view.goal.revision)
        assertFalse(view.armed)

        service.pauseOnStop("session-1")
        assertEquals(2, service.goals.value.getValue("session-1").goal.revision)
        assertNull(service.nextRound("session-1", TurnSource.USER, true))
    }

    @Test
    fun goalsAreKeptInMemoryOnly() = runTest {
        val first = GoalService()
        first.create("session-1", "Never touches disk")

        val reopened = GoalService()

        assertTrue(reopened.goals.value.isEmpty())
        assertNull(reopened.resume("session-1"))
        assertNull(reopened.nextRound("session-1", TurnSource.USER, true))
    }

    @Test
    fun revisionsStayUniqueAcrossCommits() = runTest {
        val service = GoalService()
        service.create("session-1", "Track revisions")

        val firstProposal = service.nextRound("session-1", TurnSource.USER, true)!!
        val first = service.commitRound("session-1", firstProposal.goalId, firstProposal.revision)!!
        assertEquals(firstProposal.revision + 1, first.revision)

        val secondProposal = service.nextRound("session-1", TurnSource.USER, true)!!
        assertEquals(first.revision, secondProposal.revision)
        val second = service.commitRound("session-1", secondProposal.goalId, secondProposal.revision)!!
        assertEquals(first.revision + 1, second.revision)
        assertEquals(2, service.goals.value.getValue("session-1").goal.roundsStarted)
    }

    private suspend fun GoalService.commit(proposal: GoalRound): GoalRound? =
        commitRound("session-1", proposal.goalId, proposal.revision)
}
