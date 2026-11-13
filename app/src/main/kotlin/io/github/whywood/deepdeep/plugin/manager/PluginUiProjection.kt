package io.github.whywood.deepdeep.plugin.manager

import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.plugin.PluginStrings
import io.github.whywood.deepdeep.plugin.api.MIN_SUPPORTED_API_LEVEL
import io.github.whywood.deepdeep.plugin.host.PluginPhase
import io.github.whywood.deepdeep.plugin.host.PluginRegistrySnapshot
import io.github.whywood.deepdeep.plugin.host.PluginTextResolver
import io.github.whywood.deepdeep.plugin.host.external.HostPublisherInfo

internal class PluginUiProjection(
    private val textResolver: PluginTextResolver,
    private val strings: PluginStrings,
) {
    fun snapshots(
        views: List<PluginRuntimeView>,
        registrySnapshot: PluginRegistrySnapshot,
    ): List<PluginSnapshot> = views.mapNotNull { view ->
        val descriptor = registrySnapshot.plugin(view.id) ?: return@mapNotNull null
        val retiredApiLevel = view.externalRecord?.apiLevel
            ?.takeIf { level -> level < MIN_SUPPORTED_API_LEVEL && view.plugin == null }
        PluginSnapshot(
            id = descriptor.id,
            name = textResolver.resolve(descriptor.manifest.name),
            summary = textResolver.resolve(descriptor.manifest.summary),
            description = descriptor.manifest.description?.let(textResolver::resolve),
            version = descriptor.manifest.version,
            installedVersion = view.record.installedVersion,
            pendingVersion = view.record.pendingVersion,
            previousVersion = view.record.previousVersion,
            icon = descriptor.manifest.icon,
            enabled = view.enabled,
            phase = phaseUi(view, descriptor.phase),
            availability = if (retiredApiLevel != null) {
                PluginAvailabilityUi.Unavailable(apiLevelRetired(retiredApiLevel))
            } else {
                view.availability
            },
            pages = descriptor.contributions.ui.pages.map { page ->
                PluginPageUi(page.id, textResolver.resolve(page.title))
            },
            skills = descriptor.contributions.skills.map { it.id },
            installedSizeBytes = view.externalRecord?.size,
            lastError = retiredApiLevel?.let(::apiLevelRetired) ?: view.record.lastError,
            publisher = view.externalRecord?.let { record ->
                HostPublisherInfo(descriptor.manifest.publisher?.name.orEmpty(), record.publisherKeyId)
            },
            capabilities = descriptor.manifest.capabilities.map { it.wire }.toSet(),
        )
    }

    private fun apiLevelRetired(level: Int): String = strings.get(R.string.plugins_api_level_retired, level)

    private fun phaseUi(view: PluginRuntimeView, phase: PluginPhase): PluginPhaseUi = when {
        !view.enabled -> PluginPhaseUi.DISABLED
        view.record.pendingVersion != null -> PluginPhaseUi.PENDING_RESTART
        else -> phase.toUi()
    }

    private fun PluginPhase.toUi(): PluginPhaseUi = when (this) {
        PluginPhase.INSTALLED -> PluginPhaseUi.INSTALLED
        PluginPhase.ACTIVE -> PluginPhaseUi.ACTIVE
        PluginPhase.DEGRADED -> PluginPhaseUi.DEGRADED
        PluginPhase.FAILED -> PluginPhaseUi.FAILED
        PluginPhase.DISABLED -> PluginPhaseUi.DISABLED
    }
}
