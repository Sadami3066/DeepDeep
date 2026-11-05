package io.github.whywood.deepdeep.plugin.host

import android.content.Context
import io.github.whywood.deepdeep.plugin.api.LocalizedText
import io.github.whywood.deepdeep.plugin.api.PluginHost
import io.github.whywood.deepdeep.plugin.api.PluginManifest
import java.io.File
import java.lang.reflect.Proxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

internal fun stubPluginHost(manifest: PluginManifest, context: Context): PluginHost =
    Proxy.newProxyInstance(
        PluginHost::class.java.classLoader,
        arrayOf(PluginHost::class.java),
    ) { _, method, args ->
        when (method.name) {
            "getManifest" -> manifest
            "getContext" -> context
            "getDataDir" -> File(context.cacheDir, "plugin-spike/data")
            "getAssetsDir" -> File(context.cacheDir, "plugin-spike/assets")
            "getScope" -> CoroutineScope(SupervisorJob() + Dispatchers.IO)
            "text" -> resolveText(context, args?.getOrNull(0) as? LocalizedText)
            "log" -> null
            else -> error("stub host does not implement ${method.name}")
        }
    } as PluginHost

private fun resolveText(context: Context, source: LocalizedText?): String = when (source) {
    is LocalizedText.Literal -> source.values["default"]
        ?: source.values.values.firstOrNull().orEmpty()

    is LocalizedText.Resource -> context.getString(source.id)
    null -> ""
}
