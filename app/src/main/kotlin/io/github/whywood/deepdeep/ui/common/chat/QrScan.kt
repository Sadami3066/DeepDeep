package io.github.whywood.deepdeep.ui.common.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSheetHeader
import io.github.whywood.deepdeep.design.DdSpacing
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

private const val QrScanMaxImageBytes = 8L * 1024 * 1024
private const val QrScanMaxImageSide = 2048
private const val QrScanTimeoutMillis = 15_000L

private val qrImageClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
}

internal sealed interface QrScanTarget {
    data class LocalFile(val path: String) : QrScanTarget

    data class RemoteUrl(val url: String) : QrScanTarget
}

internal enum class QrScanStatus {
    Idle,
    Scanning,
    Found,
    Empty,
    Failed,
}

internal data class QrScanState(
    val status: QrScanStatus = QrScanStatus.Idle,
    val codes: List<String> = emptyList(),
    val target: QrScanTarget? = null,
    val attempt: Int = 0,
) {
    fun scanning(target: QrScanTarget): QrScanState =
        copy(status = QrScanStatus.Scanning, codes = emptyList(), target = target, attempt = attempt + 1)

    fun completed(codes: List<String>?): QrScanState = copy(
        status = when {
            codes == null -> QrScanStatus.Failed
            codes.isEmpty() -> QrScanStatus.Empty
            else -> QrScanStatus.Found
        },
        codes = codes.orEmpty(),
    )
}

internal val QrScanStateSaver: Saver<QrScanState, Any> = listSaver<QrScanState, Any>(
    save = { state -> state.toSaveableList() },
    restore = { saved -> qrScanStateFromSaveable(saved) },
)

internal fun QrScanState.toSaveableList(): List<Any> = listOf(
    status.ordinal,
    attempt,
    ArrayList(codes),
    when (val current = target) {
        null -> ""
        is QrScanTarget.LocalFile -> "file:${current.path}"
        is QrScanTarget.RemoteUrl -> "url:${current.url}"
    },
)

internal fun qrScanStateFromSaveable(saved: List<Any?>): QrScanState? {
    if (saved.size != 4) return null
    val status = QrScanStatus.entries.getOrNull(saved[0] as? Int ?: return null) ?: return null
    val attempt = saved[1] as? Int ?: return null
    val codes = (saved[2] as? List<*>)?.filterIsInstance<String>().orEmpty()
    val target = when (val encoded = saved[3] as? String ?: return null) {
        "" -> null
        else -> when {
            encoded.startsWith("file:") -> QrScanTarget.LocalFile(encoded.removePrefix("file:"))
            encoded.startsWith("url:") -> QrScanTarget.RemoteUrl(encoded.removePrefix("url:"))
            else -> return null
        }
    }
    return QrScanState(status = status, codes = codes, target = target, attempt = attempt)
}

internal class QrScanner internal constructor(
    private val stateRef: MutableState<QrScanState>,
) {
    val state: QrScanState get() = stateRef.value

    fun requestScan(target: QrScanTarget) {
        val current = stateRef.value
        val seen = current.status == QrScanStatus.Scanning ||
            current.status == QrScanStatus.Found ||
            current.status == QrScanStatus.Empty
        if (seen) return
        stateRef.value = current.scanning(target)
    }

    fun retry() {
        val current = stateRef.value
        val target = current.target ?: return
        stateRef.value = current.scanning(target)
    }
}

@Composable
internal fun rememberQrScanner(vararg keys: Any?): QrScanner {
    val context = LocalContext.current.applicationContext
    val stateRef = rememberSaveable(*keys, stateSaver = QrScanStateSaver) { mutableStateOf(QrScanState()) }
    val scanner = remember(stateRef) { QrScanner(stateRef) }
    val current = stateRef.value
    LaunchedEffect(current.target, current.attempt) {
        val target = current.target ?: return@LaunchedEffect
        if (current.status != QrScanStatus.Scanning) return@LaunchedEffect
        stateRef.value = stateRef.value.completed(scanQrTarget(context, target))
    }
    return scanner
}

