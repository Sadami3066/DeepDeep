package io.github.whywood.deepdeep.plugin.manager

import io.github.whywood.deepdeep.plugin.api.DeepDeepPlugin
import io.github.whywood.deepdeep.plugin.api.PluginAvailability
import io.github.whywood.deepdeep.plugin.api.PluginHost
import io.github.whywood.deepdeep.plugin.api.PluginManifest
import io.github.whywood.deepdeep.plugin.api.PluginSource
import io.github.whywood.deepdeep.plugin.api.PluginStatus
import io.github.whywood.deepdeep.plugin.host.PluginContributions
import io.github.whywood.deepdeep.plugin.host.PluginDescriptor
import io.github.whywood.deepdeep.plugin.host.PluginPhase
import io.github.whywood.deepdeep.plugin.host.external.ExternalPluginRecord

internal data class ManagedPlugin(
    var manifest: PluginManifest,
    var plugin: DeepDeepPlugin?,
    val builtin: Boolean,
    var source: PluginSource,
    var installedVersion: String?,
    var enabled: Boolean,
    var phase: PluginPhase,
    var error: String? = null,
    var availability: PluginAvailabilityUi = PluginAvailabilityUi.Available,
    var status: PluginStatusUi? = null,
    var contributions: PluginContributions = PluginContributions.Empty,
    var externalRecord: ExternalPluginRecord? = null,
    var skillIds: List<String> = emptyList(),
) {
    lateinit var host: PluginHost
}

internal object PlaceholderPlugin : DeepDeepPlugin {
    override suspend fun availability(host: PluginHost): PluginAvailability = PluginAvailability.Unavailable("")
    override suspend fun status(host: PluginHost): PluginStatus = PluginStatus.Failed("")
}

internal fun ManagedPlugin.toDescriptor(): PluginDescriptor = PluginDescriptor(
    manifest = manifest,
    plugin = plugin ?: PlaceholderPlugin,
    source = source,
    phase = phase,
    error = error,
    contributions = contributions,
)
