package io.github.whywood.deepdeep.screen

import android.content.Context
import android.content.Intent
import android.app.ActivityOptions

class AndroidScreenAppCatalog(private val context: Context) : ScreenAppCatalog {
    override fun app(packageName: String): ScreenApp {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: error("找不到可启动的应用：$packageName")
        val component = checkNotNull(intent.component) { "无法解析应用入口" }
        return ScreenApp(packageName, packageName, component.className)
    }

    override fun launchOnMain(packageName: String): ScreenApp {
        val app = app(packageName)
        val intent = Intent().setClassName(app.packageName, app.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent, ActivityOptions.makeBasic().setLaunchDisplayId(0).toBundle())
        return app
    }

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

    private companion object {
        const val MAX_LISTED_APPS = 300
    }
}
