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
    suspend fun import(uri: Uri, root: File): AttachmentImport
    fun file(ref: AttachmentRef, root: File): File
    fun shareIntent(ref: AttachmentRef, root: File): Intent?
    fun resolve(raw: String, root: File): AttachmentRef?
}

class FileAttachmentStore(context: Context, private val home: File) : AttachmentStore {
    private val appContext = context.applicationContext

    override suspend fun import(uri: Uri, root: File): AttachmentImport = withContext(Dispatchers.IO) {
        val resolver = appContext.contentResolver
        val meta = queryMeta(resolver, uri) ?: return@withContext AttachmentImport.Failed
        if (meta.size > MAX_BYTES) return@withContext AttachmentImport.TooLarge
        val name = sanitizeAttachmentName(meta.name)
        val id = UUID.randomUUID().toString().replace("-", "").take(12)
        val relativePath = "$ATTACHMENTS_DIRECTORY/${id}_$name"
        val target = File(root, relativePath)
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

    override fun file(ref: AttachmentRef, root: File): File = File(root, ref.path)

    override fun resolve(raw: String, root: File): AttachmentRef? {
        val trimmed = raw.trim().trim('<', '>', '"', '\'')
        if (trimmed.isEmpty()) return null
        val decoded = if (trimmed.startsWith("file://")) {
            runCatching { Uri.parse(trimmed).path }.getOrNull()
        } else {
            trimmed.replace("%20", " ")
        } ?: return null
        val candidates = buildList {
            when {
                decoded.startsWith("workspace/") -> {
                    add(File(home, decoded.removePrefix("workspace/")))
                    add(File(root, decoded.removePrefix("workspace/")))
                }

                decoded.startsWith("$ATTACHMENTS_DIRECTORY/") || !decoded.contains('/') -> {
                    add(File(root, decoded))
                    if (root.absolutePath != home.absolutePath) add(File(home, decoded))
                }

                else -> {
                    add(File(decoded))
                    if (decoded.contains("/files/workspace/")) {
                        add(File(decoded.replace("/files/workspace/", "/files/home/")))
                    }
                }
            }
        }
        val roots = listOf(root, home).mapNotNull { runCatching { it.canonicalFile }.getOrNull() }
        for (candidate in candidates) {
            val canonical = runCatching { candidate.canonicalFile }.getOrNull() ?: continue
            if (roots.none { canonical.path.startsWith(it.path + File.separator) }) continue
            if (!canonical.isFile) continue
            val base = roots.firstOrNull { canonical.path.startsWith(it.path + File.separator) } ?: continue
            val relative = canonical.relativeTo(base).path.replace(File.separatorChar, '/')
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
        return null
    }

    override fun shareIntent(ref: AttachmentRef, root: File): Intent? {
        val target = file(ref, root)
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
