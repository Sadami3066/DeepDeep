package io.github.whywood.deepdeep.ui.chat

internal const val ChatHistoryFoldThreshold = 20
internal const val ChatHistoryInitialVisible = 3
internal const val ChatHistoryLoadStep = 20
internal const val ChatHistoryNavMaxTicks = 6

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

internal fun navWindowIndices(
    userMessageIndices: List<Int>,
    anchorMessageIndex: Int,
    maxTicks: Int,
): List<Int> {
    if (userMessageIndices.isEmpty() || maxTicks <= 0) return emptyList()
    if (userMessageIndices.size <= maxTicks) return userMessageIndices
    var anchorSlot = 0
    for (slot in userMessageIndices.indices) {
        if (userMessageIndices[slot] <= anchorMessageIndex) anchorSlot = slot else break
    }
    val start = (anchorSlot - maxTicks / 2).coerceIn(0, userMessageIndices.size - maxTicks)
    return userMessageIndices.subList(start, start + maxTicks)
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

internal fun activeNavSegmentIndex(segments: List<Int>, messageIndex: Int): Int {
    if (segments.isEmpty()) return -1
    var active = 0
    for (index in segments.indices) {
        if (segments[index] <= messageIndex) active = index else break
    }
    return active
}
