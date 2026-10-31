package io.github.whywood.deepdeep.screen

import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.WindowManager

class AndroidScreenAppCatalog(private val context: Context) : ScreenAppCatalog {
    override fun launchableApps(): List<ScreenApp> {
        val packageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = runCatching { packageManager.queryIntentActivities(intent, 0) }.getOrDefault(emptyList())
        return resolved.asSequence()
            .mapNotNull { info ->
                val activityInfo = info.activityInfo ?: return@mapNotNull null
                val packageName = activityInfo.packageName ?: return@mapNotNull null
                val label = runCatching { info.loadLabel(packageManager)?.toString() }.getOrNull()
                ScreenApp(
                    packageName = packageName,
                    label = label?.takeIf { it.isNotBlank() } ?: packageName,
                    component = activityInfo.name.orEmpty(),
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .take(MAX_LISTED_APPS)
            .toList()
    }

    override fun launchOnMain(packageName: String): ScreenApp {
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?: throw IllegalArgumentException("找不到可启动的应用：$packageName")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val component = intent.component?.flattenToShortString().orEmpty()
        val label = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        }.getOrNull() ?: packageName
        runCatching { context.startActivity(intent) }.getOrElse { failure ->
            throw IllegalStateException("启动主屏应用失败：${failure.message ?: packageName}", failure)
        }
        return ScreenApp(packageName = packageName, label = label, component = component)
    }

    private companion object {
        const val MAX_LISTED_APPS = 300
    }
}

internal fun mainScreenSize(context: Context): Pair<Int, Int> {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val bounds = windowManager?.let { manager ->
            runCatching { manager.currentWindowMetrics.bounds }.getOrNull()
        }
        if (bounds != null && bounds.width() > 0 && bounds.height() > 0) {
            return bounds.width() to bounds.height()
        }
    }
    val metrics = context.resources.displayMetrics
    return metrics.widthPixels to metrics.heightPixels
}
