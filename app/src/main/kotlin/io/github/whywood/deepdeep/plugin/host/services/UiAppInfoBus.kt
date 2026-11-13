package io.github.whywood.deepdeep.plugin.host.services

import android.content.Context
import android.content.res.Configuration
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.plugin.api.AppInfo
import io.github.whywood.deepdeep.plugin.api.AppInfoService
import io.github.whywood.deepdeep.plugin.api.PluginBus
import io.github.whywood.deepdeep.plugin.api.PluginBusEvent
import io.github.whywood.deepdeep.plugin.api.PluginPageContext
import io.github.whywood.deepdeep.plugin.api.PluginPickedFile
import io.github.whywood.deepdeep.plugin.api.PluginServiceUnavailable
import io.github.whywood.deepdeep.plugin.api.ShareRequest
import io.github.whywood.deepdeep.plugin.api.UiChooseRequest
import io.github.whywood.deepdeep.plugin.api.UiConfirmRequest
import io.github.whywood.deepdeep.plugin.api.UiDatePickRequest
import io.github.whywood.deepdeep.plugin.api.UiFilePickRequest
import io.github.whywood.deepdeep.plugin.api.UiImagePickRequest
import io.github.whywood.deepdeep.plugin.api.UiPromptRequest
import io.github.whywood.deepdeep.plugin.api.UiService
import io.github.whywood.deepdeep.plugin.api.UiTimePickRequest
import io.github.whywood.deepdeep.plugin.host.events.HostPluginBusEvent
import io.github.whywood.deepdeep.plugin.host.interaction.PluginInteraction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.serialization.json.JsonObject

internal class UiPluginService(
    private val openUrlCallback: ((String) -> Unit)?,
    private val shareCallback: ((ShareRequest) -> Unit)?,
    private val toastCallback: ((String) -> Unit)?,
    private val openPageCallback: ((String) -> Unit)?,
    private val interaction: PluginInteraction? = null,
    private val context: Context? = null,
) : UiService {
    override fun openUrl(url: String) {
        callback(openUrlCallback, "ui.openUrl")(url)
    }

    override fun share(request: ShareRequest) {
        callback(shareCallback, "ui.share")(request)
    }

    override fun shareFiles(paths: List<String>, mimeType: String?) {
        val targetContext = context ?: throw PluginServiceUnavailable("ui.shareFiles")
        if (paths.none { it.isNotBlank() }) {
            safeToast(targetContext.getString(R.string.plugin_host_share_failed))
            return
        }
        runCatching { PluginUiBridge.shareFiles(targetContext, paths, mimeType) }
            .onFailure { safeToast(targetContext.getString(R.string.plugin_host_share_failed)) }
    }

    override fun toast(message: String) {
        callback(toastCallback, "ui.toast")(message)
    }

    override fun openPage(pageId: String) {
        callback(openPageCallback, "ui.openPage")(pageId)
    }

    override fun openPageWithArguments(pageId: String, arguments: JsonObject, newTask: Boolean) {
        val active = interaction
        if (active != null) {
            active.openPage(pageId, arguments, newTask)
        } else {
            openPage(pageId)
        }
    }

    override fun closePage(result: JsonObject?) {
        interaction?.closePage(result)
    }

    override fun pageContext(): PluginPageContext? = interaction?.pageContext()

    override val isDark: Boolean
        get() = interaction?.dark ?: context?.let { target ->
            (target.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        } ?: false

    override suspend fun confirm(request: UiConfirmRequest): Boolean = interaction?.confirm(request) ?: false

    override suspend fun prompt(request: UiPromptRequest): String? = interaction?.prompt(request)

    override suspend fun choose(request: UiChooseRequest): String? = interaction?.choose(request)

    override suspend fun pickDate(request: UiDatePickRequest): Long? = interaction?.pickDate(request)

    override suspend fun pickTime(request: UiTimePickRequest): Int? = interaction?.pickTime(request)

    override suspend fun pickFiles(request: UiFilePickRequest): List<PluginPickedFile> =
        interaction?.pickFiles(request).orEmpty()

    override suspend fun pickImage(request: UiImagePickRequest): PluginPickedFile? =
        interaction?.pickImage(request)

    override suspend fun scanQr(): String? = interaction?.scanQr()

    override suspend fun pickDirectory(): String? = interaction?.pickDirectory()

    override fun snackbar(message: String, actionLabel: String?, action: (() -> Unit)?) {
        val active = interaction
        if (active != null) {
            active.snackbar(message, actionLabel, action)
        } else {
            safeToast(message)
        }
    }

    private fun safeToast(message: String) {
        runCatching { toastCallback?.invoke(message) }
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
        val publishedAt = if (event.publishedAt == 0L) System.currentTimeMillis() else event.publishedAt
        events.tryEmit(
            HostPluginBusEvent(
                topic = event.topic,
                publisher = event.publisher,
                payload = event.payload,
                publishedAt = publishedAt,
            ),
        )
    }

    override fun subscribe(topic: String?): Flow<PluginBusEvent> =
        if (topic == null) events else events.filter { it.topic == topic }
}

internal class ScopedPluginBus(
    private val bus: PluginBus,
    private val pluginId: String,
) : PluginBus {
    override fun publish(event: PluginBusEvent) {
        bus.publish(
            HostPluginBusEvent(
                topic = event.topic,
                publisher = pluginId,
                payload = event.payload,
                publishedAt = event.publishedAt,
            ),
        )
    }

    override fun subscribe(topic: String?): Flow<PluginBusEvent> = bus.subscribe(topic)
}
