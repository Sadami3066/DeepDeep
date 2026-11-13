package io.github.whywood.deepdeep.plugin.host.extensions.device

import io.github.whywood.deepdeep.plugin.api.DeviceSettings
import io.github.whywood.deepdeep.plugin.api.PluginSpecialAccess

internal enum class DeviceSettingsScreen {
    APP_DETAILS,
    APP_NOTIFICATIONS,
    ACCESSIBILITY,
    NOTIFICATION_LISTENER,
    OVERLAY,
    USAGE_STATS,
    BATTERY_OPTIMIZATION,
    INSTALL_PACKAGES,
    MANAGE_STORAGE,
    DEVELOPER_OPTIONS,
    LOCATION,
}

internal fun settingsScreenOf(section: DeviceSettings): DeviceSettingsScreen = when (section) {
    DeviceSettings.APP -> DeviceSettingsScreen.APP_DETAILS
    DeviceSettings.NOTIFICATIONS -> DeviceSettingsScreen.APP_NOTIFICATIONS
    DeviceSettings.ACCESSIBILITY -> DeviceSettingsScreen.ACCESSIBILITY
    DeviceSettings.NOTIFICATION_LISTENER -> DeviceSettingsScreen.NOTIFICATION_LISTENER
    DeviceSettings.OVERLAY -> DeviceSettingsScreen.OVERLAY
    DeviceSettings.USAGE_STATS -> DeviceSettingsScreen.USAGE_STATS
    DeviceSettings.BATTERY_OPTIMIZATION -> DeviceSettingsScreen.BATTERY_OPTIMIZATION
    DeviceSettings.INSTALL_PACKAGES -> DeviceSettingsScreen.INSTALL_PACKAGES
    DeviceSettings.STORAGE -> DeviceSettingsScreen.MANAGE_STORAGE
    DeviceSettings.DEVELOPER -> DeviceSettingsScreen.DEVELOPER_OPTIONS
    DeviceSettings.WIRELESS_DEBUGGING -> DeviceSettingsScreen.DEVELOPER_OPTIONS
    DeviceSettings.LOCATION -> DeviceSettingsScreen.LOCATION
}

internal fun settingsScreenOf(access: PluginSpecialAccess): DeviceSettingsScreen? =
    when (access.wire) {
        PluginSpecialAccess.NOTIFICATION_LISTENER.wire -> DeviceSettingsScreen.NOTIFICATION_LISTENER
        PluginSpecialAccess.ACCESSIBILITY.wire -> DeviceSettingsScreen.ACCESSIBILITY
        PluginSpecialAccess.OVERLAY.wire -> DeviceSettingsScreen.OVERLAY
        PluginSpecialAccess.USAGE_STATS.wire -> DeviceSettingsScreen.USAGE_STATS
        PluginSpecialAccess.BATTERY_OPTIMIZATION.wire -> DeviceSettingsScreen.BATTERY_OPTIMIZATION
        PluginSpecialAccess.INSTALL_PACKAGES.wire -> DeviceSettingsScreen.INSTALL_PACKAGES
        PluginSpecialAccess.MANAGE_STORAGE.wire -> DeviceSettingsScreen.MANAGE_STORAGE
        else -> null
    }
