package io.github.whywood.deepdeep.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BotAvatarStore(
    context: Context,
    private val directory: File = File(context.applicationContext.filesDir, "bot-avatars"),
) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    init {
        directory.mkdirs()
    }

    suspend fun save(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = decode(uri) ?: return@runCatching null
            val target = File(directory, UUID.randomUUID().toString().replace("-", "") + ".jpg")
            FileOutputStream(target).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)
            }
            bitmap.recycle()
            target.absolutePath
        }.getOrNull()
    }

    fun delete(path: String?) {
        val file = path?.takeIf { it.isNotBlank() }?.let(::File) ?: return
        val parent = file.parentFile?.absolutePath ?: return
        if (parent == directory.absolutePath) file.delete()
    }

    fun cleanup(validPaths: Collection<String>) {
        val valid = validPaths.toSet()
        if (valid.isEmpty()) return
        directory.listFiles()?.forEach { file ->
            if (file.absolutePath !in valid) file.delete()
        }
    }

    private fun decode(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_AVATAR_PX) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = runCatching {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }.getOrNull() ?: return null
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= MAX_AVATAR_PX) return bitmap
        val scale = MAX_AVATAR_PX.toFloat() / longest
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    private companion object {
        const val MAX_AVATAR_PX = 256
    }
}
