package io.github.whywood.deepdeep.plugin.host

import java.io.File

internal object PluginPaths {
    const val PLUGINS_DIR: String = "plugins"

    const val BUNDLE_DIR: String = "bundle"

    const val ASSETS_DIR: String = "assets"

    const val LIB_DIR: String = "lib"

    const val DATA_DIR: String = "data"

    fun pluginsRoot(filesDir: File): File = File(filesDir, PLUGINS_DIR)

    fun pluginRoot(pluginsRoot: File, pluginId: String): File = File(pluginsRoot, pluginId)

    fun versionRoot(pluginsRoot: File, pluginId: String, version: String): File =
        File(pluginRoot(pluginsRoot, pluginId), version)

    fun bundleDir(pluginsRoot: File, pluginId: String, version: String): File =
        File(versionRoot(pluginsRoot, pluginId, version), BUNDLE_DIR)

    fun assetsDir(pluginsRoot: File, pluginId: String, version: String): File =
        File(versionRoot(pluginsRoot, pluginId, version), ASSETS_DIR)

    fun libDir(pluginsRoot: File, pluginId: String, version: String): File =
        File(versionRoot(pluginsRoot, pluginId, version), LIB_DIR)

    fun dataDir(pluginsRoot: File, pluginId: String): File = File(pluginRoot(pluginsRoot, pluginId), DATA_DIR)
}
