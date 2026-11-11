package io.github.whywood.deepdeep.ui.chat

internal const val ChatHistoryFoldThreshold = 20
internal const val ChatHistoryInitialVisible = 3
internal const val ChatHistoryLoadStep = 20
internal const val ChatHistoryNavWindowTicks = 5

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

internal fun navAnchorSlot(userMessageIndices: List<Int>, anchorMessageIndex: Int): Int {
    if (userMessageIndices.isEmpty()) return 0
    var slot = 0
    for (index in userMessageIndices.indices) {
        if (userMessageIndices[index] <= anchorMessageIndex) slot = index else break
    }
    return slot
}

internal fun navFollowTopSlot(
    activeSlot: Int,
    currentTopSlot: Int,
    count: Int,
    viewportTicks: Int,
): Int {
    if (count <= viewportTicks) return 0
    val maxTop = count - viewportTicks
    val top = currentTopSlot.coerceIn(0, maxTop)
    val active = activeSlot.coerceIn(0, count - 1)
    return when {
        active < top -> active
        active >= top + viewportTicks -> (active - viewportTicks + 1).coerceAtMost(maxTop)
        else -> top
    }
}

internal fun messageItemIndex(messageIndex: Int, total: Int, visible: Int): Int {
    if (total <= 0) return 0
    val shown = visible.coerceIn(0, total)
    val hiddenStart = total - shown
    val pillOffset = if (hiddenStart > 0) 1 else 0
    val visiblePosition = (messageIndex - hiddenStart).coerceAtLeast(0)
    return (pillOffset + visiblePosition).coerceIn(0, total - 1 + pillOffset)
}

internal fun messageIndexForItemIndex(itemIndex: Int, total: Int, visible: Int): Int {
    if (total <= 0) return 0
    val shown = visible.coerceIn(0, total)
    val hiddenStart = total - shown
    val pillOffset = if (hiddenStart > 0) 1 else 0
    val visiblePosition = (itemIndex - pillOffset).coerceAtLeast(0)
    return (hiddenStart + visiblePosition).coerceIn(0, total - 1)
}
