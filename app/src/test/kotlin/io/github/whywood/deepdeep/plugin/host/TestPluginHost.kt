package io.github.whywood.deepdeep.plugin.host

import io.github.whywood.deepdeep.plugin.api.LocalizedText
import io.github.whywood.deepdeep.plugin.api.PluginCapability
import io.github.whywood.deepdeep.plugin.api.PluginHost
import io.github.whywood.deepdeep.plugin.api.PluginManifest
import io.github.whywood.deepdeep.plugin.api.assetIcon
import io.github.whywood.deepdeep.plugin.host.external.HostPluginManifest
import io.github.whywood.deepdeep.plugin.host.external.hostLocalizedText
import java.io.File
import java.lang.reflect.Proxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

internal fun testManifest(id: String, version: String = "1.0.0"): HostPluginManifest = HostPluginManifest(
    id = id,
    version = version,
    name = hostLocalizedText("name"),
    summary = hostLocalizedText("summary"),
    icon = assetIcon("icon.png"),
    capabilities = PluginCapability.ALL.toSet(),
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
            "text" -> (args?.getOrNull(0) as? LocalizedText)?.resolve().orEmpty()
            else -> error("test host does not implement ${method.name}")
        }
    } as PluginHost
