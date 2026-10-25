package io.github.whywood.deepdeep.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class GuiAccessibilityService : AccessibilityService() {
    @Volatile
    var currentPackageName: String? = null
        private set

    @Volatile
    var currentWindowTitle: String? = null
        private set

    override fun onServiceConnected() {
        super.onServiceConnected()
        GuiAccessibilityState.attach(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        event.packageName?.toString()?.takeIf { it.isNotBlank() }?.let { currentPackageName = it }
        currentWindowTitle = event.text
            .firstOrNull { it.isNotBlank() }
            ?.toString()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        GuiAccessibilityState.detach(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        GuiAccessibilityState.detach(this)
        super.onDestroy()
    }
}
