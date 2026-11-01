package io.github.whywood.deepdeep.runtime

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class GoalService : GoalController, GoalDriver {
    private val mutex = Mutex()
    private val persisted: MutableMap<String, Goal> = mutableMapOf()
    private val armedSessions = mutableSetOf<String>()
    private val turnSources = ConcurrentHashMap<String, TurnSource>()
    private val goalsState = MutableStateFlow(views())

    override val goals: StateFlow<Map<String, GoalView>> = goalsState.asStateFlow()

    override suspend fun create(sessionId: String, objective: String, maxRounds: Int?): GoalView? = mutex.withLock {
        val trimmed = objective.trim()
        if (trimmed.isEmpty()) return@withLock null
        val now = System.currentTimeMillis()
        persisted[sessionId] = Goal(
            id = newGoalId(),
            revision = 1,
            objective = trimmed,
            phase = GoalPhase.ACTIVE,
            roundsStarted = 0,
            maxRounds = normalizeMaxRounds(maxRounds),
            createdAt = now,
            updatedAt = now,
        )
        armedSessions += sessionId
        goalsState.value = views()
        goalsState.value[sessionId]
    }

    override suspend fun edit(sessionId: String, objective: String): GoalView? =
        edit(sessionId, objective, null)

    suspend fun edit(sessionId: String, objective: String?, maxRounds: Int?): GoalView? = mutex.withLock {
        val goal = persisted[sessionId] ?: return@withLock null
        val trimmed = objective?.trim()
        if (objective != null && trimmed.isNullOrEmpty()) return@withLock null
        if (trimmed == null && maxRounds == null) return@withLock null
        updateGoal(
            sessionId,
            goal.copy(
                objective = trimmed ?: goal.objective,
                maxRounds = maxRounds?.let(::normalizeMaxRounds) ?: goal.maxRounds,
                revision = goal.revision + 1,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun pause(sessionId: String): GoalView? = mutex.withLock {
        val goal = persisted[sessionId] ?: return@withLock null
        armedSessions -= sessionId
        updateGoal(
            sessionId,
            goal.copy(
                phase = GoalPhase.PAUSED,
                revision = goal.revision + 1,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun resume(sessionId: String): GoalView? = mutex.withLock {
        val goal = persisted[sessionId] ?: return@withLock null
        if (goal.phase == GoalPhase.COMPLETE) return@withLock null
        if (goal.phase == GoalPhase.ACTIVE && sessionId in armedSessions) return@withLock null
        if (goal.maxRounds > 0 && goal.roundsStarted >= goal.maxRounds) return@withLock null
        armedSessions += sessionId
        updateGoal(
            sessionId,
            goal.copy(
                phase = GoalPhase.ACTIVE,
                blockedReason = null,
                revision = goal.revision + 1,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun complete(sessionId: String): GoalView? = mutex.withLock {
        val goal = persisted[sessionId] ?: return@withLock null
        if (goal.phase == GoalPhase.COMPLETE) return@withLock null
        armedSessions -= sessionId
        updateGoal(
            sessionId,
            goal.copy(
                phase = GoalPhase.COMPLETE,
                revision = goal.revision + 1,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun clear(sessionId: String) = mutex.withLock {
        armedSessions -= sessionId
        if (persisted.remove(sessionId) != null) goalsState.value = views()
    }

    suspend fun block(sessionId: String, reason: String): GoalView? = mutex.withLock {
        val goal = persisted[sessionId] ?: return@withLock null
        if (goal.phase != GoalPhase.ACTIVE) return@withLock null
        if (goal.roundsStarted < MIN_BLOCKED_ROUNDS) return@withLock null
        val trimmed = reason.trim()
        if (trimmed.isEmpty()) return@withLock null
        armedSessions -= sessionId
        updateGoal(
            sessionId,
            goal.copy(
                phase = GoalPhase.BLOCKED,
                blockedReason = trimmed,
                revision = goal.revision + 1,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun noteTurn(sessionId: String, source: TurnSource) {
        turnSources[sessionId] = source
    }

    fun currentTurnSource(sessionId: String): TurnSource? = turnSources[sessionId]

    override suspend fun nextRound(sessionId: String, source: TurnSource, succeeded: Boolean): GoalRound? =
        mutex.withLock {
            val goal = persisted[sessionId] ?: return@withLock null
            if (goal.phase != GoalPhase.ACTIVE || sessionId !in armedSessions) return@withLock null
            if (!succeeded) {
                if (source == TurnSource.GOAL) {
                    armedSessions -= sessionId
                    updateGoal(
                        sessionId,
                        goal.copy(
                            phase = GoalPhase.PAUSED,
                            revision = goal.revision + 1,
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                }
                return@withLock null
            }
            if (goal.maxRounds > 0 && goal.roundsStarted >= goal.maxRounds) {
                blockOnRoundLimit(sessionId, goal)
                return@withLock null
            }
            proposal(goal)
        }

    override suspend fun commitRound(sessionId: String, goalId: String, revision: Long): GoalRound? =
        mutex.withLock {
            val goal = persisted[sessionId] ?: return@withLock null
            if (goal.id != goalId || goal.revision.toLong() != revision) return@withLock null
            if (goal.phase != GoalPhase.ACTIVE) return@withLock null
            if (goal.maxRounds > 0 && goal.roundsStarted >= goal.maxRounds) {
                blockOnRoundLimit(sessionId, goal)
                return@withLock null
            }
            val round = goal.roundsStarted + 1
            val updated = goal.copy(
                roundsStarted = round,
                revision = goal.revision + 1,
                updatedAt = System.currentTimeMillis(),
            )
            updateGoal(sessionId, updated)
            GoalRound(
                round = round,
                maxRounds = updated.maxRounds,
                prompt = roundPrompt(updated.objective, round, updated.maxRounds),
                objective = updated.objective,
                goalId = updated.id,
                revision = updated.revision.toLong(),
            )
        }

    private fun proposal(goal: Goal): GoalRound {
        val round = goal.roundsStarted + 1
        return GoalRound(
            round = round,
            maxRounds = goal.maxRounds,
            prompt = roundPrompt(goal.objective, round, goal.maxRounds),
            objective = goal.objective,
            goalId = goal.id,
            revision = goal.revision.toLong(),
        )
    }

    private fun blockOnRoundLimit(sessionId: String, goal: Goal) {
        armedSessions -= sessionId
        updateGoal(
            sessionId,
            goal.copy(
                phase = GoalPhase.BLOCKED,
                blockedReason = ROUND_LIMIT_REASON,
                revision = goal.revision + 1,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun pauseOnStop(sessionId: String) {
        mutex.withLock {
            val goal = persisted[sessionId] ?: return@withLock
            if (goal.phase != GoalPhase.ACTIVE) return@withLock
            armedSessions -= sessionId
            updateGoal(
                sessionId,
                goal.copy(
                    phase = GoalPhase.PAUSED,
                    revision = goal.revision + 1,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    override suspend fun clearSession(sessionId: String) {
        mutex.withLock {
            armedSessions -= sessionId
            turnSources.remove(sessionId)
            if (persisted.remove(sessionId) != null) goalsState.value = views()
        }
    }

    private fun updateGoal(sessionId: String, goal: Goal): GoalView {
        persisted[sessionId] = goal
        goalsState.value = views()
        return goalsState.value.getValue(sessionId)
    }

    private fun views(): Map<String, GoalView> = persisted.entries.associate { (sessionId, goal) ->
        sessionId to GoalView(goal, sessionId in armedSessions)
    }

    companion object {
        const val DEFAULT_MAX_ROUNDS = 0
        const val MIN_MAX_ROUNDS = 1
        const val MAX_MAX_ROUNDS = 50
        const val MIN_BLOCKED_ROUNDS = 3
        const val ROUND_LIMIT_REASON = "round-limit"

        private fun normalizeMaxRounds(maxRounds: Int?): Int = when {
            maxRounds == null -> DEFAULT_MAX_ROUNDS
            maxRounds <= 0 -> DEFAULT_MAX_ROUNDS
            else -> maxRounds.coerceIn(MIN_MAX_ROUNDS, MAX_MAX_ROUNDS)
        }

        private fun newGoalId(): String = UUID.randomUUID().toString().replace("-", "").take(12)

        private fun roundPrompt(objective: String, round: Int, maxRounds: Int): String = buildString {
            appendLine("<goal_round>")
            appendLine("Objective: $objective")
            appendLine(if (maxRounds > 0) "Round: $round/$maxRounds" else "Round: $round")
            append("Continue working toward the objective. Call update_goal with action=complete when it is achieved, or action=blocked with a concrete reason when the same blocker persisted for at least 3 rounds.")
            appendLine()
            append("</goal_round>")
        }
    }
}
