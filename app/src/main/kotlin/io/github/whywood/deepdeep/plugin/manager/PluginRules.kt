package io.github.whywood.deepdeep.plugin.manager

import io.github.whywood.deepdeep.plugin.api.PluginManifest
import io.github.whywood.deepdeep.plugin.host.PluginPhase
import io.github.whywood.deepdeep.plugin.host.external.ExternalPluginRecord

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

    fun updateAvailable(record: ExternalPluginRecord?, installedVersion: String?): Boolean {
        val pending = record?.pendingVersion ?: return false
        return pending != installedVersion
    }

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
