package io.github.whywood.deepdeep.plugin.gui

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import io.github.whywood.deepdeep.service.GuiAccessibilityService
import io.github.whywood.deepdeep.service.GuiAccessibilityState
import kotlinx.coroutines.delay

class GuiAccessibilityActions(
    private val serviceProvider: () -> GuiAccessibilityService? = { GuiAccessibilityState.service },
) : GuiActions {
    @Volatile
    private var snapshot: GuiSnapshot? = null

    override val connected: Boolean
        get() = serviceProvider() != null

    override suspend fun observe(maxDepth: Int, maxNodes: Int): GuiSnapshot {
        val service = requireService()
        val root = service.rootInActiveWindow
            ?: throw IllegalStateException("无法读取当前窗口：请解锁屏幕并回到目标界面后重试")
        val result = GuiNodeTree.buildSnapshot(
            root = convert(root),
            packageName = root.packageName?.toString()?.takeIf { it.isNotBlank() } ?: service.currentPackageName,
            windowTitle = service.currentWindowTitle,
            maxDepth = maxDepth,
            maxNodes = maxNodes,
        )
        snapshot = result
        return result
    }

    override suspend fun tap(selector: GuiSelector, durationMs: Int) {
        val service = requireService()
        if (selector is GuiSelector.Point) {
            tapPoint(service, selector.x, selector.y, durationMs)
            return
        }
        val node = requireNode(selector)
        if (node.bounds.width <= 0 || node.bounds.height <= 0) {
            throw IllegalStateException("目标节点当前不可见，请重新 gui_observe")
        }
        if (durationMs >= LONG_PRESS_MILLIS) {
            tapPoint(service, node.bounds.centerX, node.bounds.centerY, durationMs)
            return
        }
        val info = node.token as? AccessibilityNodeInfo
        if (info == null || !clickNode(info)) {
            tapPoint(service, node.bounds.centerX, node.bounds.centerY, durationMs)
        }
    }

    override suspend fun setText(selector: GuiSelector, text: String) {
        requireService()
        if (selector is GuiSelector.Point) {
            throw IllegalArgumentException("输入文本需要 ref 或 text 选择器，不能使用坐标")
        }
        val info = requireNode(selector).token as? AccessibilityNodeInfo
            ?: throw IllegalStateException("无法编辑该节点，请重新 gui_observe")
        if (!info.refresh()) throw IllegalArgumentException("输入框已失效，请重新 gui_observe")
        if (!info.isEditable) throw IllegalArgumentException("目标节点不是输入框，请选择可编辑的节点")
        info.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        if (!info.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
            throw IllegalStateException("写入文本失败：输入框可能不接受程序输入")
        }
    }

    override suspend fun scroll(direction: GuiScrollDirection, amount: Int, target: GuiSelector?) {
        val service = requireService()
        val bounds = if (target == null) {
            val root = service.rootInActiveWindow ?: throw IllegalStateException("无法读取当前窗口")
            val rect = Rect()
            root.getBoundsInScreen(rect)
            GuiBounds(rect.left, rect.top, rect.right, rect.bottom)
        } else {
            requireNode(target).bounds
        }
        if (bounds.width <= 0 || bounds.height <= 0) {
            throw IllegalStateException("目标区域不可见，无法滚动")
        }
        repeat(amount) {
            dispatch(service, swipePath(direction, bounds), SWIPE_MILLIS)
            delay(SWIPE_INTERVAL_MILLIS)
        }
    }

    override suspend fun global(action: GuiGlobalAction) {
        val service = requireService()
        val code = when (action) {
            GuiGlobalAction.BACK -> AccessibilityService.GLOBAL_ACTION_BACK
            GuiGlobalAction.HOME -> AccessibilityService.GLOBAL_ACTION_HOME
            GuiGlobalAction.RECENTS -> AccessibilityService.GLOBAL_ACTION_RECENTS
            GuiGlobalAction.NOTIFICATIONS -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
        }
        if (!service.performGlobalAction(code)) {
            throw IllegalStateException("全局操作执行失败：无障碍服务可能已断开")
        }
    }

    override suspend fun waitFor(packageName: String?, text: String?, timeoutMs: Long): Boolean {
        val service = requireService()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            val root = service.rootInActiveWindow
            val currentPackage = root?.packageName?.toString()?.takeIf { it.isNotBlank() }
                ?: service.currentPackageName
            val packageMatches = packageName == null || currentPackage == packageName
            val textMatches = text == null || (root != null && containsText(root, text, 0))
            if (packageMatches && textMatches) return true
            if (System.currentTimeMillis() >= deadline) return false
            delay(WAIT_POLL_MILLIS)
        }
    }

    private fun requireService(): GuiAccessibilityService =
        serviceProvider() ?: throw GuiUnavailableException(
            "无障碍服务未开启：请在插件页打开无障碍设置并启用 DeepDeep 无障碍服务",
        )

    private fun requireNode(selector: GuiSelector): GuiSnapshotNode {
        val current = snapshot ?: throw IllegalStateException("请先调用 gui_observe 读取当前界面")
        return GuiNodeTree.find(current, selector)
            ?: throw IllegalArgumentException("界面上找不到匹配的节点，请重新 gui_observe")
    }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        if (!node.refresh()) return false
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth <= CLICK_ANCESTOR_LIMIT) {
            if (current.isClickable && current.isEnabled &&
                current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            ) {
                return true
            }
            current = current.parent
            depth += 1
        }
        return false
    }

    private fun containsText(node: AccessibilityNodeInfo, text: String, depth: Int): Boolean {
        if (depth > MAX_WAIT_DEPTH) return false
        if (node.text?.toString()?.contains(text, ignoreCase = true) == true) return true
        if (node.contentDescription?.toString()?.contains(text, ignoreCase = true) == true) return true
        for (index in 0 until node.childCount) {
            val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
            if (containsText(child, text, depth + 1)) return true
        }
        return false
    }

    private fun convert(node: AccessibilityNodeInfo, depth: Int = 0): GuiNode {
        val children = if (depth >= MAX_LIVE_DEPTH) {
            emptyList()
        } else {
            (0 until node.childCount).mapNotNull { index ->
                runCatching { node.getChild(index) }.getOrNull()?.let { child -> convert(child, depth + 1) }
            }
        }
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return GuiNode(
            className = node.className?.toString().orEmpty(),
            bounds = GuiBounds(rect.left, rect.top, rect.right, rect.bottom),
            text = node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_LIVE_TEXT),
            desc = node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_LIVE_TEXT),
            resourceId = node.viewIdResourceName,
            clickable = node.isClickable,
            scrollable = node.isScrollable,
            editable = node.isEditable,
            enabled = node.isEnabled,
            children = children,
            token = node,
        )
    }

    private suspend fun tapPoint(service: AccessibilityService, x: Int, y: Int, durationMs: Int) {
        val duration = durationMs.coerceAtLeast(MIN_GESTURE_MILLIS).toLong()
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
            lineTo(x + 1f, y.toFloat())
        }
        dispatch(service, path, duration)
    }

    private fun swipePath(direction: GuiScrollDirection, bounds: GuiBounds): Path {
        val left = bounds.left + (bounds.width * SWIPE_INSET).toInt()
        val right = bounds.right - (bounds.width * SWIPE_INSET).toInt()
        val top = bounds.top + (bounds.height * SWIPE_INSET).toInt()
        val bottom = bounds.bottom - (bounds.height * SWIPE_INSET).toInt()
        val centerX = (left + right) / 2
        val centerY = (top + bottom) / 2
        val points = when (direction) {
            GuiScrollDirection.DOWN -> listOf(centerX, bottom, centerX, top)
            GuiScrollDirection.UP -> listOf(centerX, top, centerX, bottom)
            GuiScrollDirection.RIGHT -> listOf(right, centerY, left, centerY)
            GuiScrollDirection.LEFT -> listOf(left, centerY, right, centerY)
        }
        return Path().apply {
            moveTo(points[0].toFloat(), points[1].toFloat())
            lineTo(points[2].toFloat(), points[3].toFloat())
        }
    }

    private suspend fun dispatch(service: AccessibilityService, path: Path, durationMillis: Long) {
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, durationMillis))
            .build()
        if (!service.dispatchGesture(gesture, null, null)) {
            throw IllegalStateException("手势派发失败：无障碍服务可能已断开")
        }
        delay(durationMillis + GESTURE_SETTLE_MILLIS)
    }

    private companion object {
        const val MAX_LIVE_DEPTH = 24
        const val MAX_LIVE_TEXT = 200
        const val MAX_WAIT_DEPTH = 24
        const val LONG_PRESS_MILLIS = 400
        const val MIN_GESTURE_MILLIS = 40
        const val CLICK_ANCESTOR_LIMIT = 5
        const val SWIPE_INSET = 0.15f
        const val SWIPE_MILLIS = 240L
        const val SWIPE_INTERVAL_MILLIS = 120L
        const val GESTURE_SETTLE_MILLIS = 80L
        const val WAIT_POLL_MILLIS = 150L
    }
}
