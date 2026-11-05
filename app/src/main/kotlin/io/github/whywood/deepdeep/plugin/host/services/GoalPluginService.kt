package io.github.whywood.deepdeep.plugin.host.services

import io.github.whywood.deepdeep.plugin.api.GoalInfo
import io.github.whywood.deepdeep.plugin.api.GoalPhase
import io.github.whywood.deepdeep.plugin.api.GoalService
import io.github.whywood.deepdeep.runtime.GoalPhase as RuntimeGoalPhase
import io.github.whywood.deepdeep.runtime.GoalService as RuntimeGoalService
import io.github.whywood.deepdeep.runtime.GoalView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class GoalPluginService(
    private val goals: RuntimeGoalService,
) : GoalService {
    override fun observe(): Flow<List<GoalInfo>> = goals.goals.map { entries ->
        entries.map { (sessionId, view) -> view.toPluginInfo(sessionId) }
    }

    override suspend fun get(sessionId: String): GoalInfo? = goals.goals.value[sessionId]?.toPluginInfo(sessionId)

    override suspend fun create(sessionId: String, objective: String, maxRounds: Int?): GoalInfo? =
        goals.create(sessionId, objective, maxRounds)?.toPluginInfo(sessionId)

    override suspend fun edit(sessionId: String, objective: String): GoalInfo? =
        goals.edit(sessionId, objective)?.toPluginInfo(sessionId)

    override suspend fun pause(sessionId: String): GoalInfo? =
        goals.pause(sessionId)?.toPluginInfo(sessionId)

    override suspend fun resume(sessionId: String): GoalInfo? =
        goals.resume(sessionId)?.toPluginInfo(sessionId)

    override suspend fun complete(sessionId: String): GoalInfo? =
        goals.complete(sessionId)?.toPluginInfo(sessionId)

    override suspend fun clear(sessionId: String) = goals.clear(sessionId)

    private fun GoalView.toPluginInfo(sessionId: String): GoalInfo = GoalInfo(
        id = goal.id,
        sessionId = sessionId,
        objective = goal.objective,
        phase = goal.phase.toPluginPhase(),
        roundsStarted = goal.roundsStarted,
        maxRounds = goal.maxRounds,
        armed = armed,
        blockedReason = goal.blockedReason,
        createdAt = goal.createdAt,
        updatedAt = goal.updatedAt,
    )

    private fun RuntimeGoalPhase.toPluginPhase(): GoalPhase = when (this) {
        RuntimeGoalPhase.ACTIVE -> GoalPhase.ACTIVE
        RuntimeGoalPhase.PAUSED -> GoalPhase.PAUSED
        RuntimeGoalPhase.BLOCKED -> GoalPhase.BLOCKED
        RuntimeGoalPhase.COMPLETE -> GoalPhase.COMPLETE
    }
}
