package io.github.whywood.deepdeep.plugin.host

import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.plugin.api.IconRef
import io.github.whywood.deepdeep.plugin.api.LocalizedText
import io.github.whywood.deepdeep.plugin.api.PluginManifest
import io.github.whywood.deepdeep.plugin.api.PluginSource
import io.github.whywood.deepdeep.plugin.api.Provides

internal fun builtinPluginManifest(
    id: String,
    version: String,
    nameRes: Int,
    summaryRes: Int,
    iconRes: Int? = null,
    sizeLabelRes: Int = R.string.plugins_size_builtin,
    requiresPrivilege: Boolean = false,
    defaultEnabled: Boolean = true,
    order: Int = 0,
    tools: List<String> = emptyList(),
    skills: List<String> = emptyList(),
    settings: List<String> = emptyList(),
    events: List<String> = emptyList(),
    uiSlots: List<String> = emptyList(),
): PluginManifest = PluginManifest(
    id = id,
    version = version,
    name = LocalizedText.Resource(nameRes),
    summary = LocalizedText.Resource(summaryRes),
    sizeLabel = LocalizedText.Resource(sizeLabelRes),
    icon = iconRes?.let(IconRef::Resource),
    source = PluginSource.BUILTIN,
    requiresPrivilege = requiresPrivilege,
    defaultEnabled = defaultEnabled,
    order = order,
    provides = Provides(
        tools = tools,
        skills = skills,
        settings = settings,
        events = events,
        uiSlots = uiSlots,
    ),
)
