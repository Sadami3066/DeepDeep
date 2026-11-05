package io.github.whywood.deepdeep.plugin.host.update

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal interface PluginFetcher {
    suspend fun readText(url: String, maxBytes: Long): String

    suspend fun download(url: String, target: File, maxBytes: Long, onProgress: (Int?) -> Unit)
}

internal class HttpPluginFetcher(
    private val connectTimeoutMillis: Int = PluginUpdateDefaults.CONNECT_TIMEOUT_MILLIS,
    private val readTimeoutMillis: Int = PluginUpdateDefaults.READ_TIMEOUT_MILLIS,
    private val downloadReadTimeoutMillis: Int = PluginUpdateDefaults.DOWNLOAD_READ_TIMEOUT_MILLIS,
    private val userAgent: String = PluginUpdateDefaults.USER_AGENT,
) : PluginFetcher {
    override suspend fun readText(url: String, maxBytes: Long): String = withContext(Dispatchers.IO) {
        when (schemeOf(url)) {
            "file" -> readFileText(url, maxBytes)
            "http", "https" -> readHttpText(url, maxBytes)
            else -> throw IOException("unsupported url scheme: $url")
        }
    }

    override suspend fun download(
        url: String,
        target: File,
        maxBytes: Long,
        onProgress: (Int?) -> Unit,
    ) = withContext(Dispatchers.IO) {
        when (schemeOf(url)) {
            "file" -> copyLocalFile(url, target, maxBytes, onProgress)
            "http", "https" -> downloadHttp(url, target, maxBytes, onProgress)
            else -> throw IOException("unsupported url scheme: $url")
        }
    }

    private fun readFileText(url: String, maxBytes: Long): String {
        val file = File(URI(url))
        if (!file.isFile) throw IOException("index file not found: ${file.path}")
        if (file.length() > maxBytes) throw IOException("response is too large: ${file.length()} bytes")
        val bytes = file.readBytes()
        if (bytes.size.toLong() > maxBytes) throw IOException("response is too large: ${bytes.size} bytes")
        return bytes.toString(Charsets.UTF_8)
    }

    private fun readHttpText(url: String, maxBytes: Long): String {
        val connection = openHttp(url, readTimeoutMillis)
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code for $url")
            val length = connection.contentLengthLong
            if (length > maxBytes) throw IOException("response is too large: $length bytes")
            connection.inputStream.use { input ->
                return readBounded(input, maxBytes).toString(Charsets.UTF_8)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadHttp(url: String, target: File, maxBytes: Long, onProgress: (Int?) -> Unit) {
        val connection = openHttp(url, downloadReadTimeoutMillis)
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code for $url")
            val length = connection.contentLengthLong
            if (length > maxBytes) throw IOException("response is too large: $length bytes")
            target.parentFile?.mkdirs()
            connection.inputStream.use { input ->
                copyBounded(input, target, maxBytes, length, onProgress)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun copyLocalFile(url: String, target: File, maxBytes: Long, onProgress: (Int?) -> Unit) {
        val source = File(URI(url))
        if (!source.isFile) throw IOException("package file not found: ${source.path}")
        if (source.length() > maxBytes) throw IOException("package is too large: ${source.length()} bytes")
        source.inputStream().use { input ->
            target.parentFile?.mkdirs()
            copyBounded(input, target, maxBytes, source.length(), onProgress)
        }
    }

    private fun copyBounded(
        input: InputStream,
        target: File,
        maxBytes: Long,
        contentLength: Long,
        onProgress: (Int?) -> Unit,
    ) {
        target.outputStream().buffered().use { output ->
            val buffer = ByteArray(BUFFER_SIZE)
            var written = 0L
            var lastPercent = -1
            if (contentLength <= 0) onProgress(null)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                written += read
                if (written > maxBytes) throw IOException("package exceeds $maxBytes bytes")
                output.write(buffer, 0, read)
                if (contentLength > 0) {
                    val percent = (written * 100 / contentLength).toInt().coerceIn(0, 100)
                    if (percent != lastPercent) {
                        lastPercent = percent
                        onProgress(percent)
                    }
                }
            }
        }
    }

    private fun readBounded(input: InputStream, maxBytes: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) throw IOException("response exceeds $maxBytes bytes")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun openHttp(url: String, timeoutMillis: Int): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = connectTimeoutMillis
        connection.readTimeout = timeoutMillis
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", userAgent)
        return connection
    }

    private fun schemeOf(url: String): String? =
        runCatching { URI(url.trim()).scheme?.lowercase() }.getOrNull()

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
    }
}
