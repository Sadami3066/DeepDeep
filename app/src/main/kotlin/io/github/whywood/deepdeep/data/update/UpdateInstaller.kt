package io.github.whywood.deepdeep.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import io.github.whywood.deepdeep.BuildConfig
import java.io.File

interface UpdateInstaller {
    fun canInstallPackages(): Boolean

    fun openInstallPermissionSettings()

    fun isInstallableArchive(apk: File): Boolean

    fun install(apk: File): Boolean
}

class AndroidUpdateInstaller(private val context: Context) : UpdateInstaller {
    override fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    override fun openInstallPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    override fun isInstallableArchive(apk: File): Boolean {
        val info = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageArchiveInfo(
                    apk.absolutePath,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
            }
        }.getOrNull() ?: return false
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        return UpdatePolicy.acceptsArchive(
            archivePackage = info.packageName,
            archiveVersionCode = versionCode,
            ourPackage = context.packageName,
            installedVersionCode = BuildConfig.VERSION_CODE.toLong(),
        )
    }

    override fun install(apk: File): Boolean = runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }.isSuccess
}
