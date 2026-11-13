package io.github.whywood.deepdeep.plugin.host.extensions.data

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import io.github.whywood.deepdeep.plugin.api.AccessibilityServicePlugin
import io.github.whywood.deepdeep.plugin.api.PluginAccessibilityEvent
import io.github.whywood.deepdeep.plugin.api.PluginExperimentalApi
import io.github.whywood.deepdeep.plugin.api.PluginGlobalAction
import io.github.whywood.deepdeep.service.GuiAccessibilityService
import io.github.whywood.deepdeep.service.GuiAccessibilityState
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

internal object AccessibilityEventHub {
    private val _events = MutableSharedFlow<PluginAccessibilityEvent>(
        extraBufferCapacity = EVENTS_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val flow = _events.asSharedFlow()

    @Volatile
    private var service: GuiAccessibilityService? = null

    val enabled: Boolean
        get() = GuiAccessibilityState.connected.value || service != null

    fun events(): Flow<PluginAccessibilityEvent> = flow

    fun bind(target: GuiAccessibilityService) {
        service = target
    }

    fun unbind(target: GuiAccessibilityService) {
        if (service === target) service = null
    }

    fun publish(event: AccessibilityEvent) {
        if (!enabled) return
        _events.tryEmit(
            PluginAccessibilityEvent(
                eventType = event.eventType,
                packageName = event.packageName?.toString(),
                className = event.className?.toString(),
                text = event.text?.firstOrNull()?.toString()?.takeIf { it.isNotBlank() },
                contentDescription = event.contentDescription?.toString()?.takeIf { it.isNotBlank() },
                timeMs = event.eventTime,
            ),
        )
    }

    fun performGlobalAction(action: PluginGlobalAction): Boolean {
        val constant = globalActionConstant(action.wire) ?: return false
        val active = service ?: return false
        return runCatching { active.performGlobalAction(constant) }.getOrDefault(false)
    }

    internal fun globalActionConstant(wire: String): Int? = when (wire) {
        PluginGlobalAction.BACK.wire -> AccessibilityService.GLOBAL_ACTION_BACK
        PluginGlobalAction.HOME.wire -> AccessibilityService.GLOBAL_ACTION_HOME
        PluginGlobalAction.RECENTS.wire -> AccessibilityService.GLOBAL_ACTION_RECENTS
        PluginGlobalAction.NOTIFICATIONS.wire -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
        PluginGlobalAction.QUICK_SETTINGS.wire -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
        PluginGlobalAction.POWER_DIALOG.wire -> AccessibilityService.GLOBAL_ACTION_POWER_DIALOG
        else -> null
    }

    private const val EVENTS_BUFFER = 128
}

@PluginExperimentalApi
internal class AccessibilityPluginServiceImpl(
    private val hub: AccessibilityEventHub,
) : AccessibilityServicePlugin {
    override val enabled: Boolean get() = hub.enabled

    override fun events(): Flow<PluginAccessibilityEvent> = hub.events()

    override fun performGlobalAction(action: PluginGlobalAction): Boolean = hub.performGlobalAction(action)
}
