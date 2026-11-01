package io.github.whywood.deepdeep.plugin.gui

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import io.github.whywood.deepdeep.service.GuiAccessibilityService
import io.github.whywood.deepdeep.service.GuiAccessibilityState
import kotlinx.coroutines.delay

class GuiAccessibilityActions(
    private val serviceProvider: () -> GuiAccessibilityService? = { GuiAccessibilityState.service },
) : GuiActions {
    private val snapshots = GuiSnapshotCache()

    override val connected: Boolean
        get() = serviceProvider() != null

    override fun invalidateSnapshots() = snapshots.clear()

    override fun bounds(displayId: Int, selector: GuiSelector): GuiBounds = requireNode(displayId, selector).bounds

    override suspend fun observe(displayId: Int, maxDepth: Int, maxNodes: Int): GuiSnapshot {
        require(displayId > 0) { "只允许读取虚拟屏" }
        snapshots.remove(displayId)
        val result = snapshotDisplayWindow(requireService(), displayId, maxDepth, maxNodes)
        return snapshots.store(displayId, result)
    }

    private fun snapshotDisplayWindow(
        service: GuiAccessibilityService,
        displayId: Int,
        maxDepth: Int,
        maxNodes: Int,
    ): GuiSnapshot {
        val window = selectDisplayWindow(service, displayId)
            ?: throw IllegalStateException("无法读取虚拟屏界面：请先在虚拟屏启动一个应用后重试")
        val root = window.root
            ?: throw IllegalStateException("无法读取虚拟屏界面内容：请确认无障碍服务可以读取该窗口")
        val generation = java.util.UUID.randomUUID().toString()
        val nodes = ArrayList<GuiSnapshotNode>()
        var truncated = false
        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > maxDepth || nodes.size >= maxNodes) {
                truncated = true
                return
            }
            val rect = Rect()
            node.getBoundsInScreen(rect)
            nodes += GuiSnapshotNode(
                ref = "$displayId:$generation:${nodes.size}",
                className = node.className?.toString().orEmpty(),
                bounds = GuiBounds(rect.left, rect.top, rect.right, rect.bottom),
                text = node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_LIVE_TEXT),
                desc = node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_LIVE_TEXT),
                resourceId = node.viewIdResourceName,
                clickable = node.isClickable,
                scrollable = node.isScrollable,
                editable = node.isEditable,
                enabled = node.isEnabled,
                depth = depth,
                token = node,
            )
            for (index in 0 until node.childCount) {
                if (depth >= maxDepth || nodes.size >= maxNodes) {
                    truncated = true
                    break
                }
                val child = node.getChild(index) ?: continue
                visit(child, depth + 1)
            }
        }
        visit(root, 0)
        return GuiSnapshot(root.packageName?.toString(), window.title?.toString(), nodes, truncated, nodes.size)
    }

    override suspend fun tap(displayId: Int, selector: GuiSelector, durationMs: Int) {
        val service = requireService()
        if (selector is GuiSelector.Point) {
            tapPoint(service, displayId, selector.x, selector.y, durationMs)
            return
        }
        val node = requireNode(displayId, selector)
        if (node.bounds.width <= 0 || node.bounds.height <= 0) {
            throw IllegalStateException("目标节点当前不可见，请重新 screen_observe")
        }
        val info = node.token as? AccessibilityNodeInfo
        if (durationMs >= LONG_PRESS_MILLIS) {
            if (info != null && longClickNode(info)) return
            tapPoint(service, displayId, node.bounds.centerX, node.bounds.centerY, durationMs)
            return
        }
        if (info == null || !clickNode(info)) {
            tapPoint(service, displayId, node.bounds.centerX, node.bounds.centerY, durationMs)
        }
    }

    override suspend fun setText(displayId: Int, selector: GuiSelector, text: String): GuiTextResult {
        requireService()
        if (selector is GuiSelector.Point) {
            throw IllegalArgumentException("输入文本需要 ref 或 text 选择器，不能使用坐标")
        }
        val info = requireNode(displayId, selector).token as? AccessibilityNodeInfo
            ?: throw IllegalStateException("无法编辑该节点，请重新 screen_observe")
        if (!info.refresh()) throw IllegalArgumentException("输入框已失效，请重新 screen_observe")
        if (!info.isEditable) throw IllegalArgumentException("目标节点不是输入框，请选择可编辑的节点")
        info.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        if (!info.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
            throw IllegalStateException("写入文本失败：输入框可能不接受程序输入")
        }
        if (!info.refresh()) throw IllegalStateException("写入后无法回读输入框，请重新 screen_observe 确认")
        val actual = info.text?.toString().orEmpty()
        return GuiTextResult(actual, actual == text)
    }

    override suspend fun waitFor(
        displayId: Int,
        packageName: String?,
        text: String?,
        textGone: String?,
        timeoutMs: Long,
    ): GuiWaitOutcome {
        val service = requireService()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            require(displayId > 0) { "只允许等待虚拟屏" }
            val window = selectDisplayWindow(service, displayId)
            val root = window?.root
            val currentPackage = root?.packageName?.toString()?.takeIf { it.isNotBlank() }
            val windowTitle = window?.title?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            val current = GuiWaitOutcome(matched = false, packageName = currentPackage, windowTitle = windowTitle)
            fun contains(value: String): Boolean =
                windowTitle?.contains(value, ignoreCase = true) == true ||
                    (root != null && containsText(root, value, 0))
            val packageMatches = packageName == null || currentPackage == packageName
            val textMatches = text == null || contains(text)
            val textGoneMatches = textGone == null || (root != null && !contains(textGone))
            if (packageMatches && textMatches && textGoneMatches) return current.copy(matched = true)
            if (System.currentTimeMillis() >= deadline) return current
            delay(WAIT_POLL_MILLIS)
        }
    }

    private fun requireService(): GuiAccessibilityService =
        serviceProvider() ?: throw GuiUnavailableException(
            "无障碍服务未连接：请到系统设置的无障碍中启用 DeepDeep；如果开关已打开，请关闭再重新打开（服务被系统结束后不会自动重连，也可重启手机）",
        )

    private fun requireNode(displayId: Int, selector: GuiSelector): GuiSnapshotNode = snapshots.find(displayId, selector)

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

    private fun longClickNode(node: AccessibilityNodeInfo): Boolean {
        if (!node.refresh()) return false
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth <= CLICK_ANCESTOR_LIMIT) {
            if (current.isLongClickable && current.isEnabled &&
                current.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
            ) {
                return true
            }
            current = current.parent
            depth += 1
        }
        return false
    }

    private fun selectDisplayWindow(service: GuiAccessibilityService, displayId: Int): AccessibilityWindowInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val windows = service.windowsOnAllDisplays[displayId] ?: return null
        val candidates = windows.map { window ->
            val bounds = Rect()
            window.getBoundsInScreen(bounds)
            GuiWindowCandidate(
                kind = windowKindOf(window),
                layer = window.layer,
                readable = window.root != null,
                active = window.isActive,
                focused = window.isFocused,
                area = bounds.width() * bounds.height(),
            )
        }
        val index = GuiWindowSelection.topmostReadable(candidates) ?: return null
        return windows[index]
    }

    private fun windowKindOf(window: AccessibilityWindowInfo): GuiWindowKind = when (window.type) {
        AccessibilityWindowInfo.TYPE_APPLICATION -> GuiWindowKind.APPLICATION
        AccessibilityWindowInfo.TYPE_SYSTEM -> GuiWindowKind.SYSTEM
        AccessibilityWindowInfo.TYPE_INPUT_METHOD -> GuiWindowKind.INPUT_METHOD
        AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> GuiWindowKind.ACCESSIBILITY_OVERLAY
        AccessibilityWindowInfo.TYPE_MAGNIFICATION_OVERLAY -> GuiWindowKind.MAGNIFICATION_OVERLAY
        else -> GuiWindowKind.OTHER
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

    private suspend fun tapPoint(
        service: AccessibilityService,
        displayId: Int,
        x: Int,
        y: Int,
        durationMs: Int,
    ) {
        val duration = durationMs.coerceAtLeast(MIN_GESTURE_MILLIS).toLong()
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
            lineTo(x + 1f, y.toFloat())
        }
        dispatch(service, path, duration, displayId)
    }

    private suspend fun dispatch(
        service: AccessibilityService,
        path: Path,
        durationMillis: Long,
        displayId: Int,
    ) {
        val builder = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, durationMillis))
        require(displayId > 0) { "只允许在虚拟屏派发手势" }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) throw IllegalStateException("虚拟屏手势需要 Android 11 及以上系统")
        builder.setDisplayId(displayId)
        val gesture = builder.build()
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

