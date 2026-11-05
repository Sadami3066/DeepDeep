package io.github.whywood.deepdeep.plugin.manager

import io.github.whywood.deepdeep.plugin.api.PluginManifest

internal object PluginRules {
    fun installedVersion(manifest: PluginManifest, persistedVersion: String?, persistedInstalled: Boolean?): String? =
        when {
            manifest.installable -> if (persistedInstalled == true) persistedVersion ?: manifest.version else null
            else -> persistedVersion ?: manifest.version
        }

    fun canInstall(manifest: PluginManifest): Boolean = manifest.installable

    fun canUninstall(manifest: PluginManifest): Boolean = manifest.installable

    fun updateAvailable(
        manifestVersion: String,
        installedVersion: String?,
        pendingVersion: String?,
    ): Boolean = pendingVersion == null && installedVersion != null && manifestVersion != installedVersion
}
