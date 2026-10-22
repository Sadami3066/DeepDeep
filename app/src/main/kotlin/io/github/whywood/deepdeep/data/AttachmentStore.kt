package io.github.whywood.deepdeep.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface AttachmentImport {
    data class Imported(val ref: AttachmentRef) : AttachmentImport
    data object TooLarge : AttachmentImport
    data object Failed : AttachmentImport
}

interface AttachmentStore {
    suspend fun import(uri: Uri): AttachmentImport
    fun file(ref: AttachmentRef): File
    fun shareIntent(ref: AttachmentRef): Intent?
    fun resolve(raw: String): AttachmentRef?
}

class FileAttachmentStore(context: Context) : AttachmentStore {
    private val appContext = context.applicationContext
    private val workspace = File(appContext.filesDir, "workspace")
    private val directory = File(workspace, ATTACHMENTS_DIRECTORY)

    override suspend fun import(uri: Uri): AttachmentImport = withContext(Dispatchers.IO) {
        val resolver = appContext.contentResolver
        val meta = queryMeta(resolver, uri) ?: return@withContext AttachmentImport.Failed
        if (meta.size > MAX_BYTES) return@withContext AttachmentImport.TooLarge
        val name = sanitizeAttachmentName(meta.name)
        val id = UUID.randomUUID().toString().replace("-", "").take(12)
        val relativePath = "$ATTACHMENTS_DIRECTORY/${id}_$name"
        val target = File(workspace, relativePath)
        target.parentFile?.mkdirs()
        val copied = runCatching {
            resolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext AttachmentImport.Failed
        }.isSuccess
        if (!copied) {
            runCatching { target.delete() }
            return@withContext AttachmentImport.Failed
        }
        AttachmentImport.Imported(
            AttachmentRef(
                id = id,
                name = name,
                mime = meta.mime,
                size = target.length(),
                path = relativePath,
                kind = attachmentKind(meta.mime, name),
            ),
        )
    }

    override fun file(ref: AttachmentRef): File = File(workspace, ref.path)

    override fun resolve(raw: String): AttachmentRef? {
        val trimmed = raw.trim().trim('<', '>', '"', '\'')
        if (trimmed.isEmpty()) return null
        val decoded = if (trimmed.startsWith("file://")) {
            runCatching { Uri.parse(trimmed).path }.getOrNull()
        } else {
            trimmed.replace("%20", " ")
        } ?: return null
        val candidate = when {
            decoded.startsWith(workspace.absolutePath) -> File(decoded)
            decoded.contains("/files/workspace/") -> File(decoded)
            decoded.startsWith("workspace/") -> File(workspace, decoded.removePrefix("workspace/"))
            else -> File(workspace, decoded)
        }
        val canonical = runCatching { candidate.canonicalFile }.getOrNull() ?: return null
        val root = runCatching { workspace.canonicalFile }.getOrNull() ?: return null
        if (!canonical.path.startsWith(root.path + File.separator)) return null
        if (!canonical.isFile) return null
        val relative = canonical.relativeTo(root).path.replace(File.separatorChar, '/')
        val mime = guessMime(canonical.name)
        return AttachmentRef(
            id = relative,
            name = canonical.name,
            mime = mime,
            size = canonical.length(),
            path = relative,
            kind = attachmentKind(mime, canonical.name),
        )
    }

    override fun shareIntent(ref: AttachmentRef): Intent? {
        val target = file(ref)
        if (!target.isFile) return null
        val uri = runCatching {
            FileProvider.getUriForFile(appContext, "${appContext.packageName}.files", target)
        }.getOrNull() ?: return null
        return Intent(Intent.ACTION_SEND).apply {
            type = ref.mime.ifBlank { "application/octet-stream" }
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, ref.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private data class Meta(val name: String, val mime: String, val size: Long)

    private fun queryMeta(resolver: ContentResolver, uri: Uri): Meta? {
        var name: String? = null
        var size = -1L
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                        if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                    }
                }
        }
        val fallback = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "attachment"
        val resolvedName = name?.takeIf { it.isNotBlank() } ?: fallback
        val mime = runCatching { resolver.getType(uri) }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: guessMime(resolvedName)
        return Meta(resolvedName, mime, size)
    }

    companion object {
        const val MAX_BYTES = 25L * 1024 * 1024
        private const val ATTACHMENTS_DIRECTORY = "attachments"
    }
}
