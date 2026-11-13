package io.github.whywood.deepdeep.plugin.host.extensions.device

import io.github.whywood.deepdeep.plugin.api.PluginPermission
import io.github.whywood.deepdeep.plugin.api.PluginSpecialAccess

internal object PluginPermissionMapping {
    private val runtimePermissions: Map<String, String> = mapOf(
        PluginPermission.LOCATION_COARSE to "android.permission.ACCESS_COARSE_LOCATION",
        PluginPermission.LOCATION_FINE to "android.permission.ACCESS_FINE_LOCATION",
        PluginPermission.CONTACTS_READ to "android.permission.READ_CONTACTS",
        PluginPermission.CONTACTS_WRITE to "android.permission.WRITE_CONTACTS",
        PluginPermission.SMS_READ to "android.permission.READ_SMS",
        PluginPermission.SMS_SEND to "android.permission.SEND_SMS",
        PluginPermission.CALENDAR_READ to "android.permission.READ_CALENDAR",
        PluginPermission.CALENDAR_WRITE to "android.permission.WRITE_CALENDAR",
        PluginPermission.CAMERA to "android.permission.CAMERA",
        PluginPermission.MICROPHONE to "android.permission.RECORD_AUDIO",
        PluginPermission.NOTIFICATIONS_POST to "android.permission.POST_NOTIFICATIONS",
    )

    private val specialAccesses: Map<String, PluginSpecialAccess> = mapOf(
        PluginPermission.NOTIFICATIONS_LISTEN to PluginSpecialAccess.NOTIFICATION_LISTENER,
        PluginPermission.ACCESSIBILITY to PluginSpecialAccess.ACCESSIBILITY,
        PluginPermission.OVERLAY to PluginSpecialAccess.OVERLAY,
        PluginPermission.USAGE_STATS to PluginSpecialAccess.USAGE_STATS,
        PluginPermission.BATTERY_OPTIMIZATION to PluginSpecialAccess.BATTERY_OPTIMIZATION,
        PluginPermission.INSTALL_PACKAGES to PluginSpecialAccess.INSTALL_PACKAGES,
        PluginPermission.MANAGE_STORAGE to PluginSpecialAccess.MANAGE_STORAGE,
    )

    fun androidPermission(requested: String): String? {
        val normalized = requested.trim()
        if (normalized.startsWith(ANDROID_PERMISSION_PREFIX)) return normalized
        return runtimePermissions[normalized]
    }

    fun specialAccess(requested: String): PluginSpecialAccess? = specialAccesses[requested.trim()]

    fun runtimeMappings(): Map<String, String> = runtimePermissions

    fun specialMappings(): Map<String, PluginSpecialAccess> = specialAccesses

    private const val ANDROID_PERMISSION_PREFIX = "android.permission."
}
