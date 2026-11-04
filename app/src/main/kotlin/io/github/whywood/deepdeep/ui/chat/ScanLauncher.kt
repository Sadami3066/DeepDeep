package io.github.whywood.deepdeep.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.Toast
import io.github.whywood.deepdeep.R

internal class ScanAppInfo(val label: String, val icon: Drawable?)

internal fun scanAppInfo(context: Context, target: ScanTarget): ScanAppInfo {
    val packageManager = context.packageManager
    val appInfo = runCatching { packageManager.getApplicationInfo(target.packageName, 0) }.getOrNull()
        ?: return ScanAppInfo(target.packageName, null)
    return ScanAppInfo(
        label = runCatching {
            packageManager.getApplicationLabel(appInfo).toString()
        }.getOrDefault(target.packageName),
        icon = runCatching { packageManager.getApplicationIcon(appInfo) }.getOrNull(),
    )
}

internal object ScanLauncher {
    fun launch(context: Context, decision: ScanDecision) {
        val opened = when (val primary = decision.primary) {
            is ScanPrimary.AppScan ->
                startActivity(context, scanIntent(primary.target)) ||
                    startActivity(context, mainIntent(context, primary.target.packageName)) ||
                    startActivity(context, browseIntent(decision.raw))

            is ScanPrimary.Browse -> startActivity(context, browseIntent(primary.uri))
            ScanPrimary.Copy -> false
        }
        if (!opened) copy(context, decision.raw)
    }

    private fun scanIntent(target: ScanTarget): Intent {
        val intent = when (val launch = target.launch) {
            is ScanLaunch.UriLaunch -> Intent(Intent.ACTION_VIEW, Uri.parse(launch.uri))
            is ScanLaunch.Shortcut -> Intent(launch.action)
                .setComponent(ComponentName(target.packageName, launch.activityName))
                .putExtra(launch.extraKey, launch.extraValue)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun browseIntent(raw: String): Intent? {
        val lowered = raw.trim().lowercase()
        if (!lowered.startsWith("http://") && !lowered.startsWith("https://")) return null
        return Intent(Intent.ACTION_VIEW, Uri.parse(raw.trim()))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun mainIntent(context: Context, packageName: String): Intent? =
        context.packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun startActivity(context: Context, intent: Intent?): Boolean {
        if (intent == null) return false
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    private fun copy(context: Context, raw: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard != null) {
            clipboard.setPrimaryClip(
                ClipData.newPlainText(context.getString(R.string.scan_action_copy), raw),
            )
        }
        Toast.makeText(context, R.string.scan_copied, Toast.LENGTH_SHORT).show()
    }
}
