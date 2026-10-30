package io.github.whywood.deepdeep.runtime

import kotlinx.coroutines.flow.StateFlow

enum class GoalPhase {
    ACTIVE,
    PAUSED,
    BLOCKED,
    COMPLETE,
}

data class Goal(
    val id: String,
    val revision: Int,
    val objective: String,
    val phase: GoalPhase,
    val roundsStarted: Int,
    val maxRounds: Int,
    val blockedReason: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

data class GoalView(
    val goal: Goal,
    val armed: Boolean,
)

data class GoalRound(
    val round: Int,
    val maxRounds: Int,
    val prompt: String,
    val objective: String = "",
    val goalId: String = "",
    val revision: Long = 0,
)

interface GoalController {
    val goals: StateFlow<Map<String, GoalView>>

    suspend fun create(sessionId: String, objective: String, maxRounds: Int? = null): GoalView?

    suspend fun edit(sessionId: String, objective: String): GoalView?

    suspend fun pause(sessionId: String): GoalView?

    suspend fun resume(sessionId: String): GoalView?

    suspend fun complete(sessionId: String): GoalView?

    suspend fun clear(sessionId: String)
}

interface GoalDriver {
    suspend fun noteTurn(sessionId: String, source: TurnSource)

    suspend fun nextRound(sessionId: String, source: TurnSource, succeeded: Boolean): GoalRound?

    suspend fun commitRound(sessionId: String, goalId: String, revision: Long): GoalRound?

    suspend fun pauseOnStop(sessionId: String)

    suspend fun clearSession(sessionId: String)
}
