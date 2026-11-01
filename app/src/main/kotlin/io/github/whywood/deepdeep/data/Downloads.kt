package io.github.whywood.deepdeep.data

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import java.io.File

fun saveFileToDownloads(context: Context, file: File, mime: String?): Boolean = runCatching {
    if (!file.isFile) return false
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, file.name)
        put(MediaStore.Downloads.MIME_TYPE, mime?.takeIf { it.isNotBlank() } ?: guessMime(file.name))
        put(MediaStore.Downloads.IS_PENDING, 1)
        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
    }
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
    val copied = runCatching {
        resolver.openOutputStream(uri)?.use { output ->
            file.inputStream().use { input -> input.copyTo(output) }
        } != null
    }.getOrDefault(false)
    values.clear()
    values.put(MediaStore.Downloads.IS_PENDING, 0)
    resolver.update(uri, values, null, null)
    copied
}.getOrDefault(false)
