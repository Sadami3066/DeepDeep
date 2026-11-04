package io.github.whywood.deepdeep.ui.common.scan

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

internal fun scanAppInfo(context: Context, packageName: String): ScanAppInfo {
    val packageManager = context.packageManager
    val appInfo = runCatching { packageManager.getApplicationInfo(packageName, 0) }.getOrNull()
        ?: return ScanAppInfo(packageName, null)
    return ScanAppInfo(
        label = runCatching {
            packageManager.getApplicationLabel(appInfo).toString()
        }.getOrDefault(packageName),
        icon = runCatching { packageManager.getApplicationIcon(appInfo) }.getOrNull(),
    )
}

internal object ScanLauncher {
    fun launch(context: Context, decision: ScanDecision): Boolean {
        val opened = when (val primary = decision.primary) {
            is ScanPrimary.AppScan -> openStrategy(context, primary.strategy) ||
                (ScanMatcher.isHttp(decision.raw) && openUri(context, decision.raw))

            is ScanPrimary.Browse -> openUri(context, primary.uri)
            ScanPrimary.Copy -> false
        }
        if (!opened) copy(context, decision.raw)
        return opened
    }

    fun launchStrategy(context: Context, strategy: ScanStrategy): Boolean {
        val opened = openStrategy(context, strategy)
        if (!opened) Toast.makeText(context, R.string.scan_open_failed, Toast.LENGTH_SHORT).show()
        return opened
    }

    fun copy(context: Context, raw: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard != null) {
            clipboard.setPrimaryClip(
                ClipData.newPlainText(context.getString(R.string.scan_action_copy), raw),
            )
        }
        Toast.makeText(context, R.string.scan_copied, Toast.LENGTH_SHORT).show()
    }

    private fun openStrategy(context: Context, strategy: ScanStrategy): Boolean =
        start(context, scanIntent(strategy)) || start(context, mainIntent(context, strategy.packageName))

    private fun scanIntent(strategy: ScanStrategy): Intent {
        val intent = when (val launch = strategy.launch) {
            is ScanLaunch.Uri -> Intent(Intent.ACTION_VIEW, Uri.parse(launch.uri))
            is ScanLaunch.Component -> {
                val className = if (launch.activity.startsWith(".")) {
                    strategy.packageName + launch.activity
                } else {
                    launch.activity
                }
                Intent().apply {
                    component = ComponentName(strategy.packageName, className)
                    action = launch.action
                    launch.extras.forEach { (key, value) ->
                        when (value) {
                            is ScanExtra.Text -> putExtra(key, value.value)
                            is ScanExtra.Flag -> putExtra(key, value.value)
                            is ScanExtra.Number -> putExtra(key, value.value)
                        }
                    }
                    flagNames(launch.flags).forEach { addFlags(it) }
                }
            }
        }
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun mainIntent(context: Context, packageName: String): Intent? =
        context.packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun openUri(context: Context, raw: String): Boolean {
        val uri = runCatching { Uri.parse(raw.trim()) }.getOrNull() ?: return false
        if (uri.scheme.isNullOrBlank()) return false
        return start(
            context,
            Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun start(context: Context, intent: Intent?): Boolean {
        if (intent == null) return false
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    private fun flagNames(names: List<String>): List<Int> = names.mapNotNull {
        when (it.trim().uppercase()) {
            "NEW_TASK" -> Intent.FLAG_ACTIVITY_NEW_TASK
            "CLEAR_TOP" -> Intent.FLAG_ACTIVITY_CLEAR_TOP
            "CLEAR_TASK" -> Intent.FLAG_ACTIVITY_CLEAR_TASK
            "SINGLE_TOP" -> Intent.FLAG_ACTIVITY_SINGLE_TOP
            "NO_ANIMATION" -> Intent.FLAG_ACTIVITY_NO_ANIMATION
            else -> null
        }
    }
}
