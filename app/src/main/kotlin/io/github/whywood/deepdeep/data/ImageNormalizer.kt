package io.github.whywood.deepdeep.data

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class NormalizedImage(
    val data: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
)

@SuppressLint("ExifInterface")
object ImageNormalizer {
    const val MAX_EDGE = ImageScaling.MAX_EDGE
    const val MAX_BASE64_LENGTH = 1_500_000
    private const val MAX_PIXELS = 64L * 1024L * 1024L
    private val JPEG_QUALITIES = intArrayOf(85, 75, 65, 55, 45)

    suspend fun normalize(file: File, mime: String): NormalizedImage? = withContext(Dispatchers.IO) {
        if (!mime.startsWith("image/")) return@withContext null
        try {
            normalizeBlocking(file)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
    }

    private fun normalizeBlocking(file: File): NormalizedImage? {
        if (!file.isFile) return null
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        val sourceWidth = options.outWidth
        val sourceHeight = options.outHeight
        if (sourceWidth <= 0 || sourceHeight <= 0) return null
        if (sourceWidth.toLong() * sourceHeight.toLong() > MAX_PIXELS) return null
        options.inJustDecodeBounds = false
        options.inSampleSize = ImageScaling.sampleSizeFor(sourceWidth, sourceHeight)
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        val decoded = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        val oriented = applyOrientation(decoded, readOrientation(file))
        if (oriented !== decoded) decoded.recycle()
        val target = ImageScaling.scaledSizeFor(oriented.width, oriented.height)
        val output = if (target.width == oriented.width && target.height == oriented.height) {
            oriented
        } else {
            val scaled = Bitmap.createScaledBitmap(oriented, target.width, target.height, true)
            if (scaled !== oriented) oriented.recycle()
            scaled
        }
        return encode(output).also { output.recycle() }
    }

    private fun readOrientation(file: File): Int = runCatching {
        ExifInterface(file.absolutePath)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
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

    private fun encode(bitmap: Bitmap): NormalizedImage? {
        if (bitmap.hasAlpha()) {
            val encoded = base64(compress(bitmap, Bitmap.CompressFormat.PNG, 100))
            if (encoded.length <= MAX_BASE64_LENGTH) {
                return NormalizedImage(encoded, "image/png", bitmap.width, bitmap.height)
            }
        }
        val opaque = if (bitmap.hasAlpha()) flatten(bitmap) else bitmap
        try {
            for (quality in JPEG_QUALITIES) {
                val encoded = base64(compress(opaque, Bitmap.CompressFormat.JPEG, quality))
                if (encoded.length <= MAX_BASE64_LENGTH) {
                    return NormalizedImage(encoded, "image/jpeg", opaque.width, opaque.height)
                }
            }
            return null
        } finally {
            if (opaque !== bitmap) opaque.recycle()
        }
    }

    private fun flatten(bitmap: Bitmap): Bitmap {
        val output = androidx.core.graphics.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(output).apply {
            drawColor(Color.WHITE)
            drawBitmap(bitmap, 0f, 0f, null)
        }
        return output
    }

    private fun compress(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int): ByteArray =
        ByteArrayOutputStream().use { output ->
            bitmap.compress(format, quality, output)
            output.toByteArray()
        }

    private fun base64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
}
