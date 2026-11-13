package io.github.whywood.deepdeep.runtime

import kotlinx.serialization.Serializable

const val CAPABILITIES_NOTICE_CUSTOM_TYPE: String = "deepdeep.capabilities"
const val CAPABILITIES_BASELINE_CUSTOM_TYPE: String = "deepdeep.capabilities.baseline"

@Serializable
data class CapabilityDelta(
    val toolsAdded: List<String> = emptyList(),
    val toolsRemoved: List<String> = emptyList(),
    val skillsAdded: List<String> = emptyList(),
    val skillsRemoved: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = toolsAdded.isEmpty() && toolsRemoved.isEmpty() && skillsAdded.isEmpty() && skillsRemoved.isEmpty()
}

enum class TurnSource {
    USER,
    RETRY,
    REMINDER,
    GOAL,
    JOB,
    PLUGIN,
}

sealed interface TurnNotice {
    data class Compacted(val tokensBefore: Long, val tokensAfter: Long?) : TurnNotice

    data class GoalRound(val round: Int, val maxRounds: Int, val objective: String) : TurnNotice

    data class GoalFinished(val completed: Boolean) : TurnNotice

    data class JobFinished(val label: String, val failed: Boolean) : TurnNotice

    data class InterruptedQuestions(val count: Int) : TurnNotice

    data class InterruptedRun(val count: Int) : TurnNotice

    data class CapabilitiesChanged(val delta: CapabilityDelta) : TurnNotice
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
