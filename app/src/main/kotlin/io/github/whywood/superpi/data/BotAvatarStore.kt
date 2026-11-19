package io.github.whywood.superpi.data

import android.content.Context
import android.graphics.Bitmap
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
    init {
        directory.mkdirs()
    }

    suspend fun save(bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        val source = fit(bitmap)
        try {
            runCatching {
                val target = File(directory, UUID.randomUUID().toString().replace("-", "") + ".jpg")
                FileOutputStream(target).use { output ->
                    source.compress(Bitmap.CompressFormat.JPEG, 88, output)
                }
                target.absolutePath
            }.getOrNull()
        } finally {
            if (source !== bitmap) source.recycle()
        }
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

    private fun fit(bitmap: Bitmap): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= MAX_AVATAR_PX) return bitmap
        val scale = MAX_AVATAR_PX.toFloat() / longest
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private companion object {
        const val MAX_AVATAR_PX = 256
    }
}
