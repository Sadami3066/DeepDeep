package io.github.whywood.deepdeep.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import io.github.whywood.deepdeep.R
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface ShareStageFailure {
    val name: String

    data class TooLarge(override val name: String) : ShareStageFailure

    data class Unreadable(override val name: String) : ShareStageFailure
}

data class StagedShareFile(
    val file: File,
    val name: String,
    val mime: String,
)

data class ShareStagingResult(
    val token: String,
    val files: List<StagedShareFile>,
    val failures: List<ShareStageFailure>,
)

class ShareStaging(context: Context) {
    private val appContext = context.applicationContext
    private val messageContext = LocalizedContext(appContext)
    private val root = File(appContext.cacheDir, DIRECTORY)
    private val resolver: ContentResolver = appContext.contentResolver

    suspend fun stage(streams: List<String>): ShareStagingResult = withContext(Dispatchers.IO) {
        pruneStale()
        val token = UUID.randomUUID().toString().replace("-", "").take(12)
        val directory = File(root, token).apply { mkdirs() }
        val usedNames = mutableSetOf<String>()
        val files = mutableListOf<StagedShareFile>()
        val failures = mutableListOf<ShareStageFailure>()
        streams.forEach { raw ->
            val uri = runCatching { Uri.parse(raw) }.getOrNull()
            val fallback = runCatching { uri?.lastPathSegment?.substringAfterLast('/') }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: "shared"
            val meta = if (uri == null) null else queryMeta(uri)
            val name = uniqueName(
                sanitizeAttachmentName(meta?.name ?: fallback),
                usedNames,
            )
            val mime = meta?.mime?.takeIf { it.isNotBlank() }
                ?: guessMime(name)
            if (uri == null || meta == null) {
                failures += ShareStageFailure.Unreadable(name)
                return@forEach
            }
            if (meta.size > FileAttachmentStore.MAX_BYTES) {
                failures += ShareStageFailure.TooLarge(name)
                return@forEach
            }
            val target = File(directory, name)
            val copied = copy(uri, target)
            when {
                copied == CopyOutcome.COPIED -> files += StagedShareFile(target, name, mime)
                copied == CopyOutcome.TOO_LARGE -> {
                    target.delete()
                    failures += ShareStageFailure.TooLarge(name)
                }

                else -> {
                    target.delete()
                    failures += ShareStageFailure.Unreadable(name)
                }
            }
        }
        ShareStagingResult(token, files, failures)
    }

    fun shareUri(file: File): Uri? = runCatching {
        FileProvider.getUriForFile(appContext, "${appContext.packageName}.files", file)
    }.getOrNull()

    fun cleanup(token: String) {
        runCatching { File(root, token).deleteRecursively() }
    }

    fun messageFor(failure: ShareStageFailure): String = when (failure) {
        is ShareStageFailure.TooLarge -> messageContext.getString(R.string.share_error_too_large, failure.name)
        is ShareStageFailure.Unreadable -> messageContext.getString(R.string.share_error_unreadable, failure.name)
    }

    private data class Meta(val name: String, val mime: String, val size: Long)

    private fun queryMeta(uri: Uri): Meta? {
        var name: String? = null
        var size = -1L
        val queried = runCatching {
            resolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        }.isSuccess
        if (!queried) return null
        val resolvedName = name?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "shared"
        val mime = runCatching { resolver.getType(uri) }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: guessMime(resolvedName)
        return Meta(resolvedName, mime, size)
    }

    private fun copy(uri: Uri, target: File): CopyOutcome {
        target.parentFile?.mkdirs()
        val opened = runCatching { resolver.openInputStream(uri) }.getOrNull() ?: return CopyOutcome.FAILED
        return try {
            opened.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    var outcome = CopyOutcome.COPIED
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > FileAttachmentStore.MAX_BYTES) {
                            outcome = CopyOutcome.TOO_LARGE
                            break
                        }
                        output.write(buffer, 0, read)
                    }
                    outcome
                }
            }
        } catch (error: IOException) {
            CopyOutcome.FAILED
        } catch (error: SecurityException) {
            CopyOutcome.FAILED
        }
    }

    private fun pruneStale() {
        val cutoff = System.currentTimeMillis() - STALE_TOKEN_MS
        root.listFiles()?.forEach { child ->
            if (child.isDirectory && child.lastModified() < cutoff) {
                runCatching { child.deleteRecursively() }
            }
        }
    }

    private fun uniqueName(base: String, used: MutableSet<String>): String {
        var candidate = base
        var index = 2
        while (!used.add(candidate)) {
            candidate = if (base.contains('.')) {
                "${base.substringBeforeLast('.')}-$index.${base.substringAfterLast('.')}"
            } else {
                "$base-$index"
            }
            index++
        }
        return candidate
    }

    private enum class CopyOutcome {
        COPIED,
        TOO_LARGE,
        FAILED,
    }

    private companion object {
        const val DIRECTORY = "share-incoming"
        const val STALE_TOKEN_MS = 60L * 60L * 1000L
    }
}
