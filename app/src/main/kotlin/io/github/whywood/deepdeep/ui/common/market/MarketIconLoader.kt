package io.github.whywood.deepdeep.ui.common.market

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

internal class MarketIconLoader(
    context: Context,
    private val http: OkHttpClient = defaultHttpClient(),
) {
    private val dir = File(File(context.applicationContext.filesDir, MarketDefaults.MARKET_DIR), "icons")
    private val cache = HashMap<String, ImageBitmap?>()
    private val mutex = Mutex()

    suspend fun load(icon: String?): ImageBitmap? {
        val url = marketIconUrl(icon) ?: return null
        if (cache.containsKey(url)) return cache[url]
        return mutex.withLock {
            if (cache.containsKey(url)) {
                cache[url]
            } else {
                val bitmap = withContext(Dispatchers.IO) { readFile(url) ?: download(url) }
                cache[url] = bitmap
                bitmap
            }
        }
    }

    private fun readFile(url: String): ImageBitmap? {
        val file = fileFor(url)
        if (!file.isFile || file.length() > MarketDefaults.MAX_ICON_BYTES) return null
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        return decode(bytes)
    }

    private fun download(url: String): ImageBitmap? = runCatching {
        val request = Request.Builder().url(url).get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val body = response.body
            if (body.contentLength() > MarketDefaults.MAX_ICON_BYTES) return@use null
            val bytes = body.bytes()
            if (bytes.size > MarketDefaults.MAX_ICON_BYTES) return@use null
            val bitmap = decode(bytes) ?: return@use null
            dir.mkdirs()
            runCatching { fileFor(url).writeBytes(bytes) }
            bitmap
        }
    }.getOrNull()

    private fun decode(bytes: ByteArray): ImageBitmap? = runCatching {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()

    private fun fileFor(url: String): File = File(dir, sha256(url))

    private companion object {
        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
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
    return remember(context) { MarketIconLoader(context) }
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
    val bitmap by produceState<ImageBitmap?>(initialValue = null, icon) {
        value = loader.load(icon)
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.25f))
            .background(colors.wash),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            DdIcon(
                icon = marketPlaceholderIcon(entry.type),
                tint = colors.inkTertiary,
                size = size * 0.5f,
            )
        }
    }
}
