package io.github.whywood.superpi.ui.miniapp

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import io.github.whywood.superpi.ui.common.chat.CameraCaptureSheet
import io.github.whywood.superpi.ui.common.chat.readQrCodes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicLong

private const val PICKER_TIMEOUT_MS = 10 * 60 * 1000L

internal sealed interface MiniappInteraction {
    val key: Long
    val completion: CompletableDeferred<Unit>

    class Permissions(
        override val key: Long,
        val permissions: Array<String>,
        val result: CompletableDeferred<Map<String, Boolean>>,
    ) : MiniappInteraction {
        override val completion = CompletableDeferred<Unit>()

        fun complete(grants: Map<String, Boolean>) {
            result.complete(grants)
            completion.complete(Unit)
        }
    }

    class PickFiles(
        override val key: Long,
        val types: List<String>,
        val multiple: Boolean,
        val result: CompletableDeferred<List<Uri>>,
    ) : MiniappInteraction {
        override val completion = CompletableDeferred<Unit>()

        fun complete(uris: List<Uri>) {
            result.complete(uris)
            completion.complete(Unit)
        }
    }

    class PickMedia(
        override val key: Long,
        val type: String,
        val multiple: Boolean,
        val result: CompletableDeferred<List<Uri>>,
    ) : MiniappInteraction {
        override val completion = CompletableDeferred<Unit>()

        fun complete(uris: List<Uri>) {
            result.complete(uris)
            completion.complete(Unit)
        }
    }

    class CapturePhoto(
        override val key: Long,
        val result: CompletableDeferred<Uri?>,
    ) : MiniappInteraction {
        override val completion = CompletableDeferred<Unit>()

        fun complete(uri: Uri?) {
            result.complete(uri)
            completion.complete(Unit)
        }
    }

    class ScanQr(
        override val key: Long,
        val result: CompletableDeferred<String?>,
    ) : MiniappInteraction {
        override val completion = CompletableDeferred<Unit>()

        fun complete(text: String?) {
            result.complete(text)
            completion.complete(Unit)
        }
    }

    class ExportFile(
        override val key: Long,
        val name: String,
        val mimeType: String,
        val result: CompletableDeferred<Uri?>,
    ) : MiniappInteraction {
        override val completion = CompletableDeferred<Unit>()

        fun complete(uri: Uri?) {
            result.complete(uri)
            completion.complete(Unit)
        }
    }
}

internal object MiniappInteractionQueue {
    private val counter = AtomicLong(0)
    private val active = MutableStateFlow<MiniappInteraction?>(null)

    val activeRequest: StateFlow<MiniappInteraction?> = active.asStateFlow()

    @Volatile
    var lastLaunchedKey: Long = -1

    fun nextKey(): Long = counter.incrementAndGet()

    fun submit(request: MiniappInteraction) {
        active.value = request
    }

    fun finish(request: MiniappInteraction) {
        if (active.value?.key == request.key) active.value = null
    }
}

internal class MiniappInteractions {
    suspend fun requestPermissions(permissions: Array<String>): Map<String, Boolean> {
        if (permissions.isEmpty()) return emptyMap()
        return submit(
            timeoutMs = PICKER_TIMEOUT_MS,
            fallback = permissions.associateWith { false },
            create = { key, result -> MiniappInteraction.Permissions(key, permissions, result) },
        )
    }

    suspend fun pickFiles(types: List<String>, multiple: Boolean): List<Uri> = submit(
        timeoutMs = PICKER_TIMEOUT_MS,
        fallback = emptyList(),
        create = { key, result -> MiniappInteraction.PickFiles(key, types, multiple, result) },
    )

    suspend fun pickMedia(type: String, multiple: Boolean): List<Uri> = submit(
        timeoutMs = PICKER_TIMEOUT_MS,
        fallback = emptyList(),
        create = { key, result -> MiniappInteraction.PickMedia(key, type, multiple, result) },
    )

    suspend fun capturePhoto(): Uri? = submit(
        timeoutMs = PICKER_TIMEOUT_MS,
        fallback = null,
        create = { key, result -> MiniappInteraction.CapturePhoto(key, result) },
    )

    suspend fun scanQr(): String? = submit(
        timeoutMs = PICKER_TIMEOUT_MS,
        fallback = null,
        create = { key, result -> MiniappInteraction.ScanQr(key, result) },
    )

    suspend fun exportDocument(name: String, mimeType: String): Uri? = submit(
        timeoutMs = PICKER_TIMEOUT_MS,
        fallback = null,
        create = { key, result -> MiniappInteraction.ExportFile(key, name, mimeType, result) },
    )

