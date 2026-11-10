package io.github.whywood.deepdeep.data

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Paths
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface AttachmentImport {
    data class Imported(val ref: AttachmentRef) : AttachmentImport
    data object TooLarge : AttachmentImport
    data object InsufficientSpace : AttachmentImport
    data object UnsupportedImage : AttachmentImport
    data object Failed : AttachmentImport
}

interface AttachmentStore {
    suspend fun import(uri: Uri, root: File): AttachmentImport
    fun file(ref: AttachmentRef, root: File): File
    suspend fun materialize(ref: AttachmentRef, root: File): File?
    suspend fun delete(ref: AttachmentRef, root: File): Boolean
    suspend fun shareIntent(ref: AttachmentRef, root: File): Intent?
    suspend fun viewIntent(ref: AttachmentRef, root: File): Intent? = null
    suspend fun resolve(raw: String, root: File): AttachmentRef?
    fun available(): Boolean
}

class FileAttachmentStore(
    context: Context,
    private val home: File,
    private val hostFiles: HostFiles,
    private val sharedStorage: () -> Boolean = { isSharedStorageGranted() },
) : AttachmentStore {
    private val appContext = context.applicationContext

    private val externalRoot: File? = runCatching { sharedStorageDirectory().canonicalFile }.getOrNull()

    override fun available(): Boolean = hostFiles.available()

    @SuppressLint("UsableSpace")
    override suspend fun import(uri: Uri, root: File): AttachmentImport = withContext(Dispatchers.IO) {
        val resolver = appContext.contentResolver
        val meta = queryMeta(resolver, uri) ?: return@withContext AttachmentImport.Failed
        if (meta.size > MAX_BYTES) return@withContext AttachmentImport.TooLarge
        val name = sanitizeAttachmentName(meta.name)
        val id = UUID.randomUUID().toString().replace("-", "").take(12)
        val relativePath = "$ATTACHMENTS_DIRECTORY/${id}_$name"
        val target = File(root, relativePath)
        val usableSpace = runCatching { root.usableSpace }.getOrDefault(0L)
        if (meta.size > 0 && usableSpace > 0 && !hasSpaceForImport(meta.size, usableSpace)) {
            return@withContext AttachmentImport.InsufficientSpace
        }
        if (meta.mime.startsWith("image/") && !isSupportedImage(resolver, uri)) {
            return@withContext AttachmentImport.UnsupportedImage
        }
        val copied = runCatching {
            val input = resolver.openInputStream(uri) ?: return@withContext AttachmentImport.Failed
            input.use { hostFiles.write(target, it, MAX_BYTES) }
        }.getOrElse {
            hostFiles.delete(target)
            return@withContext AttachmentImport.Failed
        }
        if (!copied) return@withContext AttachmentImport.TooLarge
        AttachmentImport.Imported(
            AttachmentRef(
                id = id,
                name = name,
                mime = meta.mime,
                size = hostFiles.length(target),
                path = target.absolutePath,
                kind = attachmentKind(meta.mime, name),
            ),
        )
    }

    override fun file(ref: AttachmentRef, root: File): File = attachmentFile(ref, root)

    override suspend fun materialize(ref: AttachmentRef, root: File): File? = withContext(Dispatchers.IO) {
        val target = file(ref, root)
        if (target.isFile && target.canRead()) target else hostFiles.stage(target, ref.name)
    }

    override suspend fun delete(ref: AttachmentRef, root: File): Boolean = withContext(Dispatchers.IO) {
        val target = file(ref, root)
        if (hostFiles.delete(target)) true else runCatching { target.delete() }.getOrDefault(false)
    }

    override suspend fun resolve(raw: String, root: File): AttachmentRef? = withContext(Dispatchers.IO) {
        val trimmed = raw.trim().trim('<', '>', '"', '\'')
        if (trimmed.isEmpty()) return@withContext null
        val decoded = if (trimmed.startsWith("file://")) {
            runCatching { trimmed.toUri().path }.getOrNull()
        } else {
            trimmed.replace("%20", " ")
        } ?: return@withContext null
        val candidates = buildList {
            if (decoded.startsWith("/")) {
                if (decoded.startsWith("/workspace/")) {
                    val relative = decoded.removePrefix("/workspace/")
                    add(File(root, relative))
                    add(File(home, relative))
                } else {
                    add(File(decoded))
                    if (decoded.contains("/files/workspace/")) {
                        add(File(decoded.replace("/files/workspace/", "/files/home/")))
                    }
                }
            } else {
                val relative = decoded.removePrefix("workspace/")
                add(File(root, relative))
                if (root.absolutePath != home.absolutePath) add(File(home, relative))
            }
        }
        val roots = attachmentRoots(root, home, externalRoot, sharedStorage())
        val external = externalRoot
        val canReadSystemData = hostFiles.capabilities().canReadSystemData
        for (candidate in candidates) {
            val file = normalizedFile(candidate)
            val path = file.absolutePath
            val base = roots.firstOrNull { path == it.path || path.startsWith(it.path + File.separator) }
            if (base == null) {
                if (!canReadSystemData) continue
                if (external != null && path.startsWith(external.path + File.separator) && !sharedStorage()) continue
            } else if (!canReadSystemData && !file.canRead()) {
                continue
            }
            val metadata = hostFiles.metadata(file) ?: continue
            if (!metadata.isRegularFile) continue
            val relative = when {
                base == null -> path
                external != null && base.path == external.path -> path
                else -> file.relativeTo(base).path.replace(File.separatorChar, '/')
            }
            val mime = guessMime(file.name)
            return@withContext AttachmentRef(
                id = relative,
                name = file.name,
                mime = mime,
                size = metadata.size ?: 0L,
                path = path,
                kind = attachmentKind(mime, file.name),
            )
        }
        null
    }

    override suspend fun shareIntent(ref: AttachmentRef, root: File): Intent? = withContext(Dispatchers.IO) {
        val target = materialize(ref, root) ?: return@withContext null
        val uri = runCatching {
            FileProvider.getUriForFile(appContext, "${appContext.packageName}.files", target)
        }.getOrNull() ?: return@withContext null
        Intent(Intent.ACTION_SEND).apply {
            type = ref.mime.ifBlank { "application/octet-stream" }
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, ref.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    override suspend fun viewIntent(ref: AttachmentRef, root: File): Intent? = withContext(Dispatchers.IO) {
        val target = materialize(ref, root) ?: return@withContext null
        fileViewIntent(appContext, target, ref.mime)
    }

    private fun normalizedFile(file: File): File =
        hostFiles.canonical(file) ?: runCatching { Paths.get(file.absolutePath).normalize().toFile() }.getOrDefault(file)

    private fun isSupportedImage(resolver: ContentResolver, uri: Uri): Boolean {
        val input = runCatching { resolver.openInputStream(uri) }.getOrNull() ?: return false
        return runCatching {
            input.use {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(it, null, options)
                isSupportedImageSize(options.outWidth, options.outHeight)
            }
        }.getOrDefault(false)
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
        const val MAX_BYTES = 1024L * 1024 * 1024
        const val MAX_IMAGE_PIXELS = 64L * 1024 * 1024
        private const val ATTACHMENTS_DIRECTORY = "attachments"
    }
}

private const val IMPORT_SPACE_MARGIN_BYTES = 64L * 1024 * 1024

internal fun attachmentRoots(
    root: File,
    home: File,
    external: File?,
    sharedStorageEnabled: Boolean,
): List<File> = listOfNotNull(root, home, external?.takeIf { sharedStorageEnabled })
    .mapNotNull { runCatching { it.canonicalFile }.getOrNull() }

internal fun fileViewIntent(context: Context, file: File, mime: String): Intent? = runCatching {
    if (!file.isFile) return@runCatching null
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime.ifBlank { "application/octet-stream" })
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}.getOrNull()

internal fun hasSpaceForImport(fileSize: Long, usableSpace: Long): Boolean =
    fileSize + IMPORT_SPACE_MARGIN_BYTES <= usableSpace

internal fun isSupportedImageSize(width: Int, height: Int): Boolean =
    width > 0 && height > 0 && width.toLong() * height.toLong() <= FileAttachmentStore.MAX_IMAGE_PIXELS

internal fun copyWithLimit(input: InputStream, output: OutputStream, limit: Long): Boolean {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val read = input.read(buffer)
        if (read < 0) return true
        total += read
        if (total > limit) return false
        output.write(buffer, 0, read)
    }
}

internal fun writeAttachment(input: InputStream, target: File, limit: Long): Boolean {
    target.parentFile?.mkdirs()
    val copied = target.outputStream().use { output -> copyWithLimit(input, output, limit) }
    if (!copied) runCatching { target.delete() }
    return copied
}
