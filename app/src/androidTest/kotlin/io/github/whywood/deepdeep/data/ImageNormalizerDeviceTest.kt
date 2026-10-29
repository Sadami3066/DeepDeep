package io.github.whywood.deepdeep.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageNormalizerDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun downsamplesLargeJpegWithinBudget() = runBlocking {
        val source = File(context.cacheDir, "image-normalizer-source.jpg")
        val photo = Bitmap.createBitmap(4_000, 3_000, Bitmap.Config.RGB_565)
        try {
            val paint = Paint().apply {
                shader = LinearGradient(
                    0f,
                    0f,
                    photo.width.toFloat(),
                    photo.height.toFloat(),
                    Color.RED,
                    Color.BLUE,
                    Shader.TileMode.MIRROR,
                )
            }
            Canvas(photo).drawRect(0f, 0f, photo.width.toFloat(), photo.height.toFloat(), paint)
            source.outputStream().use { photo.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        } finally {
            photo.recycle()
        }
        try {
            val normalized = ImageNormalizer.normalize(source, "image/jpeg")
            assertNotNull(normalized)
            val image = normalized!!
            assertEquals("image/jpeg", image.mimeType)
            assertTrue(maxOf(image.width, image.height) <= ImageNormalizer.MAX_EDGE)
            assertTrue(image.data.length <= ImageNormalizer.MAX_BASE64_LENGTH)
            val bytes = Base64.getDecoder().decode(image.data)
            assertEquals(0xFF.toByte(), bytes[0])
            assertEquals(0xD8.toByte(), bytes[1])
        } finally {
            source.delete()
        }
    }

    @Test
    fun rejectsCorruptedBytes() = runBlocking {
        val source = File(context.cacheDir, "image-normalizer-corrupted.jpg")
        source.writeBytes(ByteArray(4_096) { (it * 31).toByte() })
        try {
            assertNull(ImageNormalizer.normalize(source, "image/jpeg"))
        } finally {
            source.delete()
        }
    }
}
