package io.github.whywood.deepdeep.plugin.host.images

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.whywood.deepdeep.plugin.api.RemoteImage
import io.github.whywood.deepdeep.sdk.ai.http.HttpRequest
import io.github.whywood.deepdeep.sdk.ai.http.HttpTransport
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class RemoteImageCache(
    private val cacheDir: File,
    private val transport: HttpTransport,
    private val maxMemoryBytes: Int = DEFAULT_MEMORY_BYTES,
    private val maxDiskBytes: Long = DEFAULT_DISK_BYTES,
    private val maxSourceBytes: Int = DEFAULT_SOURCE_BYTES,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = ConcurrentHashMap<String, Deferred<RemoteImage?>>()
    private val diskMutex = Mutex()

    private val memory = object : LruCache<String, ImageBitmap>(maxMemoryBytes) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    suspend fun load(url: String, maxEdgePx: Int): RemoteImage? {
        val normalized = ImageCachePolicy.normalize(url) ?: return null
        val edge = maxEdgePx.coerceIn(MIN_EDGE_PX, ImageCachePolicy.MAX_EDGE_PX)
        val key = ImageCachePolicy.memoryKey(normalized, edge)
        memory.get(key)?.let { bitmap ->
            return RemoteImage(bitmap, bitmap.width, bitmap.height, fromCache = true)
        }
        val job = inFlight.computeIfAbsent(key) { scope.async { loadMiss(normalized, edge) } }
        return try {
            job.await()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            null
        } finally {
            inFlight.remove(key, job)
        }
    }

    private suspend fun loadMiss(url: String, edge: Int): RemoteImage? {
        val file = diskFile(url)
        val cached = runCatching {
            withContext(Dispatchers.IO) { file.takeIf { it.isFile }?.readBytes() }
        }.getOrNull()
        val fetched = if (cached == null) fetch(url) else null
        val bytes = cached ?: fetched ?: return null
        if (cached == null && fetched != null) putDisk(file, fetched)
        val bitmap = decode(bytes, edge) ?: return null
        memory.put(ImageCachePolicy.memoryKey(url, edge), bitmap)
        return RemoteImage(
            bitmap = bitmap,
            width = bitmap.width,
            height = bitmap.height,
            fromCache = cached != null,
        )
    }

    private suspend fun fetch(url: String): ByteArray? = withContext(Dispatchers.IO) {
        for (candidate in ImageCachePolicy.candidates(url)) {
            val bytes = runCatching { fetchOne(candidate) }.getOrNull() ?: continue
            if (bytes.isNotEmpty()) return@withContext bytes
        }
        null
    }

    private suspend fun fetchOne(url: String): ByteArray? {
        val response = transport.execute(
            HttpRequest(
                method = "GET",
                url = url,
                headers = mapOf("Accept" to "image/*"),
                timeoutMs = timeoutMs,
            ),
        )
        response.use {
            if (!it.isSuccessful()) return null
            val length = it.header("Content-Length")?.toLongOrNull()
            if (length != null && length > maxSourceBytes) return null
            val output = ByteArrayOutputStream()
            it.body.inputStream().use { input ->
                val buffer = ByteArray(READ_CHUNK_BYTES)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (output.size() + read > maxSourceBytes) return null
                    output.write(buffer, 0, read)
                }
            }
            return output.toByteArray()
        }
    }

    private suspend fun putDisk(file: File, bytes: ByteArray) = withContext(Dispatchers.IO) {
        if (file.isFile && file.length() == bytes.size.toLong()) {
            file.setLastModified(System.currentTimeMillis())
            return@withContext
        }
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.${System.nanoTime()}.tmp")
        try {
            temp.writeBytes(bytes)
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
        } finally {
            temp.delete()
        }
        trimDisk()
    }

    private suspend fun trimDisk() = diskMutex.withLock {
        withContext(Dispatchers.IO) {
            val files = cacheDir.listFiles()?.filter { it.isFile } ?: return@withContext
            var total = files.sumOf { it.length() }
            if (total <= maxDiskBytes) return@withContext
            val target = maxDiskBytes / 4 * 3
            files.sortedBy { it.lastModified() }.forEach { file ->
                if (total <= target) return@forEach
                val size = file.length()
                if (file.delete()) total -= size
            }
        }
    }

    private fun diskFile(url: String): File =
        File(File(cacheDir, "images"), "${ImageCachePolicy.sha256(url)}.img")

    private fun decode(bytes: ByteArray, maxEdgePx: Int): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sample = ImageCachePolicy.sampleSize(bounds.outWidth, bounds.outHeight, maxEdgePx)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        val longEdge = maxOf(decoded.width, decoded.height)
        if (longEdge <= maxEdgePx) return decoded.asImageBitmap()
        val scale = maxEdgePx.toFloat() / longEdge
        val width = (decoded.width * scale).toInt().coerceAtLeast(1)
        val height = (decoded.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(decoded, width, height, true)
        if (scaled !== decoded) decoded.recycle()
        return scaled.asImageBitmap()
    }

    private companion object {
        const val MIN_EDGE_PX = 48
        const val DEFAULT_MEMORY_BYTES = 24 * 1024 * 1024
        const val DEFAULT_DISK_BYTES = 128L * 1024 * 1024
        const val DEFAULT_SOURCE_BYTES = 6 * 1024 * 1024
        const val DEFAULT_TIMEOUT_MS = 15_000L
        const val READ_CHUNK_BYTES = 8 * 1024
    }
}

internal object ImageCachePolicy {
    const val MAX_EDGE_PX = 1440

    fun normalize(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.length > 2048) return null
        val lower = trimmed.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return null
        return trimmed.substringBefore('#')
    }

    fun candidates(normalized: String): List<String> {
        if (!normalized.startsWith("http://")) return listOf(normalized)
        return listOf("https://" + normalized.removePrefix("http://"), normalized)
    }

    fun memoryKey(url: String, maxEdgePx: Int): String = "${sha256(url)}:$maxEdgePx"

    fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    fun sampleSize(width: Int, height: Int, maxEdgePx: Int): Int {
        var sample = 1
        val longEdge = maxOf(width, height)
        while (sample < 16) {
            val sampledEdge = longEdge / sample
            val sampledPixels = (width.toLong() / sample) * (height.toLong() / sample)
            if (sampledEdge <= maxEdgePx * 2 && sampledPixels <= MAX_DECODE_PIXELS) break
            sample *= 2
        }
        return sample
    }

    private const val MAX_DECODE_PIXELS = 4L * 1024L * 1024L
}
