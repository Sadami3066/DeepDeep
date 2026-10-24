package io.github.whywood.deepdeep.ui.workshop

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import io.github.whywood.deepdeep.runtime.WorkshopManifest
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

data class WorkshopByteRange(val start: Long, val endInclusive: Long) {
    val length: Long get() = endInclusive - start + 1
}

object WorkshopAssets {
    const val FALLBACK_MIME = "application/octet-stream"
    const val CACHE_CONTROL = "no-store"
    const val TEXT_ENCODING = "utf-8"

    private val MIME_TYPES = mapOf(
        "html" to "text/html",
        "htm" to "text/html",
        "css" to "text/css",
        "js" to "text/javascript",
        "mjs" to "text/javascript",
        "json" to "application/json",
        "svg" to "image/svg+xml",
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "webp" to "image/webp",
        "gif" to "image/gif",
        "ico" to "image/x-icon",
        "woff2" to "font/woff2",
        "woff" to "font/woff",
        "ttf" to "font/ttf",
        "otf" to "font/otf",
        "mp4" to "video/mp4",
        "webm" to "video/webm",
        "mp3" to "audio/mpeg",
        "wav" to "audio/wav",
        "wasm" to "application/wasm",
    )

    private val TEXT_MIME_TYPES = setOf(
        "application/json",
        "image/svg+xml",
    )

    fun resolveFile(appDir: File, path: String): File? {
        if (path.isEmpty() || path.contains('\u0000')) return null
        val root = runCatching { appDir.canonicalFile }.getOrNull() ?: return null
        if (!root.isDirectory) return null
        val relative = path.trimStart('/')
        if (relative.isEmpty()) return null
        if (relative.split('/').any { it == ".." }) return null
        val candidate = runCatching { File(root, relative).canonicalFile }.getOrNull() ?: return null
        if (!candidate.toPath().startsWith(root.toPath())) return null
        if (!candidate.isFile) return null
        return candidate
    }

    fun mimeTypeFor(path: String): String {
        val name = path.substringAfterLast('/', path)
        val extension = name.substringAfterLast('.', "").lowercase()
        return MIME_TYPES[extension] ?: FALLBACK_MIME
    }

    fun encodingFor(mimeType: String): String? {
        if (mimeType.startsWith("text/")) return TEXT_ENCODING
        return if (mimeType in TEXT_MIME_TYPES) TEXT_ENCODING else null
    }

    fun parseRange(header: String?, contentLength: Long): WorkshopByteRange? {
        if (header == null || contentLength <= 0) return null
        val trimmed = header.trim()
        if (!trimmed.startsWith("bytes=", ignoreCase = true)) return null
        val spec = trimmed.substring("bytes=".length).trim()
        if (spec.isEmpty() || spec.contains(',')) return null
        val dash = spec.indexOf('-')
        if (dash < 0) return null
        val startText = spec.substring(0, dash).trim()
        val endText = spec.substring(dash + 1).trim()
        return when {
            startText.isEmpty() -> {
                val suffixLength = endText.toLongOrNull() ?: return null
                if (suffixLength <= 0) return null
                val length = minOf(suffixLength, contentLength)
                WorkshopByteRange(contentLength - length, contentLength - 1)
            }

            endText.isEmpty() -> {
                val start = startText.toLongOrNull() ?: return null
                if (start < 0 || start >= contentLength) return null
                WorkshopByteRange(start, contentLength - 1)
            }

            else -> {
                val start = startText.toLongOrNull() ?: return null
                val end = endText.toLongOrNull() ?: return null
                if (start < 0 || end < start || start >= contentLength) return null
                WorkshopByteRange(start, minOf(end, contentLength - 1))
            }
        }
    }
}

class WorkshopAssetServer(appDir: File, manifest: WorkshopManifest) {
    private val root: File = appDir.canonicalFile
    private val assetLoader: WebViewAssetLoader = WebViewAssetLoader.Builder()
        .setDomain(manifest.id + ".workshop.local")
        .addPathHandler("/", object : WebViewAssetLoader.PathHandler {
            override fun handle(path: String): WebResourceResponse? = openResponse(path)
        })
        .build()

    val domain: String = manifest.id + ".workshop.local"
    val origin: String = "https://" + domain

    fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {
        val uri = request.url
        if (!uri.host.equals(domain, ignoreCase = true)) return assetLoader.shouldInterceptRequest(uri)
        val rangeHeader = request.requestHeaders?.entries
            ?.firstOrNull { it.key.equals("Range", ignoreCase = true) }
            ?.value
        if (rangeHeader != null) {
            rangeResponse(uri.path.orEmpty(), rangeHeader)?.let { return it }
        }
        return assetLoader.shouldInterceptRequest(uri)
    }

    private fun openResponse(path: String): WebResourceResponse {
        val file = WorkshopAssets.resolveFile(root, path) ?: return notFound()
        val mimeType = WorkshopAssets.mimeTypeFor(file.name)
        return try {
            val stream = FileInputStream(file)
            WebResourceResponse(
                mimeType,
                WorkshopAssets.encodingFor(mimeType),
                200,
                "OK",
                fullHeaders(file.length()),
                stream,
            )
        } catch (error: IOException) {
            notFound()
        }
    }

    private fun rangeResponse(path: String, rangeHeader: String): WebResourceResponse? {
        val file = WorkshopAssets.resolveFile(root, path) ?: return null
        val length = file.length()
        val range = WorkshopAssets.parseRange(rangeHeader, length) ?: return null
        val mimeType = WorkshopAssets.mimeTypeFor(file.name)
        val stream = runCatching { RangeInputStream(file, range.start, range.length) }.getOrNull()
            ?: return null
        return WebResourceResponse(
            mimeType,
            WorkshopAssets.encodingFor(mimeType),
            206,
            "Partial Content",
            rangeHeaders(range, length),
            stream,
        )
    }

    private fun fullHeaders(length: Long): MutableMap<String, String> = mutableMapOf(
        "Cache-Control" to WorkshopAssets.CACHE_CONTROL,
        "Accept-Ranges" to "bytes",
        "Content-Length" to length.toString(),
    )

    private fun rangeHeaders(range: WorkshopByteRange, length: Long): MutableMap<String, String> = mutableMapOf(
        "Cache-Control" to WorkshopAssets.CACHE_CONTROL,
        "Accept-Ranges" to "bytes",
        "Content-Range" to "bytes " + range.start + "-" + range.endInclusive + "/" + length,
        "Content-Length" to range.length.toString(),
    )

    private fun notFound(): WebResourceResponse = WebResourceResponse(
        "text/plain",
        WorkshopAssets.TEXT_ENCODING,
        404,
        "Not Found",
        mutableMapOf("Cache-Control" to WorkshopAssets.CACHE_CONTROL),
        ByteArrayInputStream("Not Found".toByteArray()),
    )

    private class RangeInputStream(
        file: File,
        start: Long,
        private var remaining: Long,
    ) : InputStream() {
        private val source = FileInputStream(file)

        init {
            source.channel.position(start)
        }

        override fun read(): Int {
            if (remaining <= 0) return -1
            val value = source.read()
            if (value >= 0) remaining--
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (remaining <= 0) return -1
            val requested = minOf(length.toLong(), remaining).toInt()
            if (requested <= 0) return 0
            val read = source.read(buffer, offset, requested)
            if (read > 0) remaining -= read
            return read
        }

        override fun close() = source.close()
    }
}
