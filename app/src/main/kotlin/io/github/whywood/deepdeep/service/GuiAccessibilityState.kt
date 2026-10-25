package io.github.whywood.deepdeep.service

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object GuiAccessibilityState {
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    @Volatile
    var service: GuiAccessibilityService? = null
        private set

    private var stateListener: AccessibilityManager.AccessibilityStateChangeListener? = null

    private var stateManager: AccessibilityManager? = null

    fun attach(service: GuiAccessibilityService) {
        this.service = service
        _connected.value = true
        register(service)
    }

    fun detach(service: GuiAccessibilityService) {
        if (this.service !== service) return
        this.service = null
        _connected.value = false
        unregister()
    }

    fun refresh(context: Context) {
        if (isEnabled(context)) {
            if (service != null) _connected.value = true
        } else {
            service = null
            _connected.value = false
        }
    }

    fun register(context: Context) {
        if (stateListener != null) return
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return
        val application = context.applicationContext ?: context
        val listener = AccessibilityManager.AccessibilityStateChangeListener { refresh(application) }
        stateListener = listener
        stateManager = manager
        runCatching { manager.addAccessibilityStateChangeListener(listener) }
    }

    fun unregister() {
        val listener = stateListener ?: return
        stateListener = null
        runCatching { stateManager?.removeAccessibilityStateChangeListener(listener) }
        stateManager = null
    }

    fun isEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val fromManager = manager
            ?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            ?.any { info ->
                val serviceInfo = info.resolveInfo?.serviceInfo
                serviceInfo != null &&
                    serviceInfo.packageName == context.packageName &&
                    serviceInfo.name == GuiAccessibilityService::class.java.name
            }
        if (fromManager == true) return true
        val enabled = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        }.getOrNull() ?: return false
        val component = "${context.packageName}/${GuiAccessibilityService::class.java.name}"
        return enabled.split(':').any { it.trim() == component }
    }
}
