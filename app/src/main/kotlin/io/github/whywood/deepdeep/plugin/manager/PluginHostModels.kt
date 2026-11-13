package io.github.whywood.deepdeep.plugin.manager

import io.github.whywood.deepdeep.plugin.api.DeepDeepPlugin
import io.github.whywood.deepdeep.plugin.api.PluginHost
import io.github.whywood.deepdeep.plugin.api.PluginManifest
import io.github.whywood.deepdeep.plugin.host.PluginContributions
import io.github.whywood.deepdeep.plugin.host.PluginDescriptor
import io.github.whywood.deepdeep.plugin.host.PluginPhase
import io.github.whywood.deepdeep.plugin.host.PluginRecord
import io.github.whywood.deepdeep.plugin.host.PluginRuntimeFacts
import io.github.whywood.deepdeep.plugin.host.external.ExternalPluginRecord
import io.github.whywood.deepdeep.plugin.host.external.LoadedPluginHandle
import io.github.whywood.deepdeep.theme.PluginThemeSource
import kotlinx.coroutines.CoroutineScope

internal class ManagedPlugin(
    val id: String,
    var manifest: PluginManifest,
    var plugin: DeepDeepPlugin?,
    var host: PluginHost,
    var scope: CoroutineScope,
    var externalHandle: LoadedPluginHandle? = null,
    var externalRecord: ExternalPluginRecord? = null,
    var contributions: PluginContributions = PluginContributions.Empty,
    var availability: PluginAvailabilityUi = PluginAvailabilityUi.Available,
    var skillIds: List<String> = emptyList(),
    var failure: String? = null,
    var materializationFailure: String? = null,
    var materialized: Boolean = false,
    var themeSource: PluginThemeSource? = null,
) {
    fun view(record: PluginRecord): PluginRuntimeView = PluginRuntimeView(
        record = record,
        manifest = manifest,
        plugin = plugin,
        host = host,
        scope = scope,
        externalHandle = externalHandle,
        externalRecord = externalRecord,
        contributions = contributions,
        availability = availability,
        skillIds = skillIds,
        failure = failure,
        materializationFailure = materializationFailure,
        materialized = materialized,
        themeSource = themeSource,
    )
}

internal data class PluginRuntimeView(
    val record: PluginRecord,
    val manifest: PluginManifest,
    val plugin: DeepDeepPlugin?,
    val host: PluginHost,
    val scope: CoroutineScope,
    val externalHandle: LoadedPluginHandle?,
    val externalRecord: ExternalPluginRecord?,
    val contributions: PluginContributions,
    val availability: PluginAvailabilityUi,
    val skillIds: List<String>,
    val failure: String?,
    val materializationFailure: String?,
    val materialized: Boolean,
    val themeSource: PluginThemeSource?,
) {
    val id: String get() = record.id

    val enabled: Boolean get() = record.enabled

    val installed: Boolean get() = record.installedVersion != null

    val facts: PluginRuntimeFacts
        get() = PluginRuntimeFacts(
            loaded = plugin != null,
            failed = failure ?: materializationFailure,
            degraded = (availability as? PluginAvailabilityUi.Unavailable)?.reason?.takeIf { it.isNotBlank() },
        )

    val phase: PluginPhase get() = PluginPhase.derive(record, facts)
}

internal fun PluginRuntimeView.toDescriptor(): PluginDescriptor = PluginDescriptor(
    manifest = manifest,
    phase = phase,
    error = record.lastError,
    contributions = contributions,
    themeSource = themeSource,
)
