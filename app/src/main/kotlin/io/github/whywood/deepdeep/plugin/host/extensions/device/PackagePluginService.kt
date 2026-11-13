package io.github.whywood.deepdeep.plugin.host.extensions.device

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.graphics.drawable.toBitmap
import io.github.whywood.deepdeep.plugin.api.PackageInstallResult
import io.github.whywood.deepdeep.plugin.api.PackageService
import io.github.whywood.deepdeep.plugin.api.PluginPackageInfo
import io.github.whywood.deepdeep.runtime.host.HostAccess
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class PackagePluginService(
    private val deps: DeviceServiceDeps,
    private val privilege: PrivilegeExecutor,
) : PackageService {
    private val context: Context get() = deps.appContext
    private val packageManager: PackageManager get() = context.packageManager

    override suspend fun list(includeSystem: Boolean): List<PluginPackageInfo> = withContext(Dispatchers.IO) {
        installedPackages(includeSystem)
    }

    override suspend fun get(packageName: String): PluginPackageInfo? = withContext(Dispatchers.IO) {
        val name = validPackageName(packageName) ?: return@withContext null
        packageInfoOf(name)?.toPluginPackageInfo()
    }

    override suspend fun install(apkPath: String, silent: Boolean): PackageInstallResult =
        withContext(Dispatchers.IO) {
            val apk = resolveInstallableApk(deps, apkPath)
                ?: return@withContext PackageInstallResult(
                    accepted = false,
                    error = "apk must be inside the plugin data or cache directory",
                )
            if (silent) privilege.installSilently(apk) else performUserInstall(deps, apk)
        }

    override suspend fun uninstall(packageName: String, keepData: Boolean): PackageInstallResult =
        withContext(Dispatchers.IO) {
            val name = validPackageName(packageName)
                ?: return@withContext PackageInstallResult(accepted = false, error = "invalid package name")
            if (deps.hostAccess.value.effective == HostAccess.LOCAL) {
                launchUninstall(name)
            } else {
                privilege.uninstallSilently(name, keepData)
            }
        }

    override fun launch(packageName: String): Boolean {
        val name = validPackageName(packageName) ?: return false
        val intent = runCatching { packageManager.getLaunchIntentForPackage(name) }.getOrNull()
            ?: return openAppInfo(name)
        return startActivitySafely(context, intent)
    }

    override fun openAppInfo(packageName: String): Boolean {
        val name = validPackageName(packageName) ?: return false
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$name"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return startActivitySafely(context, intent)
    }

    override suspend fun icon(packageName: String, sizePx: Int): ByteArray? = withContext(Dispatchers.IO) {
        val name = validPackageName(packageName) ?: return@withContext null
        runCatching {
            val drawable = packageManager.getApplicationIcon(name)
            val size = sizePx.coerceIn(MIN_ICON_SIZE_PX, MAX_ICON_SIZE_PX)
            val bitmap = drawable.toBitmap(size, size, Bitmap.Config.ARGB_8888)
            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
            output.toByteArray()
        }.getOrNull()
    }

    private fun installedPackages(includeSystem: Boolean): List<PluginPackageInfo> {
        val packages = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstalledPackages(0)
            }
        }.getOrDefault(emptyList())
        return packages.asSequence()
            .filter { includeSystem || !isSystemPackage(it) }
            .map { it.toPluginPackageInfo() }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            .toList()
    }

    private fun packageInfoOf(packageName: String): PackageInfo? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
    }.getOrNull()

    private fun PackageInfo.toPluginPackageInfo(): PluginPackageInfo {
        val application = applicationInfo
        val label = application
            ?.let { info -> runCatching { packageManager.getApplicationLabel(info).toString() }.getOrNull() }
            ?.takeIf { it.isNotBlank() }
            ?: packageName
        return PluginPackageInfo(
            packageName = packageName,
            label = label,
            versionName = versionName.orEmpty(),
            versionCode = longVersionCode,
            system = isSystemPackage(this),
            enabled = application?.enabled ?: false,
            installedAtMs = firstInstallTime,
            updatedAtMs = lastUpdateTime,
        )
    }

    private fun isSystemPackage(packageInfo: PackageInfo): Boolean {
        val flags = packageInfo.applicationInfo?.flags ?: return false
        return (flags and ApplicationInfo.FLAG_SYSTEM) != 0
    }

    private fun launchUninstall(packageName: String): PackageInstallResult = runCatching {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }.fold(
        onSuccess = { PackageInstallResult(accepted = true, pendingUserAction = true) },
        onFailure = { error ->
            PackageInstallResult(accepted = false, error = error.message ?: "cannot open uninstaller")
        },
    )

    private companion object {
        const val MIN_ICON_SIZE_PX = 16
        const val MAX_ICON_SIZE_PX = 2048
    }
}

internal fun resolveInstallableApk(deps: DeviceServiceDeps, rawPath: String): File? {
    val requested = rawPath.trim()
    if (requested.isEmpty()) return null
    val canonical = runCatching { File(requested).canonicalFile }.getOrNull() ?: return null
    if (!canonical.isFile) return null
    val allowedRoots = listOf(deps.pluginDataDir, deps.pluginCacheDir)
        .mapNotNull { root -> runCatching { root.canonicalFile }.getOrNull() }
    return canonical.takeIf { candidate ->
        allowedRoots.any { root -> candidate.path.startsWith(root.path + File.separator) }
    }
}

internal suspend fun performUserInstall(deps: DeviceServiceDeps, apk: File): PackageInstallResult =
    withContext(Dispatchers.IO) {
        val installer = deps.updateInstaller
        val allowed = runCatching { installer.canInstallPackages() }.getOrDefault(false)
        if (!allowed) {
            return@withContext PackageInstallResult(
                accepted = false,
                pendingUserAction = true,
                error = "install-permission-required",
            )
        }
        val staged = stageApkForInstall(deps.appContext, apk)
            ?: return@withContext PackageInstallResult(accepted = false, error = "cannot stage apk")
        val accepted = runCatching { installer.install(staged) }.getOrDefault(false)
        if (accepted) {
            PackageInstallResult(accepted = true, pendingUserAction = true)
        } else {
            PackageInstallResult(accepted = false, error = "installer-intent-failed")
        }
    }

internal fun stageApkForInstall(context: Context, apk: File): File? = runCatching {
    val pluginsRoot = File(context.filesDir, "plugins").canonicalFile
    if (apk.canonicalPath.startsWith(pluginsRoot.path + File.separator)) return apk
    val directory = File(File(context.cacheDir, "handoff"), "plugin-install").apply { mkdirs() }
    val safeName = apk.name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "plugin.apk" }
    val target = File(directory, "$safeName")
    apk.copyTo(target, overwrite = true)
    target
}.getOrNull()
