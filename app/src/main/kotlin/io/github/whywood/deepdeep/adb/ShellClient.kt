package io.github.whywood.deepdeep.adb

import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ShellClient(
    private val port: Int,
    private val token: String,
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private val execHttp: OkHttpClient = http.newBuilder()
        .readTimeout(MAX_EXEC_READ_SECONDS, TimeUnit.SECONDS)
        .build()

    private val baseUrl: String = "http://127.0.0.1:$port"

    suspend fun isAlive(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$baseUrl/health")
                .header(TOKEN_HEADER, token)
                .build()
            http.newCall(request).execute().use { response ->
                response.isSuccessful && response.body.string().contains("uid=2000")
            }
        }.getOrDefault(false)
    }

    suspend fun exec(command: String, timeoutSeconds: Long = 20): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/exec")
            .header(TOKEN_HEADER, token)
            .header(TIMEOUT_HEADER, timeoutSeconds.toString())
            .post(command.toRequestBody(TEXT_PLAIN))
            .build()
        execHttp.newCall(request).execute().use { response ->
            if (response.code == 403) error("daemon token rejected")
            response.body.string()
        }
    }

    suspend fun startHelper(apkPath: String, helperPort: Int, nonce: String): Int? = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = URLEncoder.encode(apkPath, "UTF-8")
            val request = Request.Builder()
                .url("$baseUrl/start-helper?apk=$encoded&port=$helperPort&nonce=$nonce")
                .header(TOKEN_HEADER, token)
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                Regex("pid=(\\d+)").find(response.body.string())?.groupValues?.get(1)?.toIntOrNull()
            }
        }.getOrNull()
    }

    suspend fun killHelper(pid: Int): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$baseUrl/kill-helper?pid=$pid")
                .header(TOKEN_HEADER, token)
                .build()
            http.newCall(request).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    private companion object {
        const val TOKEN_HEADER = "X-Token"
        const val TIMEOUT_HEADER = "X-Timeout"
        const val MAX_EXEC_READ_SECONDS = 125L
        val TEXT_PLAIN = "text/plain; charset=utf-8".toMediaType()
    }
}
