package io.github.whywood.deepdeep.plugin.host.extensions.device

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import io.github.whywood.deepdeep.plugin.api.HostAccessLevel
import io.github.whywood.deepdeep.runtime.host.HostAccess

internal fun hostAccessLevelOf(access: HostAccess): HostAccessLevel = when (access) {
    HostAccess.LOCAL -> HostAccessLevel.LOCAL
    HostAccess.ADB -> HostAccessLevel.ADB
    HostAccess.ROOT -> HostAccessLevel.ROOT
}

internal fun settingsIntentFor(context: Context, screen: DeviceSettingsScreen): Intent? {
    val packageUri = Uri.parse("package:${context.packageName}")
    val intent = when (screen) {
        DeviceSettingsScreen.APP_DETAILS ->
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)

        DeviceSettingsScreen.APP_NOTIFICATIONS ->
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

        DeviceSettingsScreen.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

        DeviceSettingsScreen.NOTIFICATION_LISTENER ->
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

        DeviceSettingsScreen.OVERLAY ->
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri)

        DeviceSettingsScreen.USAGE_STATS -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

        DeviceSettingsScreen.BATTERY_OPTIMIZATION ->
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

        DeviceSettingsScreen.INSTALL_PACKAGES ->
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUri)

        DeviceSettingsScreen.MANAGE_STORAGE -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION, packageUri)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
        }

        DeviceSettingsScreen.DEVELOPER_OPTIONS ->
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)

        DeviceSettingsScreen.LOCATION -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
    }
    return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

internal fun openSettingsScreen(context: Context, screen: DeviceSettingsScreen): Boolean {
    val direct = runCatching { context.startActivity(settingsIntentFor(context, screen)) }.isSuccess
    if (direct) return true
    if (screen == DeviceSettingsScreen.APP_DETAILS) return false
    return runCatching {
        context.startActivity(settingsIntentFor(context, DeviceSettingsScreen.APP_DETAILS))
    }.isSuccess
}

internal fun startActivitySafely(context: Context, intent: Intent): Boolean =
    runCatching {
        if ((intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) == 0) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }.isSuccess

internal fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

internal fun sanitizedPluginId(value: String): String? {
    val id = value.trim()
    if (id.isEmpty() || id.length > 128) return null
    if (!id.matches(PLUGIN_ID_PATTERN)) return null
    return id
}

private val PLUGIN_ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")
