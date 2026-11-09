package io.github.whywood.deepdeep.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import io.github.whywood.deepdeep.R

internal fun calendarAccessGranted(context: Context): Boolean =
    permissionGranted(context, Manifest.permission.READ_CALENDAR) &&
        permissionGranted(context, Manifest.permission.WRITE_CALENDAR)

internal fun storageAccessGranted(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        permissionGranted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
    }

internal fun cameraAccessGranted(context: Context): Boolean =
    permissionGranted(context, Manifest.permission.CAMERA)

internal fun microphoneAccessGranted(context: Context): Boolean =
    permissionGranted(context, Manifest.permission.RECORD_AUDIO)

internal fun assistantShortcutPinned(context: Context): Boolean =
    ShortcutManagerCompat.getShortcuts(
        context,
        ShortcutManagerCompat.FLAG_MATCH_PINNED,
    ).any { it.id == ASSISTANT_SHORTCUT_ID }

internal fun pinAssistantShortcut(context: Context): Boolean {
    if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return false
    val icon = runCatching {
        context.packageManager.getApplicationIcon(context.packageName).toBitmap()
    }.getOrNull() ?: return false
    val shortcut = ShortcutInfoCompat.Builder(context, ASSISTANT_SHORTCUT_ID)
        .setShortLabel(context.getString(R.string.app_name))
        .setLongLabel(context.getString(R.string.app_name))
        .setIcon(IconCompat.createWithBitmap(icon))
        .setIntent(launchIntent)
        .build()
    ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    return true
}

internal fun batteryOptimizationIgnored(context: Context): Boolean {
    val power = context.getSystemService(PowerManager::class.java) ?: return true
    return power.isIgnoringBatteryOptimizations(context.packageName)
}

internal fun openAllFilesSettings(context: Context) {
    val appIntent = Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(appIntent)
    } catch (_: Exception) {
        val fallback = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(fallback) }
    }
}

internal fun openBatteryOptimizationSettings(context: Context) {
    val appIntent = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(appIntent)
    } catch (_: Exception) {
        val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(fallback) }
    }
}

private fun permissionGranted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

internal const val ASSISTANT_SHORTCUT_ID = "deepdeep_assistant"
