package io.github.whywood.deepdeep.plugin.host.external

import io.github.whywood.deepdeep.plugin.api.PluginSource
import io.github.whywood.deepdeep.plugin.host.PluginPhase

internal data class ExternalPluginRecord(
    val id: String,
    val version: String,
    val installedAt: Long,
    val enabled: Boolean = true,
    val skillIds: List<String> = emptyList(),
    val source: PluginSource = PluginSource.EXTERNAL,
    val installPath: String,
    val activeBundleSha256: String,
    val size: Long,
    val publisherKeyId: String,
    val apiLevel: Int,
    val uiApiLevel: Int,
    val phase: PluginPhase = PluginPhase.INSTALLED,
    val lastError: String? = null,
    val pendingVersion: String? = null,
    val pendingBundleSha256: String? = null,
    val previousVersion: String? = null,
    val previousBundleSha256: String? = null,
) {
    val hasPendingUpdate: Boolean get() = pendingVersion != null
}
