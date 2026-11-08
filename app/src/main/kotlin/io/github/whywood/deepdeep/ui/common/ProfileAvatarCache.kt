package io.github.whywood.deepdeep.ui.common

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object ProfileAvatarCache {
    private const val MAX_BYTES = 8 * 1024 * 1024
    private val cache = object : LruCache<String, ImageBitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    fun cached(key: String?): ImageBitmap? = key?.let { synchronized(cache) { cache.get(it) } }

    suspend fun load(key: String, decode: () -> Bitmap?): ImageBitmap? {
        cached(key)?.let { return it }
        val bitmap = withContext(Dispatchers.IO) {
            runCatching { decode()?.asImageBitmap() }.getOrNull()
        } ?: return null
        synchronized(cache) { cache.put(key, bitmap) }
        return bitmap
    }
}

@Composable
internal fun rememberAvatarBitmap(key: String?, decode: () -> Bitmap?): State<ImageBitmap?> =
    produceState(initialValue = ProfileAvatarCache.cached(key), key) {
        value = key?.let { ProfileAvatarCache.load(it) { decode() } }
    }
