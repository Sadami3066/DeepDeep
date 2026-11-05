package io.github.whywood.deepdeep.plugin.host

import android.content.Context
import io.github.whywood.deepdeep.plugin.api.PluginHost
import io.github.whywood.deepdeep.plugin.api.PluginManifest
import java.io.File
import kotlinx.coroutines.CoroutineScope

internal fun builtinPluginHost(
    manifest: PluginManifest,
    context: Context,
    filesDir: File,
    scope: CoroutineScope,
    language: () -> String,
    services: PluginServices = PluginServices.None,
): PluginHost = PluginHostImpl(
    manifest = manifest,
    context = context,
    scope = scope,
    services = services,
    textResolver = PluginTextResolver(
        resourceString = { id -> context.getString(id) },
        language = language,
    ),
    pluginsRoot = PluginPaths.pluginsRoot(filesDir),
)
