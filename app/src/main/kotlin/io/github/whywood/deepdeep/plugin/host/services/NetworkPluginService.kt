package io.github.whywood.deepdeep.plugin.host.services

import io.github.whywood.deepdeep.sdk.ai.http.HttpRequest
import io.github.whywood.deepdeep.sdk.ai.http.HttpTransport
import io.github.whywood.deepdeep.plugin.api.DownloadResult
import io.github.whywood.deepdeep.plugin.api.NetworkResponse
import io.github.whywood.deepdeep.plugin.api.NetworkService
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class NetworkPluginService(
    private val transport: HttpTransport,
) : NetworkService {
    override suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
        timeoutMs: Long,
    ): NetworkResponse = withContext(Dispatchers.IO) {
        require(url.isNotBlank()) { "url must not be blank" }
        val response = transport.execute(
            HttpRequest(
                method.uppercase(),
                url,
                headers,
                body?.toByteArray(Charsets.UTF_8),
                timeoutMs,
                null,
            ),
        )
        try {
            val bytes = response.body.inputStream().use { input ->
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(READ_CHUNK_BYTES)
                while (buffer.size() < MAX_RESPONSE_BYTES) {
                    val read = input.read(chunk, 0, minOf(chunk.size, MAX_RESPONSE_BYTES - buffer.size()))
                    if (read < 0) break
                    buffer.write(chunk, 0, read)
                }
                buffer.toByteArray()
            }
            NetworkResponse(
                statusCode = response.status,
                body = String(bytes, Charsets.UTF_8),
                headers = response.headers,
                contentType = response.header("Content-Type"),
                finalUrl = url,
            )
        } finally {
            runCatching { response.close() }
        }
    }

    override suspend fun download(
        url: String,
        targetPath: String,
        expectedSha256: String?,
        onProgress: ((Int?) -> Unit)?,
    ): DownloadResult = withContext(Dispatchers.IO) {
        require(url.isNotBlank()) { "url must not be blank" }
        require(targetPath.isNotBlank()) { "targetPath must not be blank" }
        val target = File(targetPath)
        target.parentFile?.mkdirs()
        val response = transport.execute(HttpRequest("GET", url, emptyMap(), null, DOWNLOAD_TIMEOUT_MS, null))
        try {
            if (response.status !in 200..299) throw IllegalStateException("download failed: HTTP ${response.status}")
            val total = response.header("Content-Length")?.toLongOrNull()
            val digest = MessageDigest.getInstance("SHA-256")
            val partial = File(target.parentFile, "${target.name}.part-${System.nanoTime()}")
            var size = 0L
            var lastPercent = -1
            try {
                response.body.inputStream().use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_BYTES)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            size += read
                            if (size > MAX_DOWNLOAD_BYTES) {
                                throw IllegalStateException("download exceeds $MAX_DOWNLOAD_BYTES bytes")
                            }
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            if (total != null && total > 0) {
                                val percent = ((size * 100) / total).toInt().coerceIn(0, 100)
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    onProgress?.invoke(percent)
                                }
                            }
                        }
                    }
                }
                val sha256 = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
                if (expectedSha256 != null && !sha256.equals(expectedSha256.trim(), ignoreCase = true)) {
                    throw IllegalStateException("download checksum mismatch: expected $expectedSha256, got $sha256")
                }
                if (!partial.renameTo(target)) {
                    target.delete()
                    if (!partial.renameTo(target)) throw IllegalStateException("cannot move download to ${target.absolutePath}")
                }
                DownloadResult(path = target.absolutePath, size = size, sha256 = sha256)
            } finally {
                partial.delete()
            }
        } finally {
            runCatching { response.close() }
        }
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024

        const val READ_CHUNK_BYTES = 8 * 1024

        const val MAX_DOWNLOAD_BYTES = 512L * 1024 * 1024

        const val BUFFER_BYTES = 64 * 1024

        const val DOWNLOAD_TIMEOUT_MS = 10 * 60 * 1000L
    }
}
