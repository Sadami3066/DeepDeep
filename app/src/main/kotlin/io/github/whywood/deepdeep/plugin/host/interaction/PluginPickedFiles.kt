package io.github.whywood.deepdeep.plugin.host.interaction

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import io.github.whywood.deepdeep.plugin.api.PluginPickedFile
import java.io.File
import java.io.IOException

internal const val PluginPickedMaxBytes: Long = 512L * 1024L * 1024L

internal object PluginPickedFiles {
    fun import(context: Context, pluginId: String, uri: Uri, preferredName: String? = null): PluginPickedFile? {
        val resolver = context.contentResolver
        val displayName = preferredName?.takeIf { it.isNotBlank() }
            ?: queryDisplayName(resolver, uri)
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "file"
        val declaredSize = querySize(resolver, uri)
        if (declaredSize != null && declaredSize > PluginPickedMaxBytes) return null
        val directory = pickedDirectory(context, pluginId)
        val target = File(directory, "${System.currentTimeMillis()}-$displayName".sanitizePickedName())
        val copied = try {
            copy(uri, resolver, target)
        } catch (_: Exception) {
            target.delete()
            return null
        }
        if (copied <= 0L) {
            target.delete()
            return null
        }
        return PluginPickedFile(
            path = target.absolutePath,
            uri = uri.toString(),
            name = displayName,
            sizeBytes = copied,
            mimeType = runCatching { resolver.getType(uri) }.getOrNull(),
        )
    }

    fun resolveDirectoryPath(context: Context, uri: Uri): String? {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
        return when (uri.authority.orEmpty()) {
            DownloadsAuthority -> resolveDownloadsDirectory(documentId)
            else -> resolveExternalStorageDirectory(documentId)
        }
    }

    private fun copy(uri: Uri, resolver: ContentResolver, target: File): Long {
        val input = resolver.openInputStream(uri) ?: throw IOException("cannot open picked uri")
        return input.use { source ->
            target.outputStream().use { sink ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > PluginPickedMaxBytes) throw IOException("picked file is too large")
                    sink.write(buffer, 0, read)
                }
                total
            }
        }
    }

    private fun pickedDirectory(context: Context, pluginId: String): File {
        val root = File(File(context.filesDir, "plugins"), pluginId.sanitizePluginId())
        val directory = File(File(root, "data"), "picked").apply { mkdirs() }
        prune(directory)
        return directory
    }

    private fun prune(directory: File) {
        val files = directory.listFiles().orEmpty().filter { it.isFile }
        var total = files.sumOf { it.length() }
        if (total <= PickedDirectoryMaxBytes) return
        files.sortedBy { it.lastModified() }.forEach { file ->
            if (total <= PickedDirectoryMaxBytes) return
            total -= file.length()
            file.delete()
        }
    }

    private fun resolveExternalStorageDirectory(documentId: String): String? {
        val separator = documentId.indexOf(':')
        if (separator <= 0) return null
        val volume = documentId.substring(0, separator)
        val relative = documentId.substring(separator + 1)
        if (volume == "raw") {
            return File(relative).takeIf { it.isDirectory && it.canRead() }?.absolutePath
        }
        val base = when (volume) {
            "primary" -> Environment.getExternalStorageDirectory()
            "home" -> File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_DOCUMENTS)
            else -> File("/storage", volume)
        }
        val directory = if (relative.isBlank()) base else File(base, relative)
        return directory.takeIf { it.isDirectory && it.canRead() }?.absolutePath
    }

    private fun resolveDownloadsDirectory(documentId: String): String? {
        if (documentId.startsWith("raw:")) {
            return File(documentId.removePrefix("raw:"))
                .takeIf { it.isDirectory && it.canRead() }
                ?.absolutePath
        }
        val downloads = File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_DOWNLOADS)
        return downloads.takeIf { it.isDirectory && it.canRead() }?.absolutePath
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
        }
    }.getOrNull()

    private fun querySize(resolver: ContentResolver, uri: Uri): Long? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
    }.getOrNull()

    private fun String.sanitizePickedName(): String {
        val cleaned = map { char ->
            if (char.isLetterOrDigit() || char == '.' || char == '-' || char == '_') char else '_'
        }.joinToString("").trim('.', '_').take(96)
        return cleaned.ifEmpty { "file" }
    }

    private fun String.sanitizePluginId(): String =
        map { char -> if (char.isLetterOrDigit() || char == '.' || char == '-' || char == '_') char else '_' }
            .joinToString("")

    private const val DownloadsAuthority = "com.android.providers.downloads.documents"
    private const val PickedDirectoryMaxBytes = 1024L * 1024L * 1024L
}
