package io.github.whywood.deepdeep.plugin.host.extensions.device

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import io.github.whywood.deepdeep.plugin.api.IntentsService
import io.github.whywood.deepdeep.plugin.api.PluginActivityResult
import io.github.whywood.deepdeep.plugin.api.ResolvedIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class IntentsPluginService(private val deps: DeviceServiceDeps) : IntentsService {
    private val context: Context get() = deps.appContext
    private val packageManager: PackageManager get() = context.packageManager

    override fun startActivity(intent: Intent): Boolean = startActivitySafely(context, intent)

    override suspend fun startActivityForResult(
        intent: Intent,
        timeoutMs: Long,
    ): PluginActivityResult = runCatching {
        deps.interaction.startActivityForResult(intent, timeoutMs.coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS))
    }.getOrElse {
        PluginActivityResult(resultCode = Activity.RESULT_CANCELED, data = null, cancelled = true)
    }

    override fun sendBroadcast(intent: Intent): Boolean =
        runCatching { context.sendBroadcast(intent) }.isSuccess

    override fun startService(intent: Intent): Boolean = runCatching {
        context.startService(intent)
        true
    }.getOrDefault(false)

    override fun stopService(intent: Intent): Boolean =
        runCatching { context.stopService(intent) }.getOrDefault(false)

    override fun canResolve(intent: Intent): Boolean = resolveActivity(intent) != null

    override suspend fun resolve(intent: Intent): ResolvedIntent? = withContext(Dispatchers.IO) {
        resolveActivity(intent)?.toResolvedIntent()
    }

    override suspend fun queryActivities(intent: Intent): List<ResolvedIntent> = withContext(Dispatchers.IO) {
        queryResolvedActivities(intent).map { it.toResolvedIntent() }
    }

    private fun resolveActivity(intent: Intent): ResolveInfo? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.resolveActivity(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
    }.getOrNull()

    private fun queryResolvedActivities(intent: Intent): List<ResolveInfo> = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
    }.getOrDefault(emptyList())

    private fun ResolveInfo.toResolvedIntent(): ResolvedIntent {
        val info = activityInfo
        val label = runCatching { loadLabel(packageManager).toString() }.getOrNull()
            ?.takeIf { it.isNotBlank() }
        return ResolvedIntent(
            packageName = info?.packageName ?: resolvePackageName.orEmpty(),
            className = info?.name,
            label = label,
            exported = info?.exported ?: false,
        )
    }

    private companion object {
        const val MIN_TIMEOUT_MS = 1_000L
        const val MAX_TIMEOUT_MS = 10 * 60_000L
    }
}
