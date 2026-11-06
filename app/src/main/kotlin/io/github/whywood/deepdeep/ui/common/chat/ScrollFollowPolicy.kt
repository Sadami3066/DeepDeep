package io.github.whywood.deepdeep.ui.common.chat

internal object ScrollFollowPolicy {
    const val NearBottomThresholdDp = 80f
    const val RetractDeadbandDp = 24f
    const val RetractDebounceMs = 250L
    const val NestedScrollHoldMs = 300L
    const val MaxRevealPx = 240f
    const val RevealDurationMs = 180

    fun nearBottom(distancePx: Float, thresholdPx: Float): Boolean = distancePx <= thresholdPx

    fun revealDeltaPx(overflowPx: Float): Float = overflowPx.coerceIn(0f, MaxRevealPx)

    fun decideFollowAction(
        overflowPx: Float,
        contentBottomDeltaPx: Float,
        sinceGrowthMs: Long?,
        deadbandPx: Float,
        debounceMs: Long,
    ): FollowAction {
        if (overflowPx <= 0f) return FollowAction.None
        val retracting = contentBottomDeltaPx < 0f
        val withinDeadband = -contentBottomDeltaPx < deadbandPx
        val recentGrowth = sinceGrowthMs != null && sinceGrowthMs in 0..debounceMs
        return if (retracting && withinDeadband && recentGrowth) FollowAction.Ignore else FollowAction.Reveal
    }
}

internal enum class FollowAction { Reveal, Ignore, None }

internal class MovementWindow(
    private val holdMs: Long,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private var lastMarkMs: Long? = null

    fun mark() {
        lastMarkMs = nowMillis()
    }

    fun active(): Boolean {
        val last = lastMarkMs ?: return false
        return nowMillis() - last <= holdMs
    }
}