internal enum class GuiWindowKind {
    APPLICATION,
    SYSTEM,
    INPUT_METHOD,
    ACCESSIBILITY_OVERLAY,
    MAGNIFICATION_OVERLAY,
    OTHER,
}

internal data class GuiWindowCandidate(
    val kind: GuiWindowKind,
    val layer: Int,
    val readable: Boolean,
    val active: Boolean = false,
    val focused: Boolean = false,
    val area: Int = 0,
)

internal object GuiWindowSelection {
    private const val MIN_AREA_RATIO = 0.1f

    fun topmostReadable(candidates: List<GuiWindowCandidate>): Int? {
        val readable = candidates.indices.filter { index ->
            val candidate = candidates[index]
            candidate.readable && candidate.kind in PREFERRED_KINDS && candidate.area > 0
        }
        if (readable.isEmpty()) return null
        val largestArea = readable.maxOf { index -> candidates[index].area }
        val minimumArea = (largestArea * MIN_AREA_RATIO).toInt()
        return readable
            .filter { index ->
                val candidate = candidates[index]
                candidate.active || candidate.focused || candidate.area >= minimumArea
            }
            .maxWithOrNull(
                compareBy<Int> { index -> candidates[index].active }
                    .thenBy { index -> candidates[index].focused }
                    .thenBy { index -> candidates[index].area }
                    .thenBy { index -> candidates[index].layer },
            )
    }

    private val PREFERRED_KINDS = setOf(GuiWindowKind.APPLICATION, GuiWindowKind.SYSTEM)
}

internal fun missingSnapshotMessage(displayId: Int): String =
    "虚拟屏 $displayId 的快照已失效，请先调用 screen_observe 后再使用 ref 或文本选择器"
