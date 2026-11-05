package io.github.whywood.deepdeep.plugin.manager

import io.github.whywood.deepdeep.plugin.api.PluginManifest
import io.github.whywood.deepdeep.plugin.host.PluginPhase

internal object PluginRules {
    fun installedVersion(manifest: PluginManifest, persistedVersion: String?, persistedInstalled: Boolean?): String? =
        when {
            manifest.installable -> if (persistedInstalled == true) persistedVersion ?: manifest.version else null
            else -> persistedVersion ?: manifest.version
        }

    fun isActive(
        enabled: Boolean,
        installedVersion: String?,
        availability: PluginAvailabilityUi,
        phase: PluginPhase,
    ): Boolean = enabled && installedVersion != null && availability is PluginAvailabilityUi.Available && phase.contributes

    fun canInstall(manifest: PluginManifest): Boolean = manifest.installable

    fun canUninstall(manifest: PluginManifest): Boolean = manifest.installable

    fun updateAvailable(
        manifestVersion: String,
        installedVersion: String?,
        pendingVersion: String?,
    ): Boolean = pendingVersion == null && installedVersion != null && manifestVersion != installedVersion

    fun phase(
        enabled: Boolean,
        installedVersion: String?,
        installable: Boolean,
        availability: PluginAvailabilityUi,
        status: PluginStatusUi?,
    ): PluginPhase = when {
        !enabled -> PluginPhase.DISABLED
        installedVersion == null && installable -> PluginPhase.INSTALLED
        availability is PluginAvailabilityUi.Unavailable -> PluginPhase.DEGRADED
        status is PluginStatusUi.Failed -> PluginPhase.FAILED
        else -> PluginPhase.ACTIVE
    }
}
