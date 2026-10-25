package io.github.whywood.deepdeep.service

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri

object KeepAlive {
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun isBackgroundRestricted(context: Context): Boolean =
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isBackgroundRestricted == true

    fun supportsAutostartSettings(context: Context): Boolean =
        isMiui() && activityExists(context, AUTOSTART_COMPONENT)

    fun supportsPowerSettings(context: Context): Boolean =
        isMiui() && activityExists(context, POWER_COMPONENT)

    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context) {
        launch(
            context,
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData("package:${context.packageName}".toUri()),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        )
    }

    fun openAutostartSettings(context: Context) {
        launch(context, Intent().setComponent(AUTOSTART_COMPONENT), appDetails(context))
    }

    fun openPowerSettings(context: Context) {
        val label = context.applicationInfo.loadLabel(context.packageManager)?.toString().orEmpty()
        launch(
            context,
            Intent().setComponent(POWER_COMPONENT)
                .putExtra("package_name", context.packageName)
                .putExtra("package_label", label),
            appDetails(context),
        )
    }

    private fun isMiui(): Boolean {
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val brand = Build.BRAND.orEmpty()
        return manufacturer.equals("Xiaomi", ignoreCase = true) ||
            brand.equals("Xiaomi", ignoreCase = true) ||
            brand.equals("Redmi", ignoreCase = true) ||
            brand.equals("POCO", ignoreCase = true)
    }

    private fun activityExists(context: Context, component: ComponentName): Boolean =
        runCatching {
            context.packageManager.getActivityInfo(component, 0)
            true
        }.getOrDefault(false)

    private fun appDetails(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData("package:${context.packageName}".toUri())

    private fun launch(context: Context, primary: Intent, fallback: Intent) {
        val appContext = context.applicationContext ?: context
        primary.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            appContext.startActivity(primary)
        } catch (_: ActivityNotFoundException) {
            runCatching { appContext.startActivity(fallback) }
        }
    }

    private val AUTOSTART_COMPONENT = ComponentName(
        "com.miui.securitycenter",
        "com.miui.permcenter.autostart.AutoStartManagementActivity",
    )

    private val POWER_COMPONENT = ComponentName(
        "com.miui.powerkeeper",
        "com.miui.powerkeeper.ui.HiddenAppsConfigActivity",
    )
}
