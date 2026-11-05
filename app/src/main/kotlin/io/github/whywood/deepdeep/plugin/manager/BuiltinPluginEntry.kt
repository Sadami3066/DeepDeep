package io.github.whywood.deepdeep.plugin.manager

import io.github.whywood.deepdeep.plugin.api.DeepDeepPlugin
import io.github.whywood.deepdeep.plugin.api.PluginManifest

internal data class BuiltinPluginEntry(
    val manifest: PluginManifest,
    val plugin: DeepDeepPlugin,
)
