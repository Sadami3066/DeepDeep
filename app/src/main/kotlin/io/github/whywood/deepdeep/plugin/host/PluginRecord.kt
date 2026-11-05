package io.github.whywood.deepdeep.plugin.host

internal data class PluginRecord(
    val id: String,
    val enabled: Boolean,
    val installedVersion: String?,
    val pendingVersion: String? = null,
    val previousVersion: String? = null,
    val lastError: String? = null,
)

internal data class PluginRuntimeFacts(
    val loaded: Boolean,
    val installable: Boolean,
    val failed: String? = null,
    val degraded: String? = null,
)
