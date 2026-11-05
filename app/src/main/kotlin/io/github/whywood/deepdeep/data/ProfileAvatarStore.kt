package io.github.whywood.deepdeep.data

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow

class ProfileAvatarStore(context: Context) {
    private val appContext = context.applicationContext
    private val directory = File(appContext.filesDir, DIRECTORY)

    val file: File get() = File(directory, FILE_NAME)

    fun current(): File? = file.takeIf { it.isFile }

    fun load(): Bitmap? = file.takeIf { it.isFile }?.let { stored ->
        runCatching { BitmapFactory.decodeFile(stored.absolutePath) }.getOrNull()
    }

    fun save(uri: Uri): Boolean {
        val bitmap = decode(uri) ?: return false
        return try {
            directory.mkdirs()
            val temp = File(directory, "$FILE_NAME.tmp")
            temp.outputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream) }
            if (temp.renameTo(file)) {
                revision.value = revision.value + 1
                true
            } else {
                temp.delete()
                false
            }
        } catch (_: IOException) {
            false
        } finally {
            bitmap.recycle()
        }
    }

    fun clear(): Boolean {
        val removed = !file.exists() || file.delete()
        if (removed) revision.value = revision.value + 1
        return removed
    }

    private fun decode(uri: Uri): Bitmap? {
        val resolver = appContext.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            resolver.openInputStream(uri)?.use { stream -> BitmapFactory.decodeStream(stream, null, bounds) }
        }.getOrNull() ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = runCatching {
            resolver.openInputStream(uri)?.use { stream -> BitmapFactory.decodeStream(stream, null, options) }
        }.getOrNull() ?: return null
        val oriented = applyOrientation(decoded, readOrientation(uri))
        if (oriented !== decoded) decoded.recycle()
        val square = centerCrop(oriented)
        if (square !== oriented) oriented.recycle()
        if (square.width <= MAX_EDGE) return square
        val scaled = Bitmap.createScaledBitmap(square, MAX_EDGE, MAX_EDGE, true)
        if (scaled !== square) square.recycle()
        return scaled
    }

    @SuppressLint("ExifInterface")
    private fun readOrientation(uri: Uri): Int = runCatching {
        appContext.contentResolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180f)
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun centerCrop(bitmap: Bitmap): Bitmap {
        val size = minOf(bitmap.width, bitmap.height)
        if (size == bitmap.width && size == bitmap.height) return bitmap
        return Bitmap.createBitmap(bitmap, (bitmap.width - size) / 2, (bitmap.height - size) / 2, size, size)
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (width / sample > DECODE_EDGE || height / sample > DECODE_EDGE) sample *= 2
        return sample
    }

    companion object {
        val revision = MutableStateFlow(0L)

        private const val DIRECTORY = "profile"
        private const val FILE_NAME = "avatar.jpg"
        private const val MAX_EDGE = 512
        private const val DECODE_EDGE = 1024
    }
}
