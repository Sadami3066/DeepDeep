package io.github.whywood.deepdeep.plugin.host.services

import android.util.Log as AndroidLog
import io.github.whywood.deepdeep.plugin.api.LogService
import io.github.whywood.deepdeep.plugin.api.PluginLogLevel

internal class LogPluginService(
    pluginId: String,
    customSink: ((PluginLogLevel, String, Throwable?) -> Unit)? = null,
) : LogService {
    private val sink: (PluginLogLevel, String, Throwable?) -> Unit = customSink ?: androidSink(pluginId)

    override fun log(level: PluginLogLevel, message: String, error: Throwable?) {
        sink(level, message, error)
    }

    private companion object {
        fun androidSink(pluginId: String): (PluginLogLevel, String, Throwable?) -> Unit {
            val tag = "DeepDeepPlugin" + pluginId.takeIf { it.isNotBlank() }?.let { "/$it" }.orEmpty()
            return { level, message, error ->
                when (level) {
                    PluginLogLevel.DEBUG -> AndroidLog.d(tag, message, error)
                    PluginLogLevel.INFO -> AndroidLog.i(tag, message, error)
                    PluginLogLevel.WARN -> AndroidLog.w(tag, message, error)
                    PluginLogLevel.ERROR -> AndroidLog.e(tag, message, error)
                }
            }
        }
    }
}
