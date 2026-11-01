package io.github.whywood.deepdeep.screen

import android.content.Context
import android.content.Intent

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

    private companion object {
        const val MAX_LISTED_APPS = 300
    }
}
