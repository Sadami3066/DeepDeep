package io.github.whywood.deepdeep.plugin.host

import android.content.Context
import dalvik.system.InMemoryDexClassLoader
import io.github.whywood.deepdeep.plugin.api.DeepDeepPlugin
import io.github.whywood.deepdeep.plugin.api.PluginManifest
import io.github.whywood.deepdeep.plugin.api.PluginManifestCodec
import io.github.whywood.deepdeep.plugin.api.PluginSource
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.ZipFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class ExternalPluginLoader(private val context: Context) {
    data class LoadedPlugin(
        val manifest: PluginManifest,
        val plugin: DeepDeepPlugin,
        val classLoader: ClassLoader,
    )

    fun load(pluginFile: File): LoadedPlugin {
        val manifest = readManifest(pluginFile)
        val entryClass = requireNotNull(manifest.entryClass) { "plugin.json is missing entryClass" }
        val classLoader = InMemoryDexClassLoader(readDexBuffers(pluginFile), null, context.classLoader)
        val loaded = Class.forName(entryClass, true, classLoader)
        require(DeepDeepPlugin::class.java.isAssignableFrom(loaded)) {
            "$entryClass does not implement DeepDeepPlugin"
        }
        val plugin = loaded.getDeclaredConstructor().newInstance() as DeepDeepPlugin
        return LoadedPlugin(manifest, plugin, classLoader)
    }

    fun readManifest(pluginFile: File): PluginManifest {
        val raw = ZipFile(pluginFile).use { zip ->
            val entry = zip.getEntry(MANIFEST_ENTRY) ?: error("plugin bundle is missing $MANIFEST_ENTRY")
            zip.getInputStream(entry).bufferedReader().use { it.readText() }
        }
        return PluginManifestCodec.parse(Json.parseToJsonElement(raw).jsonObject, PluginSource.EXTERNAL)
    }

    private fun readDexBuffers(pluginFile: File): Array<ByteBuffer> = ZipFile(pluginFile).use { zip ->
        val buffers = zip.entries().asSequence()
            .filter { it.name.matches(DEX_NAME) }
            .sortedBy { it.name }
            .map { entry -> ByteBuffer.wrap(zip.getInputStream(entry).use { it.readBytes() }) }
            .toList()
        require(buffers.isNotEmpty()) { "plugin bundle has no classes.dex" }
        buffers.toTypedArray()
    }

    private companion object {
        const val MANIFEST_ENTRY = "assets/plugin.json"
        val DEX_NAME = Regex("classes[0-9]*\\.dex")
    }
}
