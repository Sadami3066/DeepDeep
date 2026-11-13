package io.github.whywood.deepdeep.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import io.github.whywood.deepdeep.plugin.host.extensions.data.AccessibilityEventHub

class GuiAccessibilityService : AccessibilityService() {
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
        if (event != null) AccessibilityEventHub.publish(event)
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
}
