package io.github.whywood.deepdeep.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import io.github.whywood.deepdeep.plugin.host.extensions.data.AccessibilityEventHub
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.min

class GuiAccessibilityService : AccessibilityService() {
    private val lastEventByDisplay = ConcurrentHashMap<Int, Long>()
    private val displayChanges = MutableSharedFlow<Int>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo?.let { info ->
            info.flags = info.flags or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            serviceInfo = info
        }
        GuiAccessibilityState.attach(this)
        AccessibilityEventHub.bind(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType and IGNORED_EVENTS == 0) {
            val display = displayOf(event)
            if (display != Display.INVALID_DISPLAY) {
                lastEventByDisplay[display] = SystemClock.uptimeMillis()
                displayChanges.tryEmit(display)
            }
        }
        AccessibilityEventHub.publish(event)
    }

    internal suspend fun awaitQuiet(displayId: Int, quietMs: Long, capMs: Long): Boolean {
        val start = SystemClock.uptimeMillis()
        val deadline = start + capMs.coerceAtLeast(0)
        while (true) {
            val now = SystemClock.uptimeMillis()
            val quietFor = now - maxOf(start, lastEventByDisplay[displayId] ?: start)
            if (quietFor >= quietMs) return true
            if (now >= deadline) return false
            delay(min(quietMs - quietFor, deadline - now).coerceIn(10, 100))
        }
    }

    internal suspend fun awaitDisplayChange(displayId: Int, timeoutMs: Long): Boolean =
        withTimeoutOrNull(timeoutMs.coerceAtLeast(0)) { displayChanges.first { it == displayId } } != null

    private fun displayOf(event: AccessibilityEvent): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return event.displayId
        val windowId = event.windowId
        if (windowId == -1) return Display.INVALID_DISPLAY
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return if (windows.any { it.id == windowId }) Display.DEFAULT_DISPLAY else Display.INVALID_DISPLAY
        }
        val all = windowsOnAllDisplays
        for (i in 0 until all.size()) {
            if (all.valueAt(i).any { it.id == windowId }) return all.keyAt(i)
        }
        return Display.INVALID_DISPLAY
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        AccessibilityEventHub.unbind(this)
        GuiAccessibilityState.detach(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        AccessibilityEventHub.unbind(this)
        GuiAccessibilityState.detach(this)
        super.onDestroy()
    }

    private companion object {
        const val IGNORED_EVENTS = AccessibilityEvent.TYPE_VIEW_HOVER_ENTER or AccessibilityEvent.TYPE_VIEW_HOVER_EXIT or
            AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START or AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_END or
            AccessibilityEvent.TYPE_GESTURE_DETECTION_START or AccessibilityEvent.TYPE_GESTURE_DETECTION_END or
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START or AccessibilityEvent.TYPE_TOUCH_INTERACTION_END or
            AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED or AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED
    }
}
