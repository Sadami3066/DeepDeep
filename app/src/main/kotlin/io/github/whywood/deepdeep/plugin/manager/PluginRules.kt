package io.github.whywood.deepdeep.plugin.manager

internal object PluginRules {
    fun updateAvailable(
        manifestVersion: String,
        installedVersion: String?,
        pendingVersion: String?,
    ): Boolean = pendingVersion == null && installedVersion != null && manifestVersion != installedVersion
}
