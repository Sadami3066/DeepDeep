package io.github.whywood.deepdeep.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileAvatarStoreDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun savesPickedImageAndLoadsIt() = runBlocking {
        val base = File(context.cacheDir, "profile-avatar-saved").apply { mkdirs() }
        val source = File(context.cacheDir, "profile-avatar-source.jpg")
        val bitmap = Bitmap.createBitmap(900, 600, Bitmap.Config.ARGB_8888)
        try {
            Canvas(bitmap).drawColor(Color.rgb(32, 96, 200))
            source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        } finally {
            bitmap.recycle()
        }
        val store = ProfileAvatarStore(context, base)
        try {
            assertTrue(store.save(Uri.fromFile(source)))
            assertNotNull(store.current())
            val loaded = store.load()
            assertNotNull(loaded)
            assertEquals(512, loaded!!.width)
            assertEquals(512, loaded.height)
            loaded.recycle()
        } finally {
            source.delete()
            base.deleteRecursively()
        }
    }

    @Test
    fun rejectsCorruptedImage() = runBlocking {
        val base = File(context.cacheDir, "profile-avatar-corrupted").apply { mkdirs() }
        val source = File(context.cacheDir, "profile-avatar-broken.jpg")
        source.writeBytes(ByteArray(512) { (it * 17).toByte() })
        val store = ProfileAvatarStore(context, base)
        try {
            assertFalse(store.save(Uri.fromFile(source)))
            assertNull(store.current())
        } finally {
            source.delete()
            base.deleteRecursively()
        }
    }
}
