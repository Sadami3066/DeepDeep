package io.github.whywood.deepdeep.plugin.host.interaction

import android.content.Intent
import io.github.whywood.deepdeep.plugin.api.PluginActivityResult
import io.github.whywood.deepdeep.plugin.api.PluginPickedFile
import io.github.whywood.deepdeep.plugin.api.UiChooseRequest
import io.github.whywood.deepdeep.plugin.api.UiConfirmRequest
import io.github.whywood.deepdeep.plugin.api.UiDatePickRequest
import io.github.whywood.deepdeep.plugin.api.UiFilePickRequest
import io.github.whywood.deepdeep.plugin.api.UiImagePickRequest
import io.github.whywood.deepdeep.plugin.api.UiPromptRequest
import io.github.whywood.deepdeep.plugin.api.UiTimePickRequest
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal sealed class PluginInteractionRequest {
    abstract val key: Long
    abstract val pluginId: String
    abstract val completion: CompletableDeferred<Unit>

    abstract fun cancel()

    class Permissions(
        override val key: Long,
        override val pluginId: String,
        val permissions: Array<String>,
        val result: CompletableDeferred<Map<String, Boolean>>,
    ) : PluginInteractionRequest() {
        override val completion: CompletableDeferred<Unit> = CompletableDeferred()

        fun complete(grants: Map<String, Boolean>) {
            result.complete(grants)
            completion.complete(Unit)
        }

        override fun cancel() = complete(emptyMap())
    }

    class StartActivity(
        override val key: Long,
        override val pluginId: String,
        val intent: Intent,
        val result: CompletableDeferred<PluginActivityResult>,
    ) : PluginInteractionRequest() {
        override val completion: CompletableDeferred<Unit> = CompletableDeferred()

        fun complete(activityResult: PluginActivityResult) {
            result.complete(activityResult)
            completion.complete(Unit)
        }

        override fun cancel() = complete(PluginActivityResult(resultCode = 0, data = null, cancelled = true))
    }

    class Confirm(
        override val key: Long,
        override val pluginId: String,
        val request: UiConfirmRequest,
        val result: CompletableDeferred<Boolean>,
    ) : PluginInteractionRequest() {
        override val completion: CompletableDeferred<Unit> = CompletableDeferred()

        fun complete(confirmed: Boolean) {
            result.complete(confirmed)
            completion.complete(Unit)
        }

        override fun cancel() = complete(false)
    }

    class Prompt(
        override val key: Long,
        override val pluginId: String,
        val request: UiPromptRequest,
        val result: CompletableDeferred<String?>,
    ) : PluginInteractionRequest() {
        override val completion: CompletableDeferred<Unit> = CompletableDeferred()

        fun complete(value: String?) {
            result.complete(value)
            completion.complete(Unit)
        }

        override fun cancel() = complete(null)
    }

    class Choose(
        override val key: Long,
        override val pluginId: String,
        val request: UiChooseRequest,
        val result: CompletableDeferred<String?>,
    ) : PluginInteractionRequest() {
        override val completion: CompletableDeferred<Unit> = CompletableDeferred()

        fun complete(value: String?) {
            result.complete(value)
            completion.complete(Unit)
        }

        override fun cancel() = complete(null)
    }

    class PickDate(
        override val key: Long,
        override val pluginId: String,
        val request: UiDatePickRequest,
        val result: CompletableDeferred<Long?>,
    ) : PluginInteractionRequest() {
        override val completion: CompletableDeferred<Unit> = CompletableDeferred()

        fun complete(value: Long?) {
            result.complete(value)
            completion.complete(Unit)
        }

        override fun cancel() = complete(null)
    }

    class PickTime(
        override val key: Long,
        override val pluginId: String,
        val request: UiTimePickRequest,
        val result: CompletableDeferred<Int?>,
    ) : PluginInteractionRequest() {
        override val completion: CompletableDeferred<Unit> = CompletableDeferred()

        fun complete(value: Int?) {
            result.complete(value)
            completion.complete(Unit)
        }

        override fun cancel() = complete(null)
    }

    class Files(
        override val key: Long,
        override val pluginId: String,
        val request: UiFilePickRequest,
        val result: CompletableDeferred<List<PluginPickedFile>>,
    ) : PluginInteractionRequest() {
        override val completion: CompletableDeferred<Unit> = CompletableDeferred()

        fun complete(files: List<PluginPickedFile>) {
            result.complete(files)
            completion.complete(Unit)
        }

        override fun cancel() = complete(emptyList())
    }

    class Image(
        override val key: Long,
        override val pluginId: String,
        val request: UiImagePickRequest,
        val result: CompletableDeferred<PluginPickedFile?>,
    ) : PluginInteractionRequest() {
        override val completion: CompletableDeferred<Unit> = CompletableDeferred()

        fun complete(file: PluginPickedFile?) {
            result.complete(file)
            completion.complete(Unit)
        }

        override fun cancel() = complete(null)
    }

    class Scan(
        override val key: Long,
        override val pluginId: String,
        val result: CompletableDeferred<String?>,
    ) : PluginInteractionRequest() {
        override val completion: CompletableDeferred<Unit> = CompletableDeferred()

        fun complete(value: String?) {
            result.complete(value)
            completion.complete(Unit)
        }

        override fun cancel() = complete(null)
    }

    class Directory(
        override val key: Long,
        override val pluginId: String,
        val result: CompletableDeferred<String?>,
    ) : PluginInteractionRequest() {
        override val completion: CompletableDeferred<Unit> = CompletableDeferred()

        fun complete(value: String?) {
            result.complete(value)
            completion.complete(Unit)
        }

        override fun cancel() = complete(null)
    }
}

internal object PluginInteractionQueue {
    private val counter = AtomicLong(0L)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val active = MutableStateFlow<PluginInteractionRequest?>(null)

    private val requests: Channel<PluginInteractionRequest> = Channel(capacity = Channel.UNLIMITED)

    val activeRequest: StateFlow<PluginInteractionRequest?> = active.asStateFlow()

    @Volatile
    var lastLaunchedKey: Long? = null

    init {
        scope.launch {
            for (request in requests) {
                active.value = request
                request.completion.join()
                if (active.value === request) active.value = null
            }
        }
    }

    fun nextKey(): Long = counter.incrementAndGet()

    fun submit(request: PluginInteractionRequest) {
        requests.trySend(request)
    }
}
