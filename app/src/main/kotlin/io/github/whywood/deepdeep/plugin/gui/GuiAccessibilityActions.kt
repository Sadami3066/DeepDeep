package io.github.whywood.deepdeep.plugin.gui

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.annotation.RequiresApi
import io.github.whywood.deepdeep.service.GuiAccessibilityService
import io.github.whywood.deepdeep.service.GuiAccessibilityState
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine

class GuiAccessibilityActions(
    private val serviceProvider: () -> GuiAccessibilityService? = { GuiAccessibilityState.service },
) : GuiActions, GuiDisplayActions {
    private val snapshots = ConcurrentHashMap<Int, GuiSnapshot>()

    override val connected: Boolean
        get() = serviceProvider() != null

    override suspend fun observe(maxDepth: Int, maxNodes: Int): GuiSnapshot =
        observe(GUI_MAIN_DISPLAY, maxDepth, maxNodes)

    override suspend fun observe(displayId: Int, maxDepth: Int, maxNodes: Int): GuiSnapshot {
        val service = requireService()
        val result = if (displayId == GUI_MAIN_DISPLAY) {
            snapshotActiveWindow(service, maxDepth, maxNodes)
        } else {
            snapshotDisplayWindow(service, displayId, maxDepth, maxNodes)
        }
        snapshots[displayId] = result
        return result
    }

    private fun snapshotActiveWindow(service: GuiAccessibilityService, maxDepth: Int, maxNodes: Int): GuiSnapshot {
        val window = mainDisplayWindow(service)
        val root = if (window != null) {
            window.root ?: throw IllegalStateException("无法读取主屏窗口：请解锁屏幕并回到目标界面后重试")
        } else {
            service.rootInActiveWindow
                ?: throw IllegalStateException("无法读取当前窗口：请解锁屏幕并回到目标界面后重试")
        }
        return GuiNodeTree.buildSnapshot(
            root = convert(root),
            packageName = root.packageName?.toString()?.takeIf { it.isNotBlank() } ?: service.currentPackageName,
            windowTitle = window?.title?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: service.currentWindowTitle,
            maxDepth = maxDepth,
            maxNodes = maxNodes,
        )
    }

    private fun mainDisplayWindow(service: GuiAccessibilityService): AccessibilityWindowInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) selectDisplayWindow(service, GUI_MAIN_DISPLAY) else null

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
        return GuiNodeTree.buildSnapshot(
            root = convert(root),
            packageName = root.packageName?.toString()?.takeIf { it.isNotBlank() },
            windowTitle = window.title?.toString()?.trim()?.takeIf { it.isNotEmpty() },
            maxDepth = maxDepth,
            maxNodes = maxNodes,
        )
    }

    override suspend fun tap(selector: GuiSelector, durationMs: Int) {
        tap(GUI_MAIN_DISPLAY, selector, durationMs)
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

    override suspend fun setText(selector: GuiSelector, text: String): GuiTextResult {
        return setText(GUI_MAIN_DISPLAY, selector, text)
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

    override suspend fun scroll(direction: GuiScrollDirection, amount: Int, target: GuiSelector?) {
        val service = requireService()
        val bounds = if (target == null) {
            val root = mainDisplayWindow(service)?.root
                ?: service.rootInActiveWindow
                ?: throw IllegalStateException("无法读取当前窗口")
            val rect = Rect()
            root.getBoundsInScreen(rect)
            GuiBounds(rect.left, rect.top, rect.right, rect.bottom)
        } else {
            requireNode(GUI_MAIN_DISPLAY, target).bounds
        }
        if (bounds.width <= 0 || bounds.height <= 0) {
            throw IllegalStateException("目标区域不可见，无法滚动")
        }
        repeat(amount) {
            dispatch(service, swipePath(direction, bounds), SWIPE_MILLIS, GUI_MAIN_DISPLAY)
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

    override suspend fun waitFor(packageName: String?, text: String?, textGone: String?, timeoutMs: Long): GuiWaitOutcome =
        waitFor(GUI_MAIN_DISPLAY, packageName, text, textGone, timeoutMs)

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
            val window = if (displayId == GUI_MAIN_DISPLAY) {
                mainDisplayWindow(service)
            } else {
                selectDisplayWindow(service, displayId)
            }
            val root = when {
                window != null -> window.root
                displayId == GUI_MAIN_DISPLAY -> service.rootInActiveWindow
                else -> null
            }
            val currentPackage = root?.packageName?.toString()?.takeIf { it.isNotBlank() }
                ?: if (displayId == GUI_MAIN_DISPLAY) service.currentPackageName else null
            val windowTitle = window?.title?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                ?: if (displayId == GUI_MAIN_DISPLAY) service.currentWindowTitle else null
            val current = GuiWaitOutcome(matched = false, packageName = currentPackage, windowTitle = windowTitle)
            fun contains(value: String): Boolean =
                windowTitle?.contains(value, ignoreCase = true) == true ||
                    (root != null && containsText(root, value, 0))
            val packageMatches = packageName == null || currentPackage == packageName
            val textMatches = text == null || contains(text)
            val textGoneMatches = textGone == null || !contains(textGone)
            if (packageMatches && textMatches && textGoneMatches) return current.copy(matched = true)
            if (System.currentTimeMillis() >= deadline) return current
            delay(WAIT_POLL_MILLIS)
        }
    }

    override suspend fun screenshot(): GuiScreenshot {
        val service = requireService()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            throw IllegalStateException("截图需要 Android 11（API 30）及以上系统")
        }
        if (isKeyguardLocked(service)) {
            throw IllegalStateException("锁屏状态下无法截图，请先解锁后重试")
        }
        return encodeScreenshot(awaitScreenshot(service))
    }

    private fun isKeyguardLocked(service: AccessibilityService): Boolean =
        (service.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isKeyguardLocked == true

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun awaitScreenshot(service: GuiAccessibilityService): AccessibilityService.ScreenshotResult =
        suspendCancellableCoroutine { continuation ->
            val callback = object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                    if (continuation.isActive) continuation.resume(screenshot)
                }

                override fun onFailure(errorCode: Int) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(IllegalStateException(screenshotErrorMessage(errorCode)))
                    }
                }
            }
            runCatching { service.takeScreenshot(Display.DEFAULT_DISPLAY, service.mainExecutor, callback) }
                .onFailure { error ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            IllegalStateException("截图请求提交失败：${error.message ?: "无障碍服务可能已断开"}"),
                        )
                    }
                }
        }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun encodeScreenshot(result: AccessibilityService.ScreenshotResult): GuiScreenshot {
        val buffer = requireNotNull(result.hardwareBuffer) { "截图数据为空，请稍后重试" }
        try {
            val bitmap = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                ?: throw IllegalStateException("无法解析截图数据")
            val output = ByteArrayOutputStream()
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw IllegalStateException("截图编码失败")
            }
            return GuiScreenshot(png = output.toByteArray(), width = bitmap.width, height = bitmap.height)
        } finally {
            buffer.close()
        }
    }

    private fun screenshotErrorMessage(errorCode: Int): String = when (errorCode) {
        AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW ->
            "当前界面禁止截图（安全窗口或 FLAG_SECURE），请用户自行查看"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS ->
            "无障碍服务已断开，请重新开启后再截图"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT ->
            "截图过于频繁，请稍后再试"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY ->
            "无法识别当前屏幕，请稍后重试"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_WINDOW ->
            "当前窗口无法截图，请重新 screen_observe 后重试"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR ->
            "系统截图失败，请稍后重试"
        else -> "截图失败（系统错误码 $errorCode）"
    }

    private fun requireService(): GuiAccessibilityService =
        serviceProvider() ?: throw GuiUnavailableException(
            "无障碍服务未连接：请到系统设置的无障碍中启用 DeepDeep；如果开关已打开，请关闭再重新打开（服务被系统结束后不会自动重连，也可重启手机）",
        )

    private fun requireNode(displayId: Int, selector: GuiSelector): GuiSnapshotNode {
        val current = snapshots[displayId] ?: throw IllegalStateException(missingSnapshotMessage(displayId))
        return GuiNodeTree.find(current, selector)
            ?: throw IllegalArgumentException("界面上找不到匹配的节点，请重新 screen_observe")
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

    private suspend fun dispatch(
        service: AccessibilityService,
        path: Path,
        durationMillis: Long,
        displayId: Int,
    ) {
        val builder = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, durationMillis))
        if (displayId != GUI_MAIN_DISPLAY) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                throw IllegalStateException("在副屏上派发手势需要 Android 11（API 30）及以上系统")
            }
            builder.setDisplayId(displayId)
        }
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

internal const val GUI_MAIN_DISPLAY = 0

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
    if (displayId == GUI_MAIN_DISPLAY) {
        "请先调用 screen_observe 读取主屏界面"
    } else {
        "请先调用 screen_observe 读取虚拟屏界面后再使用 ref 或文本选择器"
    }
