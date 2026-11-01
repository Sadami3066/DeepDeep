package io.github.whywood.deepdeep.plugin.gui

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Bitmap
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import io.github.whywood.deepdeep.service.GuiAccessibilityService
import io.github.whywood.deepdeep.service.GuiAccessibilityState
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class GuiAccessibilityActions(
    private val serviceProvider: () -> GuiAccessibilityService? = { GuiAccessibilityState.service },
) : GuiActions {
    private val snapshots = GuiSnapshotCache()

    override val connected: Boolean
        get() = serviceProvider() != null

    override fun invalidateSnapshots() = snapshots.clear()

    override fun bounds(displayId: Int, selector: GuiSelector): GuiBounds = requireNode(displayId, selector).bounds

    override suspend fun observe(displayId: Int, maxDepth: Int, maxNodes: Int): GuiSnapshot {
        require(displayId >= 0) { "屏幕编号不能为负数" }
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
            ?: throw IllegalStateException("无法读取所选屏幕界面：请在该屏幕打开目标应用后重试")
        val root = window.root
            ?: throw IllegalStateException("无法读取所选屏幕内容：请确认无障碍服务可以读取该窗口")
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
            require(displayId >= 0) { "屏幕编号不能为负数" }
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

    private fun requireNode(displayId: Int, selector: GuiSelector): GuiSnapshotNode {
        val cached = snapshots.find(displayId, selector)
        val node = cached.token as? AccessibilityNodeInfo ?: throw IllegalStateException("节点已失效，请重新 screen_observe")
        if (!node.refresh() || selectDisplayWindow(requireService(), displayId)?.id != node.windowId) {
            snapshots.remove(displayId)
            throw IllegalStateException("所选屏幕窗口已变化，请重新 screen_observe")
        }
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return cached.copy(bounds = GuiBounds(rect.left, rect.top, rect.right, rect.bottom))
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
        val windows = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            service.windowsOnAllDisplays[displayId] ?: return null
        } else {
            if (displayId != 0) return null
            service.windows
        }
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
        require(displayId >= 0) { "屏幕编号不能为负数" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) builder.setDisplayId(displayId)
        else check(displayId == 0) { "虚拟屏手势需要 Android 11 及以上系统" }
        val gesture = builder.build()
        val completed = withTimeoutOrNull(durationMillis + 5000L) {
            suspendCancellableCoroutine<Unit> { continuation ->
                val callback = object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) continuation.resumeWithException(IllegalStateException("手势被系统取消；请观察当前页面后再决定下一步"))
                    }
                }
                if (!service.dispatchGesture(gesture, callback, null) && continuation.isActive) {
                    continuation.resumeWithException(IllegalStateException("手势派发失败：无障碍服务可能已断开"))
                }
            }
        }
        check(completed != null) { "未确认手势完成，请先观察当前屏幕；不要自动重试" }
        delay(GESTURE_SETTLE_MILLIS)
    }

    override suspend fun swipeMain(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long) {
        val path = Path().apply { moveTo(x1.toFloat(), y1.toFloat()); lineTo(x2.toFloat(), y2.toFloat()) }
        dispatch(requireService(), path, durationMs, 0)
    }

    override suspend fun keyMain(name: String) {
        val service = requireService()
        if (name == "enter" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val root = selectDisplayWindow(service, 0)?.root ?: error("主屏没有可读窗口")
            val field = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: error("主屏没有获得焦点的输入框")
            check(field.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)) { "输入框不支持确认，请按节点点击确认按钮" }
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val displays = service.windowsOnAllDisplays
            check((0 until displays.size()).none { index -> displays.keyAt(index) != 0 && displays.valueAt(index).any { it.isFocused } }) {
                "另一块屏幕拥有焦点，无法安全执行全局按键；请启用 ADB 或 Root 后重试"
            }
        }
        val action = when (name) {
            "back" -> AccessibilityService.GLOBAL_ACTION_BACK
            "home" -> AccessibilityService.GLOBAL_ACTION_HOME
            "recents" -> AccessibilityService.GLOBAL_ACTION_RECENTS
            "notifications" -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            else -> error("此主屏按键需要 ADB 或 Root；文本编辑可改用 screen_type 的输入框 ref")
        }
        check(service.performGlobalAction(action)) { "系统拒绝了主屏按键，请观察当前状态后再决定下一步" }
    }

    override suspend fun screenshotMain(): GuiScreenshot {
        val service = requireService()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) error("主屏截图需要 Android 11 或以上系统")
        check((service.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isKeyguardLocked != true) { "请先解锁屏幕后重试" }
        return withTimeoutOrNull(10000L) {
            suspendCancellableCoroutine { continuation ->
                service.takeScreenshot(0, Dispatchers.IO.asExecutor(), object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        val buffer = result.hardwareBuffer
                        try {
                            if (!continuation.isActive) return
                            val bitmap = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace) ?: error("无法解析截图")
                            try {
                                val output = ByteArrayOutputStream()
                                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "截图编码失败" }
                                val capture = GuiScreenshot(output.toByteArray(), bitmap.width, bitmap.height)
                                continuation.resume(capture)
                            } finally { bitmap.recycle() }
                        } catch (error: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(error)
                        } finally { buffer.close() }
                    }
                    override fun onFailure(errorCode: Int) {
                        val message = when (errorCode) {
                            AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW -> "当前界面受保护，不能截图，请用户自行查看"
                            AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> "截图过于频繁，请稍后重试"
                            else -> "主屏截图失败（$errorCode），请检查无障碍连接与当前屏幕状态"
                        }
                        if (continuation.isActive) continuation.resumeWithException(IllegalStateException(message))
                    }
                })
            }
        } ?: error("主屏截图超时，请检查无障碍连接后重新观察")
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
    "屏幕 $displayId 的快照已失效，请对同一 target 调用 screen_observe 后再使用 ref 或文本选择器"