    private suspend fun <T> submit(
        timeoutMs: Long,
        fallback: T,
        create: (Long, CompletableDeferred<T>) -> MiniappInteraction,
    ): T {
        val result = CompletableDeferred<T>()
        val request = create(MiniappInteractionQueue.nextKey(), result)
        MiniappInteractionQueue.submit(request)
        return try {
            withTimeout(timeoutMs) { result.await() }
        } catch (timeout: TimeoutCancellationException) {
            MiniappInteractionQueue.finish(request)
            fallback
        } catch (cancelled: CancellationException) {
            MiniappInteractionQueue.finish(request)
            throw cancelled
        } finally {
            if (result.isCompleted) MiniappInteractionQueue.finish(request)
        }
    }
}

@Composable
internal fun MiniappInteractionOverlay() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val active by MiniappInteractionQueue.activeRequest.collectAsState()
    var launched by remember { mutableStateOf<MiniappInteraction?>(null) }
    var resumed by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        resumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val request = launched as? MiniappInteraction.Permissions ?: return@rememberLauncherForActivityResult
        if (request.completion.isCompleted) return@rememberLauncherForActivityResult
        request.complete(grants)
        MiniappInteractionQueue.finish(request)
    }

    val filesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uris = urisOf(result.resultCode, result.data)
        when (val request = launched) {
            is MiniappInteraction.PickFiles -> {
                request.complete(uris)
                MiniappInteractionQueue.finish(request)
            }

            is MiniappInteraction.ExportFile -> {
                if (request.completion.isCompleted) return@rememberLauncherForActivityResult
                request.complete(result.data?.data)
                MiniappInteractionQueue.finish(request)
            }

            else -> Unit
        }
    }

    val mediaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uris = urisOf(result.resultCode, result.data)
        when (val request = launched) {
            is MiniappInteraction.PickMedia -> {
                request.complete(uris)
                MiniappInteractionQueue.finish(request)
            }

            else -> Unit
        }
    }

    LaunchedEffect(active?.key, resumed) {
        val request = active ?: return@LaunchedEffect
        if (!resumed || MiniappInteractionQueue.lastLaunchedKey == request.key) return@LaunchedEffect
        launched = request
        when (request) {
            is MiniappInteraction.Permissions -> {
                MiniappInteractionQueue.lastLaunchedKey = request.key
                permissionsLauncher.launch(request.permissions)
            }

            is MiniappInteraction.PickFiles -> {
                MiniappInteractionQueue.lastLaunchedKey = request.key
                filesLauncher.launch(
                    Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = when {
                            request.types.isEmpty() -> "*/*"
                            request.types.size == 1 -> request.types.first()
                            else -> "*/*"
                        }
                        if (request.types.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, request.types.toTypedArray())
                        if (request.multiple) putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    },
                )
            }

            is MiniappInteraction.PickMedia -> {
                MiniappInteractionQueue.lastLaunchedKey = request.key
                mediaLauncher.launch(
                    Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = when (request.type) {
                            "video" -> "video/*"
                            "any" -> "*/*"
                            else -> "image/*"
                        }
                        if (request.multiple) putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    },
                )
            }

            is MiniappInteraction.ExportFile -> {
                MiniappInteractionQueue.lastLaunchedKey = request.key
                filesLauncher.launch(
                    Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = request.mimeType
                        putExtra(Intent.EXTRA_TITLE, request.name)
                    },
                )
            }

            else -> Unit
        }
    }

    when (val request = active) {
        is MiniappInteraction.CapturePhoto -> CameraCaptureSheet(
            onDismiss = {
                request.complete(null)
                MiniappInteractionQueue.finish(request)
            },
            onCaptured = { uri ->
                request.complete(uri)
                MiniappInteractionQueue.finish(request)
            },
        )

        is MiniappInteraction.ScanQr -> CameraCaptureSheet(
            onDismiss = {
                request.complete(null)
                MiniappInteractionQueue.finish(request)
            },
            onCaptured = { uri ->
                scope.launch {
                    val text = withContext(Dispatchers.IO) { decodeQr(context, uri) }
                    request.complete(text)
                    MiniappInteractionQueue.finish(request)
                }
            },
        )

        else -> Unit
    }
}

private fun urisOf(resultCode: Int, data: Intent?): List<Uri> {
    if (resultCode != Activity.RESULT_OK || data == null) return emptyList()
    val clip = data.clipData
    if (clip != null) {
        return (0 until clip.itemCount).mapNotNull { index -> clip.getItemAt(index)?.uri }
    }
    return listOfNotNull(data.data)
}

private suspend fun decodeQr(context: android.content.Context, uri: Uri): String? {
    val bitmap = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream)
            }
        }.getOrNull()
    } ?: return null
    return runCatching { readQrCodes(bitmap).firstOrNull() }.getOrNull()
}
