package io.github.whywood.deepdeep.plugin.host.extensions.device

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.Process
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.whywood.deepdeep.plugin.api.PermissionService
import io.github.whywood.deepdeep.plugin.api.PluginAccessState
import io.github.whywood.deepdeep.plugin.api.PluginSpecialAccess
import io.github.whywood.deepdeep.service.GuiAccessibilityState

internal class PermissionPluginService(private val deps: DeviceServiceDeps) : PermissionService {
    private val context: Context get() = deps.appContext

    override fun granted(permission: String): Boolean {
        val requested = permission.trim()
        if (requested.isEmpty()) return false
        val androidPermission = PluginPermissionMapping.androidPermission(requested)
        if (androidPermission != null) {
            return runtimeGranted(androidPermission)
        }
        val access = PluginPermissionMapping.specialAccess(requested) ?: return false
        return special(access).granted
    }

    override suspend fun request(vararg permissions: String): Map<String, Boolean> {
        val requested = permissions.toList().map { it.trim() }.filter { it.isNotEmpty() }
        if (requested.isEmpty()) return emptyMap()
        val androidPermissions = requested.mapNotNull(PluginPermissionMapping::androidPermission).distinct()
        val result = if (androidPermissions.isEmpty()) {
            emptyMap()
        } else {
            runCatching { deps.interaction.requestPermissions(androidPermissions.toTypedArray()) }
                .getOrDefault(emptyMap())
        }
        return requested.associateWith { requestedValue ->
            val androidPermission = PluginPermissionMapping.androidPermission(requestedValue)
            when {
                androidPermission != null -> result[androidPermission] ?: runtimeGranted(androidPermission)

                else -> PluginPermissionMapping.specialAccess(requestedValue)?.let { special(it).granted } ?: false
            }
        }
    }

    override fun special(access: PluginSpecialAccess): PluginAccessState = when (access.wire) {
        PluginSpecialAccess.NOTIFICATION_LISTENER.wire -> PluginAccessState(
            granted = notificationListenerGranted(),
            available = true,
        )

        PluginSpecialAccess.ACCESSIBILITY.wire -> PluginAccessState(
            granted = accessibilityGranted(),
            available = true,
        )

        PluginSpecialAccess.OVERLAY.wire -> PluginAccessState(
            granted = overlayGranted(),
            available = true,
        )

        PluginSpecialAccess.USAGE_STATS.wire -> PluginAccessState(
            granted = usageStatsGranted(),
            available = true,
        )

        PluginSpecialAccess.BATTERY_OPTIMIZATION.wire -> PluginAccessState(
            granted = batteryOptimizationIgnored(),
            available = true,
        )

        PluginSpecialAccess.INSTALL_PACKAGES.wire -> PluginAccessState(
            granted = installPackagesGranted(),
            available = true,
        )

        PluginSpecialAccess.MANAGE_STORAGE.wire -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            PluginAccessState(granted = manageStorageGranted(), available = true)
        } else {
            PluginAccessState(granted = false, available = false, reason = "requires-android-11")
        }

        else -> PluginAccessState(granted = false, available = false, reason = "unknown-access")
    }

    override fun openSettings(access: PluginSpecialAccess): Boolean {
        val screen = settingsScreenOf(access) ?: return false
        return openSettingsScreen(deps.context, screen)
    }

    private fun runtimeGranted(androidPermission: String): Boolean {
        if (androidPermission == Manifest.permission.POST_NOTIFICATIONS &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        ) {
            return runCatching { NotificationManagerCompat.from(context).areNotificationsEnabled() }
                .getOrDefault(false)
        }
        return ContextCompat.checkSelfPermission(context, androidPermission) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun notificationListenerGranted(): Boolean = runCatching {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }.getOrDefault(false)

    private fun accessibilityGranted(): Boolean =
        runCatching { GuiAccessibilityState.isEnabled(context) }.getOrDefault(false)

    private fun overlayGranted(): Boolean = runCatching {
        android.provider.Settings.canDrawOverlays(context)
    }.getOrDefault(false)

    private fun usageStatsGranted(): Boolean = runCatching {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    private fun batteryOptimizationIgnored(): Boolean = runCatching {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }.getOrDefault(false)

    private fun installPackagesGranted(): Boolean = runCatching {
        context.packageManager.canRequestPackageInstalls()
    }.getOrDefault(false)

    private fun manageStorageGranted(): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else false
    }.getOrDefault(false)
}
