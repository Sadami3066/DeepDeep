package io.github.whywood.deepdeep.plugin.host.interaction

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import io.github.whywood.deepdeep.plugin.api.PluginActivityResult
import io.github.whywood.deepdeep.plugin.api.PluginPageContext
import io.github.whywood.deepdeep.plugin.api.PluginPickedFile
import io.github.whywood.deepdeep.plugin.api.UiChooseRequest
import io.github.whywood.deepdeep.plugin.api.UiConfirmRequest
import io.github.whywood.deepdeep.plugin.api.UiDatePickRequest
import io.github.whywood.deepdeep.plugin.api.UiFilePickRequest
import io.github.whywood.deepdeep.plugin.api.UiImagePickRequest
import io.github.whywood.deepdeep.plugin.api.UiPromptRequest
import io.github.whywood.deepdeep.plugin.api.UiTimePickRequest
import io.github.whywood.deepdeep.plugin.host.components.PluginHostActivity
import io.github.whywood.deepdeep.plugin.host.services.PluginUiBridge
import io.github.whywood.deepdeep.service.AppVisibility
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject

internal data class HostPluginPageContext(
    override val pageId: String,
    override val arguments: JsonObject,
) : PluginPageContext

internal data class ActivePluginPage(
    val pluginId: String,
    val pageId: String,
    val context: PluginPageContext,
)

internal object PluginPageContexts {
    private val active = MutableStateFlow<ActivePluginPage?>(null)

    val current: StateFlow<ActivePluginPage?> = active.asStateFlow()

    fun enter(pluginId: String, pageId: String, context: PluginPageContext) {
        active.value = ActivePluginPage(pluginId, pageId, context)
    }

    fun exit(pluginId: String, pageId: String) {
        active.update { current ->
            if (current != null && current.pluginId == pluginId && current.pageId == pageId) null else current
        }
    }
}

internal data class PluginSnackbarMessage(
    val message: String,
    val actionLabel: String?,
    val action: (() -> Unit)?,
)

internal object PluginInteractionHost {
    private val activityReference = AtomicReference<WeakReference<Activity>?>(null)

    private val snackbars = MutableSharedFlow<PluginSnackbarMessage>(
        extraBufferCapacity = 8,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )

    val snackbarMessages: SharedFlow<PluginSnackbarMessage> = snackbars.asSharedFlow()

    val currentActivity: Activity?
        get() = activityReference.get()?.get()

    fun attachActivity(activity: Activity) {
        activityReference.set(WeakReference(activity))
    }

    fun detachActivity(activity: Activity) {
        val reference = activityReference.get() ?: return
        if (reference.get() === activity) {
            activityReference.compareAndSet(reference, null)
        }
    }

    fun forPlugin(pluginId: String, context: Context): PluginInteraction =
        PluginInteractionImpl(pluginId, context.applicationContext)

    fun launchMainActivity(context: Context) {
        AppVisibility.bringToFront(context)
    }

    fun showSnackbar(message: String, actionLabel: String?, action: (() -> Unit)?) {
        if (message.isBlank()) return
        snackbars.tryEmit(PluginSnackbarMessage(message, actionLabel, action))
    }
}

