package io.github.whywood.deepdeep.screen

import io.github.whywood.deepdeep.plugin.gui.GUI_MAX_DEPTH
import io.github.whywood.deepdeep.plugin.gui.GuiBounds
import io.github.whywood.deepdeep.plugin.gui.GuiNodeTree
import io.github.whywood.deepdeep.plugin.gui.GuiScrollDirection
import io.github.whywood.deepdeep.plugin.gui.GuiSelector
import io.github.whywood.deepdeep.plugin.gui.GuiSnapshot
import io.github.whywood.deepdeep.plugin.gui.GuiSnapshotNode

internal const val SCREEN_DEFAULT_MAX_DEPTH = GUI_MAX_DEPTH

internal fun GuiBounds.isUsableOn(display: GuiBounds): Boolean {
    if (width <= 0 || height <= 0) return false
    if (right <= display.left || left >= display.right) return false
    if (bottom <= display.top || top >= display.bottom) return false
    return true
}

internal fun GuiSnapshot.usableOn(display: GuiBounds): GuiSnapshot {
    val usable = nodes.filter { node -> node.bounds.isUsableOn(display) }
    return if (usable.size == nodes.size) this else copy(nodes = usable)
}

internal fun findScreenNode(snapshot: GuiSnapshot, selector: GuiSelector): GuiSnapshotNode? =
    when (selector) {
        is GuiSelector.Ref -> {
            val ref = GuiNodeTree.parseRef(selector.ref)?.toString()
            if (ref == null) null else snapshot.nodes.firstOrNull { node -> node.ref == ref }
        }
        else -> GuiNodeTree.find(snapshot, selector)
    }

internal fun clampedSwipePath(direction: GuiScrollDirection, bounds: GuiBounds, display: GuiBounds): IntArray? {
    if (!bounds.isUsableOn(display)) return null
    val raw = swipePathFor(direction, bounds)
    val startX = raw[0].coerceIn(display.left, display.right - 1)
    val startY = raw[1].coerceIn(display.top, display.bottom - 1)
    val endX = raw[2].coerceIn(display.left, display.right - 1)
    val endY = raw[3].coerceIn(display.top, display.bottom - 1)
    if (startX == endX && startY == endY) return null
    return intArrayOf(startX, startY, endX, endY)
}

internal fun GuiBounds.centerWithin(display: GuiBounds): Pair<Int, Int> =
    centerX.coerceIn(display.left, display.right - 1) to centerY.coerceIn(display.top, display.bottom - 1)

internal fun invalidTapCoordinate(x: Int, y: Int, display: GuiBounds): String? {
    if (x < display.left || x >= display.right || y < display.top || y >= display.bottom) {
        return "点击坐标超出屏幕范围：屏幕 ${display.width}×${display.height}，收到 ($x, $y)；" +
            "请重新 screen_observe 后按节点坐标或 ref 点击"
    }
    return null
}

internal fun invalidSwipeCoordinates(x1: Int, y1: Int, x2: Int, y2: Int, display: GuiBounds): String? {
    val outsideX = listOf(x1, x2).any { x -> x < display.left || x >= display.right }
    val outsideY = listOf(y1, y2).any { y -> y < display.top || y >= display.bottom }
    if (outsideX || outsideY) {
        return "滑动坐标超出屏幕范围：屏幕 ${display.width}×${display.height}，收到 ($x1, $y1) → ($x2, $y2)"
    }
    if (x1 == x2 && y1 == y2) {
        return "滑动起点与终点相同：请提供不同的起点和终点坐标"
    }
    return null
}