internal fun remoteImageUrl(source: String): String? {
    val trimmed = source.trim()
    return trimmed.takeIf {
        it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
    }
}

internal fun qrCopyLabelRes(index: Int, count: Int): Int =
    if (count > 1) R.string.files_qr_copy_index else R.string.files_qr_copy

internal suspend fun scanQrTarget(context: Context, target: QrScanTarget): List<String>? {
    val file = when (target) {
        is QrScanTarget.LocalFile -> File(target.path)
        is QrScanTarget.RemoteUrl -> downloadQrImage(context, target.url) ?: return null
    }
    return try {
        withTimeout(QrScanTimeoutMillis) { readQrCodesFromFile(file) }
    } catch (_: TimeoutCancellationException) {
        null
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        null
    } finally {
        if (target is QrScanTarget.RemoteUrl) file.delete()
    }
}

private suspend fun readQrCodesFromFile(file: File): List<String> {
    val bitmap = withContext(Dispatchers.IO) { decodeQrBitmap(file) } ?: throw IOException("unsupported image")
    return readQrCodes(bitmap)
}

private fun decodeQrBitmap(file: File): Bitmap? {
    if (!file.isFile || file.length() <= 0L) return null
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > QrScanMaxImageSide || bounds.outHeight / sample > QrScanMaxImageSide) {
            sample *= 2
        }
        BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    } catch (_: OutOfMemoryError) {
        null
    }
}

private suspend fun downloadQrImage(context: Context, url: String): File? {
    val request = runCatching {
        Request.Builder()
            .url(url)
            .header("User-Agent", "DeepDeep")
            .header("Accept", "image/*")
            .build()
    }.getOrNull() ?: return null
    val directory = File(context.cacheDir, "qr-scan")
    val file = runCatching {
        directory.mkdirs()
        File.createTempFile("qr-", ".img", directory)
    }.getOrNull() ?: return null
    return try {
        suspendCancellableCoroutine { continuation ->
            val call = qrImageClient.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    runCatching {
                        response.use { result ->
                            if (!result.isSuccessful) throw IOException("HTTP ${result.code}")
                            val body = result.body
                            if (body.contentLength() > QrScanMaxImageBytes) throw IOException("image too large")
                            file.outputStream().use { output ->
                                body.byteStream().use { input ->
                                    val buffer = ByteArray(16 * 1024)
                                    var total = 0L
                                    while (true) {
                                        val read = input.read(buffer)
                                        if (read < 0) break
                                        total += read
                                        if (total > QrScanMaxImageBytes) throw IOException("image too large")
                                        output.write(buffer, 0, read)
                                    }
                                }
                            }
                        }
                        if (continuation.isActive) continuation.resume(Unit)
                    }.onFailure { error ->
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
            })
        }
        if (file.length() <= 0L) {
            file.delete()
            null
        } else {
            file
        }
    } catch (cancellation: CancellationException) {
        file.delete()
        throw cancellation
    } catch (_: Exception) {
        file.delete()
        null
    }
}

@Composable
internal fun QrCodeResultSheet(codes: List<String>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val copied = stringResource(R.string.files_copied)
    val copyFailed = stringResource(R.string.files_copy_failed)
    DdSheet(onDismiss = onDismiss) {
        DdSheetHeader(title = stringResource(R.string.files_qr_result_title), onClose = onDismiss)
        Column(Modifier.fillMaxWidth().padding(horizontal = DdSpacing.Xxl)) {
            Spacer(Modifier.height(DdSpacing.Sm))
            codes.forEachIndexed { index, raw ->
                DdListRow(
                    title = stringResource(qrCopyLabelRes(index, codes.size), index + 1),
                    subtitle = raw,
                    subtitleMaxLines = 2,
                    icon = DdIcons.Copy,
                    onClick = {
                        if (copyToClipboard(context, raw)) {
                            Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                            onDismiss()
                        } else {
                            Toast.makeText(context, copyFailed, Toast.LENGTH_SHORT).show()
                        }
                    },
                )
                if (index != codes.lastIndex) DdHairline()
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}
