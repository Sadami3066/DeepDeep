package io.github.whywood.deepdeep.runtime

enum class TurnSource {
    USER,
    RETRY,
    REMINDER,
    GOAL,
    JOB,
}

sealed interface TurnNotice {
    data class Compacted(val tokensBefore: Long, val tokensAfter: Long?) : TurnNotice

    data class GoalRound(val round: Int, val maxRounds: Int, val objective: String) : TurnNotice

    data class GoalFinished(val completed: Boolean) : TurnNotice

    data class JobFinished(val label: String, val failed: Boolean) : TurnNotice

    data class InterruptedQuestions(val count: Int) : TurnNotice
}

data class NoticeEvent(
    override val runId: String,
    val notice: TurnNotice,
) : RuntimeEvent

data class BackgroundTurnStarted(
    override val runId: String,
    val notice: TurnNotice? = null,
) : RuntimeEvent

data class UsageChanged(
    override val runId: String,
    val usage: SessionUsage,
) : RuntimeEvent
