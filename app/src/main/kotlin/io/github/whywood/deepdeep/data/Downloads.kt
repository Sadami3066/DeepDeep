package io.github.whywood.deepdeep.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.InputStream

fun saveFileToDownloads(context: Context, file: File, mime: String?): Boolean = runCatching {
    if (!file.isFile) return false
    saveStreamToDownloads(context, file.name, mime) { file.inputStream() } != null
}.getOrDefault(false)

fun saveStreamToDownloads(
    context: Context,
    name: String,
    mime: String?,
    open: () -> InputStream?,
): Uri? = runCatching {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, name)
        put(MediaStore.Downloads.MIME_TYPE, mime?.takeIf { it.isNotBlank() } ?: guessMime(name))
        put(MediaStore.Downloads.IS_PENDING, 1)
        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
    }
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@runCatching null
    val copied = runCatching {
        val input = open() ?: return@runCatching false
        input.use { source ->
            resolver.openOutputStream(uri)?.use { output -> source.copyTo(output) } != null
        }
    }.getOrDefault(false)
    if (!copied) {
        runCatching { resolver.delete(uri, null, null) }
        return@runCatching null
    }
    values.clear()
    values.put(MediaStore.Downloads.IS_PENDING, 0)
    resolver.update(uri, values, null, null)
    uri
}.getOrNull()
