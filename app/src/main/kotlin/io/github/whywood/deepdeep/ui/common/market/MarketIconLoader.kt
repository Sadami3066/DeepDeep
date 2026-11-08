package io.github.whywood.deepdeep.ui.common.market

import android.content.Context
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.market.MarketDefaults
import io.github.whywood.deepdeep.market.MarketEntry
import io.github.whywood.deepdeep.market.MarketEntryType
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

internal class MarketIconLoader(
    context: Context,
    private val http: OkHttpClient = defaultHttpClient(),
) {
    private val dir = File(File(context.applicationContext.filesDir, MarketDefaults.MARKET_DIR), "icons")
    private val cache = object : LruCache<String, ImageBitmap>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }
    private val failedAt = ConcurrentHashMap<String, Long>()
    private val inFlight = ConcurrentHashMap<String, Deferred<ImageBitmap?>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun cached(icon: String?): ImageBitmap? = marketIconUrl(icon)?.let { url ->
        synchronized(cache) { cache.get(url) }
    }

    suspend fun load(icon: String?): ImageBitmap? {
        val url = marketIconUrl(icon) ?: return null
        synchronized(cache) { cache.get(url) }?.let { return it }
        failedAt[url]?.let { failedTime ->
            if (SystemClock.elapsedRealtime() - failedTime < FAIL_RETRY_MS) return null
            failedAt.remove(url)
        }
        val request = inFlight.computeIfAbsent(url) { scope.async { fetch(url) } }
        return try {
            request.await()
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            throw cancel
        } catch (_: Exception) {
            null
        } finally {
            inFlight.remove(url, request)
        }
    }

    private suspend fun fetch(url: String): ImageBitmap? {
        val bitmap = withContext(Dispatchers.IO) {
            runCatching { readFile(url) ?: download(url) }.getOrNull()
        }
        if (bitmap != null) {
            synchronized(cache) { cache.put(url, bitmap) }
            failedAt.remove(url)
        } else {
            failedAt[url] = SystemClock.elapsedRealtime()
        }
        return bitmap
    }

    private fun readFile(url: String): ImageBitmap? {
        val file = fileFor(url)
        if (!file.isFile || file.length() > MAX_ICON_BYTES) return null
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        return decode(bytes)
    }

    private fun download(url: String): ImageBitmap? = runCatching {
        val request = Request.Builder().url(url).get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val body = response.body
            if (body.contentLength() > MAX_ICON_BYTES) return@use null
            val bytes = body.bytes()
            if (bytes.size > MAX_ICON_BYTES) return@use null
            val bitmap = decode(bytes) ?: return@use null
            dir.mkdirs()
            runCatching { fileFor(url).writeBytes(bytes) }
            bitmap
        }
    }.getOrNull()

    private fun decode(bytes: ByteArray): ImageBitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= TARGET_PX && bounds.outHeight / (sampleSize * 2) >= TARGET_PX) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    }.getOrNull()

    private fun fileFor(url: String): File = File(dir, sha256(url))

    companion object {
        private const val MAX_CACHE_BYTES = 4 * 1024 * 1024
        private const val MAX_ICON_BYTES = 512 * 1024L
        private const val TARGET_PX = 192
        private const val FAIL_RETRY_MS = 5 * 60 * 1000L

        @Volatile private var shared: MarketIconLoader? = null

        fun shared(context: Context): MarketIconLoader =
            shared ?: synchronized(this) {
                shared ?: MarketIconLoader(context.applicationContext).also { shared = it }
            }

        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}

@Composable
internal fun rememberMarketIconLoader(): MarketIconLoader {
    val context = LocalContext.current.applicationContext
    return remember(context) { MarketIconLoader.shared(context) }
}

internal fun marketPlaceholderIcon(type: MarketEntryType): DdIcon = when (type) {
    MarketEntryType.Skill -> DdIcons.Sparkles
    MarketEntryType.Mcp -> DdIcons.Plug
    MarketEntryType.Plugin -> DdIcons.Puzzle
}

@Composable
internal fun MarketEntryIcon(
    entry: MarketEntry,
    loader: MarketIconLoader,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val colors = deepDeepColors()
    val icon = entry.icon
    val bitmap by produceState<ImageBitmap?>(initialValue = loader.cached(icon), icon) {
        value = runCatching { loader.load(icon) }.getOrNull()
    }
    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.22f)),
            contentScale = ContentScale.Crop,
        )
    } else {
        DdIcon(
            icon = marketPlaceholderIcon(entry.type),
            tint = colors.inkTertiary,
            size = size * 0.55f,
            modifier = modifier,
        )
    }
}
