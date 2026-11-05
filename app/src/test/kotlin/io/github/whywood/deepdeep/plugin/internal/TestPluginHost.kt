package io.github.whywood.deepdeep.plugin.internal

import io.github.whywood.deepdeep.plugin.api.IconRef
import io.github.whywood.deepdeep.plugin.api.LocalizedText
import io.github.whywood.deepdeep.plugin.api.PluginHost
import io.github.whywood.deepdeep.plugin.api.PluginManifest
import io.github.whywood.deepdeep.plugin.api.PluginSource
import java.io.File
import java.lang.reflect.Proxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

internal fun testBuiltinManifest(id: String, version: String = "1.0"): PluginManifest = PluginManifest(
    id = id,
    version = version,
    name = LocalizedText.Literal("name"),
    summary = LocalizedText.Literal("summary"),
    icon = IconRef.Resource(0),
    source = PluginSource.BUILTIN,
)

internal fun testPluginHost(
    manifest: PluginManifest,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
): PluginHost =
    Proxy.newProxyInstance(
        PluginHost::class.java.classLoader,
        arrayOf(PluginHost::class.java),
    ) { _, method, args ->
        when (method.name) {
            "getManifest" -> manifest
            "getDataDir" -> File("/tmp/deepdeep-plugin-test/data")
            "getAssetsDir" -> File("/tmp/deepdeep-plugin-test/assets")
            "getScope" -> scope
            "text" -> "text:${(args?.getOrNull(0) as? LocalizedText.Resource)?.id ?: "literal"}"
            else -> error("test host does not implement ${method.name}")
        }
    } as PluginHost
