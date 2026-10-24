package io.github.whywood.deepdeep.plugin

import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class PluginDownloader(
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 60_000,
    private val maxBytes: Long = 100L * 1024 * 1024,
) {
    suspend fun download(
        urls: List<String>,
        expectedSha256: String,
        target: File,
        onProgress: (Int?) -> Unit,
    ) = withContext(Dispatchers.IO) {
        require(urls.isNotEmpty()) { "no download url" }
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.part")
        var lastError: Exception? = null
        for (url in urls) {
            ensureActive()
            try {
                downloadFrom(url, temp, onProgress)
                val digest = sha256(temp)
                if (!digest.equals(expectedSha256, ignoreCase = true)) {
                    throw IllegalStateException("安装包校验失败（SHA-256 不匹配）")
                }
                if (target.exists()) target.delete()
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }
                return@withContext
            } catch (error: Exception) {
                ensureActive()
                lastError = error
                temp.delete()
            }
        }
        throw lastError ?: IllegalStateException("下载失败")
    }

    private fun downloadFrom(url: String, target: File, onProgress: (Int?) -> Unit) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "DeepDeep-Android")
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("下载失败：HTTP $code")
            }
            val total = connection.contentLengthLong
            if (total > maxBytes) {
                throw IllegalStateException("安装包过大")
            }
            FileOutputStream(target).use { output ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    var lastPercent = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        written += read
                        if (written > maxBytes) throw IllegalStateException("安装包过大")
                        output.write(buffer, 0, read)
                        if (total > 0) {
                            val percent = (written * 100 / total).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        } else {
                            onProgress(null)
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
