package io.github.whywood.deepdeep.plugin.host.external

import io.github.whywood.deepdeep.plugin.api.DEFAULT_LANGUAGE
import io.github.whywood.deepdeep.plugin.api.IconRef
import io.github.whywood.deepdeep.plugin.api.LocalizedText
import io.github.whywood.deepdeep.plugin.api.PluginCapability
import io.github.whywood.deepdeep.plugin.api.PluginManifest
import io.github.whywood.deepdeep.plugin.api.Provides
import io.github.whywood.deepdeep.plugin.api.PublisherInfo
import io.github.whywood.deepdeep.plugin.api.UiManifest

internal data class HostLocalizedText(
    override val values: Map<String, String>,
) : LocalizedText

internal fun hostLocalizedText(default: String): LocalizedText = HostLocalizedText(mapOf(DEFAULT_LANGUAGE to default))

internal fun hostLocalizedText(values: Map<String, String>): LocalizedText = HostLocalizedText(values)

internal data class HostPublisherInfo(
    override val name: String,
    override val keyId: String,
) : PublisherInfo

internal data class HostUiManifest(
    override val apiLevel: Int = io.github.whywood.deepdeep.plugin.api.HOST_UI_API_LEVEL,
    override val kotlin: String? = null,
    override val compose: String? = null,
) : UiManifest

internal data class HostProvides(
    override val tools: List<String> = emptyList(),
    override val skills: List<String> = emptyList(),
    override val uiSlots: List<String> = emptyList(),
    override val pages: List<String> = emptyList(),
) : Provides

internal data class HostPluginManifest(
    override val id: String,
    override val version: String,
    override val apiLevel: Int = io.github.whywood.deepdeep.plugin.api.PLUGIN_API_LEVEL,
    override val minHostVersion: String? = null,
    override val ui: UiManifest? = null,
    override val name: LocalizedText,
    override val summary: LocalizedText,
    override val description: LocalizedText? = null,
    override val homepage: String? = null,
    override val changelog: String? = null,
    override val icon: IconRef? = null,
    override val entryClass: String? = null,
    override val publisher: PublisherInfo? = null,
    override val provides: Provides = HostProvides(),
    override val capabilities: Set<PluginCapability> = emptySet(),
    override val defaultEnabled: Boolean = true,
) : PluginManifest
