package io.github.whywood.deepdeep.plugin.host.extensions.device

import io.github.whywood.deepdeep.plugin.api.DeviceSettings
import io.github.whywood.deepdeep.plugin.api.PluginSpecialAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DeviceSettingsMappingTest {
    @Test
    fun mapsEveryDeviceSettingToScreen() {
        val expected = mapOf(
            DeviceSettings.APP to DeviceSettingsScreen.APP_DETAILS,
            DeviceSettings.NOTIFICATIONS to DeviceSettingsScreen.APP_NOTIFICATIONS,
            DeviceSettings.ACCESSIBILITY to DeviceSettingsScreen.ACCESSIBILITY,
            DeviceSettings.NOTIFICATION_LISTENER to DeviceSettingsScreen.NOTIFICATION_LISTENER,
            DeviceSettings.OVERLAY to DeviceSettingsScreen.OVERLAY,
            DeviceSettings.USAGE_STATS to DeviceSettingsScreen.USAGE_STATS,
            DeviceSettings.BATTERY_OPTIMIZATION to DeviceSettingsScreen.BATTERY_OPTIMIZATION,
            DeviceSettings.INSTALL_PACKAGES to DeviceSettingsScreen.INSTALL_PACKAGES,
            DeviceSettings.STORAGE to DeviceSettingsScreen.MANAGE_STORAGE,
            DeviceSettings.DEVELOPER to DeviceSettingsScreen.DEVELOPER_OPTIONS,
            DeviceSettings.WIRELESS_DEBUGGING to DeviceSettingsScreen.DEVELOPER_OPTIONS,
            DeviceSettings.LOCATION to DeviceSettingsScreen.LOCATION,
        )
        assertEquals(DeviceSettings.entries.toSet(), expected.keys)
        expected.forEach { (section, screen) ->
            assertEquals(section.name, screen, settingsScreenOf(section))
        }
    }

    @Test
    fun mapsEverySpecialAccessToSettingsScreen() {
        val expected = mapOf(
            PluginSpecialAccess.NOTIFICATION_LISTENER to DeviceSettingsScreen.NOTIFICATION_LISTENER,
            PluginSpecialAccess.ACCESSIBILITY to DeviceSettingsScreen.ACCESSIBILITY,
            PluginSpecialAccess.OVERLAY to DeviceSettingsScreen.OVERLAY,
            PluginSpecialAccess.USAGE_STATS to DeviceSettingsScreen.USAGE_STATS,
            PluginSpecialAccess.BATTERY_OPTIMIZATION to DeviceSettingsScreen.BATTERY_OPTIMIZATION,
            PluginSpecialAccess.INSTALL_PACKAGES to DeviceSettingsScreen.INSTALL_PACKAGES,
            PluginSpecialAccess.MANAGE_STORAGE to DeviceSettingsScreen.MANAGE_STORAGE,
        )
        assertEquals(PluginSpecialAccess.ALL.toSet(), expected.keys)
        expected.forEach { (access, screen) ->
            assertEquals(access.wire, screen, settingsScreenOf(access))
        }
        assertNotNull(PluginSpecialAccess.ALL.first())
    }
}
