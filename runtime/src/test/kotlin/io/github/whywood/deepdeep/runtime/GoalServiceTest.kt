package io.github.whywood.deepdeep.runtime

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GoalServiceTest {
    private lateinit var directory: File
    private lateinit var store: GoalStore

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("deepdeep-goals").toFile()
        store = GoalStore(directory)
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun createGoalUsesDefaultsAndPersists() = runTest {
        val service = GoalService(store, backgroundScope)
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
        assertEquals(goal, store.read()["session-1"])
        assertTrue(File(directory, "goals.json").exists())
        assertFalse(File(directory, "goals.json.tmp").exists())
        assertNull(service.create("session-1", "   "))
    }

    @Test
    fun maxRoundsAreClampedToOneThroughFifty() = runTest {
        val service = GoalService(store, backgroundScope)

        assertEquals(0, service.create("low", "x", 0)!!.goal.maxRounds)
        assertEquals(50, service.create("high", "x", 100)!!.goal.maxRounds)
        assertEquals(3, service.create("exact", "x", 3)!!.goal.maxRounds)
        assertEquals(0, service.create("default", "x", null)!!.goal.maxRounds)
    }

    @Test
    fun mutationsIncrementRevisionAndPersist() = runTest {
        val service = GoalService(store, backgroundScope)

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

        assertEquals(view.goal, store.read()["session-1"])
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
        val service = GoalService(store, backgroundScope)
        val created = service.create("session-1", "Original")!!

        assertNull(service.edit("session-1", "   "))

        assertEquals(created, service.goals.value["session-1"])
        assertEquals(created.goal, store.read()["session-1"])
    }

    @Test
    fun clearRemovesGoalAndSessionState() = runTest {
        val service = GoalService(store, backgroundScope)
        service.create("session-1", "Temporary")
        service.noteTurn("session-1", TurnSource.USER)

        service.clear("session-1")

        assertNull(service.goals.value["session-1"])
        assertNull(store.read()["session-1"])
        service.clear("session-1")

        service.create("session-2", "Also temporary")
        service.noteTurn("session-2", TurnSource.USER)
        service.clearSession("session-2")

        assertNull(service.goals.value["session-2"])
        assertNull(store.read()["session-2"])
        assertNull(service.currentTurnSource("session-2"))
    }

    @Test
    fun roundLimitBlocksGoal() = runTest {
        val service = GoalService(store, backgroundScope)
        service.create("session-1", "Finish the task", 2)

        val first = service.nextRound("session-1", TurnSource.USER, true)!!
        assertEquals(1, first.round)
        assertEquals(2, first.maxRounds)
        assertEquals(
            "<goal_round>\nObjective: Finish the task\nRound: 1/2\n" +
                "Continue working toward the objective. Call update_goal with action complete when it is achieved, or blocked with a concrete reason when the same blocker persisted for at least 3 rounds.\n" +
                "</goal_round>",
            first.prompt,
        )

        val second = service.nextRound("session-1", TurnSource.USER, true)!!
        assertEquals(2, second.round)
        assertNull(service.nextRound("session-1", TurnSource.USER, true))

        val view = service.goals.value.getValue("session-1")
        assertEquals(GoalPhase.BLOCKED, view.goal.phase)
        assertEquals(GoalService.ROUND_LIMIT_REASON, view.goal.blockedReason)
        assertEquals(2, view.goal.roundsStarted)
        assertEquals(4, view.goal.revision)
        assertFalse(view.armed)
        assertEquals(view.goal, store.read()["session-1"])
        assertNull(service.resume("session-1"))
        assertNull(service.nextRound("session-1", TurnSource.USER, true))
    }

    @Test
    fun failedGoalRoundPausesAndDisarms() = runTest {
        val service = GoalService(store, backgroundScope)
        service.create("session-1", "Keep improving")
        service.noteTurn("session-1", TurnSource.GOAL)
        assertNotNull(service.nextRound("session-1", TurnSource.GOAL, true))

        assertNull(service.nextRound("session-1", TurnSource.GOAL, false))

        val view = service.goals.value.getValue("session-1")
        assertEquals(GoalPhase.PAUSED, view.goal.phase)
        assertFalse(view.armed)
        assertEquals(3, view.goal.revision)
        assertNull(service.nextRound("session-1", TurnSource.USER, true))

        val resumed = service.resume("session-1")!!
        assertEquals(GoalPhase.ACTIVE, resumed.goal.phase)
        assertTrue(resumed.armed)
        assertNotNull(service.nextRound("session-1", TurnSource.USER, true))
    }

    @Test
    fun blockingRequiresThreeStartedRounds() = runTest {
        val service = GoalService(store, backgroundScope)
        service.create("session-1", "Resolve the blocker")
        service.noteTurn("session-1", TurnSource.GOAL)

        assertNull(service.block("session-1", "stuck"))
        assertEquals(GoalPhase.ACTIVE, service.goals.value.getValue("session-1").goal.phase)

        assertNotNull(service.nextRound("session-1", TurnSource.GOAL, true))
        assertNotNull(service.nextRound("session-1", TurnSource.GOAL, true))
        assertNull(service.block("session-1", "stuck"))
        assertEquals(GoalPhase.ACTIVE, service.goals.value.getValue("session-1").goal.phase)

        assertNotNull(service.nextRound("session-1", TurnSource.GOAL, true))
        val blocked = service.block("session-1", "  The dependency is permanently unavailable  ")!!

        assertEquals(GoalPhase.BLOCKED, blocked.goal.phase)
        assertEquals("The dependency is permanently unavailable", blocked.goal.blockedReason)
        assertEquals(3, blocked.goal.roundsStarted)
        assertEquals(5, blocked.goal.revision)
        assertFalse(blocked.armed)
        assertEquals(blocked.goal, store.read()["session-1"])
        assertNull(service.nextRound("session-1", TurnSource.GOAL, true))
    }

    @Test
    fun pauseOnStopPausesActiveGoal() = runTest {
        val service = GoalService(store, backgroundScope)
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
    fun reloadedGoalIsDisarmed() = runTest {
        val first = GoalService(store, backgroundScope)
        val created = first.create("session-1", "Survive a restart")!!

        val reopened = GoalService(store, backgroundScope)
        val view = reopened.goals.value.getValue("session-1")

        assertEquals(created.goal, view.goal)
        assertFalse(view.armed)
        assertNull(reopened.nextRound("session-1", TurnSource.USER, true))
        assertEquals(0, reopened.goals.value.getValue("session-1").goal.roundsStarted)

        val resumed = reopened.resume("session-1")!!
        assertTrue(resumed.armed)
        assertEquals(2, resumed.goal.revision)

        val round = reopened.nextRound("session-1", TurnSource.USER, true)!!
        assertEquals(1, round.round)
        assertEquals(1, reopened.goals.value.getValue("session-1").goal.roundsStarted)
        assertEquals(3, reopened.goals.value.getValue("session-1").goal.revision)
    }
}
