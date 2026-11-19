package io.github.whywood.superpi.ui.miniapp

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.webkit.MimeTypeMap
import io.github.whywood.superpi.runtime.MiniappFileEntry
import io.github.whywood.superpi.runtime.MiniappFileRead
import io.github.whywood.superpi.runtime.MiniappFileRef
import io.github.whywood.superpi.runtime.MiniappHostException
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_FILE_BYTES = 16L * 1024 * 1024
private const val MAX_READ_BYTES = 16L * 1024 * 1024
private const val MAX_TOTAL_BYTES = 100L * 1024 * 1024
private const val MAX_ENTRIES = 500
private const val MAX_PATH_CHARS = 512

internal class MiniappFileStore(
    private val resolver: ContentResolver,
    root: File,
) {
    val root: File = root.absoluteFile

    fun ensureRoot() {
        if (!root.isDirectory && !root.mkdirs() && !root.isDirectory) {
            throw MiniappHostException(MiniappHostException.FAILED, "cannot create miniapp file folder")
        }
    }

    fun resolve(path: String): File {
        val relative = normalize(path)
        ensureRoot()
        val canonicalRoot = root.canonicalFile
        val file = File(canonicalRoot, relative).canonicalFile
        if (!file.toPath().startsWith(canonicalRoot.toPath())) {
            throw MiniappHostException(MiniappHostException.INVALID_PARAMS, "path leaves the miniapp folder")
        }
        return file
    }

    fun relative(file: File): String = file.canonicalFile.relativeTo(root.canonicalFile).path.replace(File.separatorChar, '/')

    fun url(path: String): String {
        resolve(path)
        val encoded = normalize(path).split('/').joinToString("/") { segment ->
            URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
        }
        return "/__files/$encoded"
    }

    fun mimeType(name: String): String {
        val extension = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
    }

    suspend fun import(uri: Uri, preferredName: String? = null): MiniappFileRef = withContext(Dispatchers.IO) {
        val name = safeName(preferredName ?: displayName(uri) ?: "file")
        val target = uniqueTarget(name)
        val written = runCatching {
            resolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output ->
                    copyLimited(input, output, MAX_FILE_BYTES)
                }
            } ?: throw IOException("cannot open selected file")
        }.getOrElse { error ->
            target.delete()
            if (error is MiniappHostException) throw error
            throw MiniappHostException(MiniappHostException.FAILED, error.message ?: "cannot import file")
        }
        checkQuota(written)
        fileRef(target, written)
    }

    suspend fun write(path: String, encoding: String, data: String): MiniappFileRef = withContext(Dispatchers.IO) {
        val target = resolve(path)
        if (target.isDirectory) {
            throw MiniappHostException(MiniappHostException.INVALID_PARAMS, "path is a folder")
        }
        val bytes = if (encoding == "base64") decodeBase64(data) else data.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_FILE_BYTES) {
            throw MiniappHostException(MiniappHostException.TOO_LARGE, "file is too large")
        }
        target.parentFile?.let { parent ->
            if (!parent.isDirectory && !parent.mkdirs() && !parent.isDirectory) {
                throw MiniappHostException(MiniappHostException.FAILED, "cannot create file folder")
            }
        }
        runCatching { target.writeBytes(bytes) }.getOrElse { error ->
            throw MiniappHostException(MiniappHostException.FAILED, error.message ?: "cannot write file")
        }
        checkQuota(target.length())
        fileRef(target, target.length())
    }

    suspend fun read(path: String, encoding: String): MiniappFileRead = withContext(Dispatchers.IO) {
        val target = resolve(path)
        if (!target.isFile) {
            throw MiniappHostException(MiniappHostException.NOT_FOUND, "file not found")
        }
        val size = target.length()
        if (size > MAX_READ_BYTES) {
            throw MiniappHostException(MiniappHostException.TOO_LARGE, "file is too large to read")
        }
        val bytes = runCatching { target.readBytes() }.getOrElse { error ->
            throw MiniappHostException(MiniappHostException.FAILED, error.message ?: "cannot read file")
        }
        val mimeType = mimeType(target.name)
        if (encoding == "base64") {
            MiniappFileRead(
                path = relative(target),
                size = size,
                mimeType = mimeType,
                text = null,
                base64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
            )
        } else {
            MiniappFileRead(
                path = relative(target),
                size = size,
                mimeType = mimeType,
                text = bytes.toString(Charsets.UTF_8),
                base64 = null,
            )
        }
    }

    suspend fun list(directory: String): List<MiniappFileEntry> = withContext(Dispatchers.IO) {
        val dir = if (directory.isBlank()) root.canonicalFile else resolve(directory)
        if (!dir.isDirectory) {
            if (directory.isBlank()) return@withContext emptyList()
            throw MiniappHostException(MiniappHostException.NOT_FOUND, "folder not found")
        }
        (dir.listFiles() ?: emptyArray())
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            .take(MAX_ENTRIES)
            .map { file ->
                MiniappFileEntry(
                    name = file.name,
                    path = relative(file),
                    size = if (file.isDirectory) 0L else file.length(),
                    directory = file.isDirectory,
                )
            }
    }

    suspend fun delete(path: String): Boolean = withContext(Dispatchers.IO) {
        val target = resolve(path)
        if (!target.exists()) return@withContext false
        if (target.isDirectory) target.deleteRecursively() else target.delete()
    }

    suspend fun export(path: String, open: suspend (name: String, mimeType: String) -> Uri?): Boolean =
        withContext(Dispatchers.IO) {
            val target = resolve(path)
            if (!target.isFile) {
                throw MiniappHostException(MiniappHostException.NOT_FOUND, "file not found")
            }
            val uri = open(target.name, mimeType(target.name)) ?: return@withContext false
            runCatching {
                resolver.openOutputStream(uri)?.use { output ->
                    target.inputStream().use { input -> input.copyTo(output) }
                } ?: throw IOException("cannot open export target")
            }.getOrElse { error ->
                throw MiniappHostException(MiniappHostException.FAILED, error.message ?: "cannot export file")
            }
            true
        }

    fun fileRef(file: File, size: Long): MiniappFileRef = MiniappFileRef(
        name = file.name,
        path = relative(file),
        size = size,
        mimeType = mimeType(file.name),
    )

    private fun normalize(path: String): String {
        val trimmed = path.trim().replace('\\', '/')
        if (trimmed.isEmpty()) {
            throw MiniappHostException(MiniappHostException.INVALID_PARAMS, "path is required")
        }
        if (trimmed.length > MAX_PATH_CHARS) {
            throw MiniappHostException(MiniappHostException.INVALID_PARAMS, "path is too long")
        }
        if (trimmed.startsWith("/")) {
            throw MiniappHostException(MiniappHostException.INVALID_PARAMS, "path must be relative")
        }
        if (trimmed.split('/').any { it == ".." || it == "." }) {
            throw MiniappHostException(MiniappHostException.INVALID_PARAMS, "path must stay inside the miniapp folder")
        }
        return trimmed
    }

    private fun safeName(raw: String): String {
        val name = raw.substringAfterLast('/').trim().replace(Regex("[\\u0000-\\u001f]"), "")
        if (name.isEmpty() || name == "." || name == "..") return "file"
        return name.take(120)
    }

    private fun uniqueTarget(name: String): File {
        ensureRoot()
        val base = name.substringBeforeLast('.', name)
        val extension = name.substringAfterLast('.', "")
        var candidate = File(root, name)
        var index = 1
        while (candidate.exists() && index < 1000) {
            val nextName = if (extension.isEmpty()) "$base-$index" else "$base-$index.$extension"
            candidate = File(root, nextName)
            index += 1
        }
        return candidate
    }

    private fun displayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column < 0) null else cursor.getString(column)
        }
    }.getOrNull()

    private fun copyLimited(input: java.io.InputStream, output: java.io.OutputStream, limit: Long): Long {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) {
                throw MiniappHostException(MiniappHostException.TOO_LARGE, "file is too large")
            }
            output.write(buffer, 0, read)
        }
        return total
    }

    private fun checkQuota(lastWritten: Long) {
        var total = 0L
        var files = 0
        root.walkTopDown().forEach { file ->
            if (file.isFile) {
                total += file.length()
                files += 1
            }
        }
        if (total > MAX_TOTAL_BYTES) {
            throw MiniappHostException(MiniappHostException.TOO_LARGE, "miniapp file storage is full")
        }
        if (files > MAX_ENTRIES) {
            throw MiniappHostException(MiniappHostException.TOO_LARGE, "too many files")
        }
        if (lastWritten > MAX_FILE_BYTES) {
            throw MiniappHostException(MiniappHostException.TOO_LARGE, "file is too large")
        }
    }

    private fun decodeBase64(data: String): ByteArray = runCatching {
        Base64.decode(data, Base64.DEFAULT)
    }.getOrElse {
        throw MiniappHostException(MiniappHostException.INVALID_PARAMS, "data is not valid base64")
    }
}
