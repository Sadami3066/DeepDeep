package io.github.whywood.deepdeep.ui.common.chat

internal object ScrollFollowPolicy {
    const val NearBottomThresholdDp = 80f

    fun nearBottom(distancePx: Float, thresholdPx: Float): Boolean = distancePx <= thresholdPx
}
