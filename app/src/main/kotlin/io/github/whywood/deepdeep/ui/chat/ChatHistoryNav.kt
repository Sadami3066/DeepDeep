package io.github.whywood.deepdeep.ui.chat

internal const val ChatHistoryFoldThreshold = 20
internal const val ChatHistoryInitialVisible = 3
internal const val ChatHistoryLoadStep = 20
internal const val ChatHistoryNavMaxSegments = 8

internal fun initialVisibleMessages(total: Int): Int =
    if (total > ChatHistoryFoldThreshold) ChatHistoryInitialVisible else total.coerceAtLeast(0)

internal fun expandVisibleMessages(visible: Int, total: Int): Int {
    val safeTotal = total.coerceAtLeast(0)
    val safeVisible = visible.coerceIn(0, safeTotal)
    if (safeVisible >= safeTotal) return safeVisible
    return (safeVisible + ChatHistoryLoadStep).coerceAtMost(safeTotal)
}

internal fun resolveVisibleMessages(visible: Int, previousTotal: Int, total: Int): Int {
    if (total <= 0) return 0
    if (visible <= 0 || previousTotal <= 0) return initialVisibleMessages(total)
    if (total <= previousTotal) return visible.coerceAtMost(total)
    val collapsed = visible <= ChatHistoryInitialVisible && previousTotal > ChatHistoryFoldThreshold
    return if (collapsed) {
        ChatHistoryInitialVisible.coerceAtMost(total)
    } else {
        (visible + (total - previousTotal)).coerceAtMost(total)
    }
}

internal fun navSegmentIndices(userMessageIndices: List<Int>, maxSegments: Int): List<Int> {
    if (userMessageIndices.isEmpty() || maxSegments <= 0) return emptyList()
    if (userMessageIndices.size <= maxSegments) return userMessageIndices
    if (maxSegments == 1) return listOf(userMessageIndices.last())
    return List(maxSegments) { slot ->
        val position = slot * (userMessageIndices.size - 1) / (maxSegments - 1)
        userMessageIndices[position]
    }
}

internal fun navSegmentForFraction(fraction: Float, segmentCount: Int): Int {
    if (segmentCount <= 0) return 0
    if (segmentCount == 1) return 0
    val clamped = fraction.coerceIn(0f, 1f)
    return (clamped * (segmentCount - 1) + 0.5f).toInt().coerceIn(0, segmentCount - 1)
}

internal fun messageItemIndex(messageIndex: Int, total: Int, visible: Int): Int {
    if (total <= 0) return 0
    val shown = visible.coerceIn(0, total)
    val hiddenStart = total - shown
    val pillOffset = if (hiddenStart > 0) 1 else 0
    val visiblePosition = (messageIndex - hiddenStart).coerceAtLeast(0)
    return (pillOffset + visiblePosition).coerceIn(0, total - 1 + pillOffset)
}

internal fun navSegmentCenter(index: Int, count: Int, height: Float, padding: Float): Float {
    if (count <= 1) return height / 2f
    val usable = (height - padding * 2f).coerceAtLeast(0f)
    return padding + usable * index / (count - 1)
}

internal fun messageIndexForItemIndex(itemIndex: Int, total: Int, visible: Int): Int {
    if (total <= 0) return 0
    val shown = visible.coerceIn(0, total)
    val hiddenStart = total - shown
    val pillOffset = if (hiddenStart > 0) 1 else 0
    val visiblePosition = (itemIndex - pillOffset).coerceAtLeast(0)
    return (hiddenStart + visiblePosition).coerceIn(0, total - 1)
}

internal fun activeNavSegmentIndex(segments: List<Int>, messageIndex: Int): Int {
    if (segments.isEmpty()) return -1
    var active = 0
    for (index in segments.indices) {
        if (segments[index] <= messageIndex) active = index else break
    }
    return active
}

internal fun navIndicatorFraction(position: Float, itemCount: Int): Float {
    if (itemCount <= 1) return 0f
    return (position / (itemCount - 1)).coerceIn(0f, 1f)
}