private class PluginInteractionImpl(
    private val pluginId: String,
    private val context: Context,
) : PluginInteraction {
    override val foreground: Boolean
        get() = AppVisibility.foreground

    override val dark: Boolean
        get() = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    override fun launchMainActivity() {
        PluginInteractionHost.launchMainActivity(context)
    }

    override suspend fun requestPermissions(permissions: Array<String>): Map<String, Boolean> =
        submit(
            timeoutMs = PermissionTimeoutMillis,
            fallback = emptyMap(),
            create = { key, result -> PluginInteractionRequest.Permissions(key, pluginId, permissions, result) },
        )

    override suspend fun startActivityForResult(
        intent: Intent,
        timeoutMs: Long,
    ): PluginActivityResult = submit(
        timeoutMs = timeoutMs.coerceIn(1_000L, MaxInteractionTimeoutMillis),
        fallback = PluginActivityResult(resultCode = 0, data = null, cancelled = true, timedOut = true),
        create = { key, result -> PluginInteractionRequest.StartActivity(key, pluginId, intent, result) },
    )

    override suspend fun confirm(request: UiConfirmRequest): Boolean = submit(
        timeoutMs = DialogTimeoutMillis,
        fallback = false,
        create = { key, result -> PluginInteractionRequest.Confirm(key, pluginId, request, result) },
    )

    override suspend fun prompt(request: UiPromptRequest): String? = submit(
        timeoutMs = DialogTimeoutMillis,
        fallback = null,
        create = { key, result -> PluginInteractionRequest.Prompt(key, pluginId, request, result) },
    )

    override suspend fun choose(request: UiChooseRequest): String? = submit(
        timeoutMs = DialogTimeoutMillis,
        fallback = null,
        create = { key, result -> PluginInteractionRequest.Choose(key, pluginId, request, result) },
    )

    override suspend fun pickDate(request: UiDatePickRequest): Long? = submit(
        timeoutMs = DialogTimeoutMillis,
        fallback = null,
        create = { key, result -> PluginInteractionRequest.PickDate(key, pluginId, request, result) },
    )

    override suspend fun pickTime(request: UiTimePickRequest): Int? = submit(
        timeoutMs = DialogTimeoutMillis,
        fallback = null,
        create = { key, result -> PluginInteractionRequest.PickTime(key, pluginId, request, result) },
    )

    override suspend fun pickFiles(request: UiFilePickRequest): List<PluginPickedFile> = submit(
        timeoutMs = PickerTimeoutMillis,
        fallback = emptyList(),
        create = { key, result -> PluginInteractionRequest.Files(key, pluginId, request, result) },
    )

    override suspend fun pickImage(request: UiImagePickRequest): PluginPickedFile? = submit(
        timeoutMs = PickerTimeoutMillis,
        fallback = null,
        create = { key, result -> PluginInteractionRequest.Image(key, pluginId, request, result) },
    )

    override suspend fun scanQr(): String? = submit(
        timeoutMs = PickerTimeoutMillis,
        fallback = null,
        create = { key, result -> PluginInteractionRequest.Scan(key, pluginId, result) },
    )

    override suspend fun pickDirectory(): String? = submit(
        timeoutMs = PickerTimeoutMillis,
        fallback = null,
        create = { key, result -> PluginInteractionRequest.Directory(key, pluginId, result) },
    )

    override suspend fun importUri(uri: Uri, fileName: String?): PluginPickedFile? =
        withContext(Dispatchers.IO) {
            PluginPickedFiles.import(context, pluginId, uri, fileName)
        }

    override fun snackbar(message: String, actionLabel: String?, action: (() -> Unit)?) {
        PluginInteractionHost.showSnackbar(message, actionLabel, action)
    }

    override fun openPage(pageId: String, arguments: JsonObject, newTask: Boolean) {
        if (newTask) {
            PluginHostActivity.launch(context, pluginId, pageId, arguments, newTask = true)
        } else {
            PluginUiBridge.requestPage(pluginId, pageId, arguments)
            PluginInteractionHost.launchMainActivity(context)
        }
    }

    override fun closePage(result: JsonObject?) {
        val active = PluginPageContexts.current.value ?: return
        if (active.pluginId != pluginId) return
        val activity = PluginInteractionHost.currentActivity
        if (activity is PluginHostActivity) {
            activity.finishWithResult(result)
        } else {
            PluginUiBridge.requestClosePage()
        }
    }

    override fun pageContext(): PluginPageContext? =
        PluginPageContexts.current.value?.takeIf { it.pluginId == pluginId }?.context

    override fun chooseOption(request: UiChooseRequest): String? = null

    private suspend fun <T> submit(
        timeoutMs: Long,
        fallback: T,
        create: (Long, CompletableDeferred<T>) -> PluginInteractionRequest,
    ): T {
        if (!foreground) PluginInteractionHost.launchMainActivity(context)
        val result = CompletableDeferred<T>()
        val request = create(PluginInteractionQueue.nextKey(), result)
        PluginInteractionQueue.submit(request)
        return try {
            withTimeout(timeoutMs) { result.await() }
        } catch (timeout: TimeoutCancellationException) {
            request.cancel()
            fallback
        } catch (cancelled: CancellationException) {
            request.cancel()
            throw cancelled
        } finally {
            if (!result.isCompleted) request.cancel()
        }
    }

    private companion object {
        const val PermissionTimeoutMillis = 300_000L
        const val DialogTimeoutMillis = 600_000L
        const val PickerTimeoutMillis = 600_000L
        const val MaxInteractionTimeoutMillis = 600_000L
    }
}
