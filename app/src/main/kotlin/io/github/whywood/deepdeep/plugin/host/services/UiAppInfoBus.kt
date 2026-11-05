package io.github.whywood.deepdeep.plugin.host.services

import io.github.whywood.deepdeep.plugin.api.AppInfo
import io.github.whywood.deepdeep.plugin.api.AppInfoService
import io.github.whywood.deepdeep.plugin.api.PluginBus
import io.github.whywood.deepdeep.plugin.api.PluginBusEvent
import io.github.whywood.deepdeep.plugin.api.PluginServiceUnavailable
import io.github.whywood.deepdeep.plugin.api.ShareRequest
import io.github.whywood.deepdeep.plugin.api.UiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.BufferOverflow

internal class UiPluginService(
    private val openUrlCallback: ((String) -> Unit)?,
    private val shareCallback: ((ShareRequest) -> Unit)?,
    private val toastCallback: ((String) -> Unit)?,
    private val openPageCallback: ((String) -> Unit)?,
) : UiService {
    override fun openUrl(url: String) {
        callback(openUrlCallback, "ui.openUrl")(url)
    }

    override fun share(request: ShareRequest) {
        callback(shareCallback, "ui.share")(request)
    }

    override fun toast(message: String) {
        callback(toastCallback, "ui.toast")(message)
    }

    override fun openPage(pageId: String) {
        callback(openPageCallback, "ui.openPage")(pageId)
    }

    private fun <T> callback(value: T?, service: String): T =
        value ?: throw PluginServiceUnavailable(service)
}

internal class AppInfoPluginService(
    private val versionName: String,
    private val versionCode: Long,
    private val language: () -> String,
    private val sdkInt: () -> Int,
    private val deviceModel: () -> String,
    installationId: suspend () -> String,
    scope: CoroutineScope,
) : AppInfoService {
    @Volatile
    private var cachedInstallationId: String = ""

    init {
        scope.launch {
            cachedInstallationId = runCatching { installationId() }.getOrDefault("")
        }
    }

    override val info: AppInfo
        get() = AppInfo(
            versionName = versionName,
            versionCode = versionCode,
            language = language(),
            androidSdkInt = sdkInt(),
            deviceModel = deviceModel(),
            installationId = cachedInstallationId,
        )
}

internal class PluginBusImpl : PluginBus {
    private val events = MutableSharedFlow<PluginBusEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override fun publish(event: PluginBusEvent) {
        val stamped = if (event.publishedAt == 0L) event.copy(publishedAt = System.currentTimeMillis()) else event
        events.tryEmit(stamped)
    }

    override fun subscribe(topic: String?): Flow<PluginBusEvent> =
        if (topic == null) events else events.filter { it.topic == topic }
}

internal class ScopedPluginBus(
    private val bus: PluginBus,
    private val pluginId: String,
) : PluginBus {
    override fun publish(event: PluginBusEvent) {
        bus.publish(event.copy(publisher = pluginId))
    }

    override fun subscribe(topic: String?): Flow<PluginBusEvent> = bus.subscribe(topic)
}
