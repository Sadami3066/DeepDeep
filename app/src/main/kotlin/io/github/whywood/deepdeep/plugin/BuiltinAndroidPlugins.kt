package io.github.whywood.deepdeep.plugin

import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.plugin.api.IconRef
import io.github.whywood.deepdeep.plugin.api.LocalizedText
import io.github.whywood.deepdeep.plugin.api.PluginManifest
import io.github.whywood.deepdeep.plugin.api.PluginSource
import io.github.whywood.deepdeep.plugin.api.Provides
import io.github.whywood.deepdeep.plugin.manager.BuiltinPluginEntry
import io.github.whywood.deepdeep.plugin.micloud.MiCloudPlugin
import io.github.whywood.deepdeep.plugin.micloud.MiCloudSettingKeys

internal fun androidMiCloud(wiring: BuiltinPluginWiring): BuiltinPluginEntry = BuiltinPluginEntry(
    manifest = androidMiCloudManifest(),
    plugin = MiCloudPlugin(
        context = wiring.uiContext,
        onCapabilitiesChanged = { wiring.onCapabilitiesChanged() },
        onSessionInvalidated = { wiring.onSessionInvalidated() },
    ),
)

internal fun androidMiCloudManifest(): PluginManifest = PluginManifest(
    id = "micloud",
    version = "1.0.0",
    name = LocalizedText.Resource(R.string.plugin_micloud_name),
    summary = LocalizedText.Resource(R.string.plugin_micloud_summary),
    sizeLabel = LocalizedText.Resource(R.string.plugin_micloud_size),
    icon = IconRef.Resource(DdIcons.Cloud.res),
    source = PluginSource.EXTERNAL,
    installable = false,
    provides = Provides(
        tools = MICLOUD_TOOLS,
        skills = listOf("deepdeep-micloud"),
        settings = MiCloudSettingKeys.ALL,
    ),
)

private val MICLOUD_TOOLS = listOf(
    "mi_cloud_account",
    "mi_cloud_notes",
    "mi_cloud_todo",
    "mi_cloud_sms",
    "mi_cloud_contacts",
    "mi_cloud_gallery",
    "mi_cloud_recordings",
)
