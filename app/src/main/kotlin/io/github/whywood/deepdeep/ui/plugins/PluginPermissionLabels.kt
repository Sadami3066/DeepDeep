package io.github.whywood.deepdeep.ui.plugins

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.plugin.api.PluginPermission

@Composable
internal fun permissionSummary(permissions: List<String>): String {
    val labels = permissions.distinct().map { wire -> stringResource(permissionLabelRes(wire)) }
    return labels.joinToString(", ")
}

@Composable
internal fun componentSummary(components: List<String>): String {
    val labels = components.distinct().map { wire -> stringResource(componentLabelRes(wire)) }
    return labels.joinToString(", ")
}

private fun permissionLabelRes(wire: String): Int = when (wire) {
    PluginPermission.LOCATION_COARSE -> R.string.plugins_permission_location_coarse
    PluginPermission.LOCATION_FINE -> R.string.plugins_permission_location_fine
    PluginPermission.CONTACTS_READ -> R.string.plugins_permission_contacts_read
    PluginPermission.CONTACTS_WRITE -> R.string.plugins_permission_contacts_write
    PluginPermission.SMS_READ -> R.string.plugins_permission_sms_read
    PluginPermission.SMS_SEND -> R.string.plugins_permission_sms_send
    PluginPermission.CALENDAR_READ -> R.string.plugins_permission_calendar_read
    PluginPermission.CALENDAR_WRITE -> R.string.plugins_permission_calendar_write
    PluginPermission.CAMERA -> R.string.plugins_permission_camera
    PluginPermission.MICROPHONE -> R.string.plugins_permission_microphone
    PluginPermission.NOTIFICATIONS_POST -> R.string.plugins_permission_notifications_post
    PluginPermission.NOTIFICATIONS_LISTEN -> R.string.plugins_permission_notifications_listen
    PluginPermission.ACCESSIBILITY -> R.string.plugins_permission_accessibility
    PluginPermission.OVERLAY -> R.string.plugins_permission_overlay
    PluginPermission.USAGE_STATS -> R.string.plugins_permission_usage_stats
    PluginPermission.BATTERY_OPTIMIZATION -> R.string.plugins_permission_battery_optimization
    PluginPermission.INSTALL_PACKAGES -> R.string.plugins_permission_install_packages
    PluginPermission.MANAGE_STORAGE -> R.string.plugins_permission_manage_storage
    else -> R.string.plugins_permission_other
}

private fun componentLabelRes(wire: String): Int = when (wire) {
    "background" -> R.string.plugins_component_background
    "foregroundService" -> R.string.plugins_component_foreground_service
    "notificationListener" -> R.string.plugins_component_notification_listener
    "accessibility" -> R.string.plugins_component_accessibility
    "share" -> R.string.plugins_component_share
    "deeplinks" -> R.string.plugins_component_deeplinks
    else -> R.string.plugins_permission_other
}
