package io.github.whywood.deepdeep.runtime

import dev.pi.ai.PiJson
import dev.pi.mcp.config.McpServerConfig
import dev.pi.mcp.config.parseMcpConfig
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

data class McpConfigSnapshot(
    val entries: Map<String, JsonObject>,
    val servers: Map<String, McpServerConfig>,
    val errors: List<String>,
)

class McpConfigStore(
    agentDir: File,
) {
    private val mutex = Mutex()
    private val file = File(agentDir, FILE_NAME)

    suspend fun load(): McpConfigSnapshot = mutex.withLock { loadLocked() }

    suspend fun loadServers(): Map<String, McpServerConfig> = load().servers

    suspend fun upsert(name: String, config: McpServerConfig) = mutex.withLock {
        val current = loadLocked()
        val entry = config.toConfigJsonObject()
        validateEntry(name, entry)
        writeLocked(current.entries + (name to entry))
    }

    suspend fun remove(name: String) = mutex.withLock {
        val current = loadLocked()
        if (name !in current.entries) return@withLock
        writeLocked(current.entries - name)
    }

    suspend fun setEnabled(name: String, enabled: Boolean) = mutex.withLock {
        val current = loadLocked()
        val entry = current.entries[name] ?: return@withLock
        val updated = JsonObject(entry + ("enabled" to JsonPrimitive(enabled)))
        validateEntry(name, updated)
        writeLocked(current.entries + (name to updated))
    }

    private suspend fun loadLocked(): McpConfigSnapshot = withContext(Dispatchers.IO) {
        val read = readRootLocked()
        read.error?.let { message ->
            return@withContext McpConfigSnapshot(emptyMap(), emptyMap(), listOf(message))
        }
        val root = read.root
        val rawServers = (root["mcpServers"] as? JsonObject)
            ?: return@withContext McpConfigSnapshot(emptyMap(), emptyMap(), emptyList())
        val entries = LinkedHashMap<String, JsonObject>()
        rawServers.forEach { (name, element) ->
            (element as? JsonObject)?.let { entries[name] = it }
        }
        val parsed = parseMcpConfig(JsonObject(mapOf("mcpServers" to JsonObject(rawServers))))
        McpConfigSnapshot(
            entries = entries,
            servers = parsed.servers.toMap(),
            errors = parsed.errors,
        )
    }

    private data class RootRead(val root: JsonObject, val error: String?)

    private fun readRootLocked(): RootRead {
        if (!file.isFile) return RootRead(JsonObject(emptyMap()), null)
        val text = runCatching { file.readText() }.getOrElse { error ->
            return RootRead(JsonObject(emptyMap()), "mcp.json 读取失败：${error.message ?: "unknown"}")
        }
        if (text.isBlank()) return RootRead(JsonObject(emptyMap()), null)
        return runCatching { PiJson.parseToJsonElement(text).jsonObject }
            .fold(
                onSuccess = { RootRead(it, null) },
                onFailure = { RootRead(JsonObject(emptyMap()), "mcp.json 解析失败：${it.message ?: "invalid JSON"}") },
            )
    }

    private suspend fun writeLocked(entries: Map<String, JsonObject>) = withContext(Dispatchers.IO) {
        val root = buildJsonObject {
            put("mcpServers", JsonObject(entries))
        }
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "$FILE_NAME.tmp")
        temp.writeText(root.toString())
        try {
            Files.move(
                temp.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun validateEntry(name: String, entry: JsonObject) {
        val result = parseMcpConfig(
            buildJsonObject { put("mcpServers", buildJsonObject { put(name, entry) }) },
        )
        if (result.errors.isNotEmpty()) {
            throw IllegalArgumentException(result.errors.joinToString("; "))
        }
    }

    private companion object {
        const val FILE_NAME = "mcp.json"
    }
}

fun McpServerConfig.toConfigJsonObject(): JsonObject = buildJsonObject {
    when (val config = this@toConfigJsonObject) {
        is McpServerConfig.Stdio -> {
            put("type", "stdio")
            put("command", config.command)
            if (config.args.isNotEmpty()) {
                put("args", buildJsonArray { config.args.forEach { add(JsonPrimitive(it)) } })
            }
            if (config.env.isNotEmpty()) {
                put("env", buildJsonObject { config.env.forEach { (key, value) -> put(key, value) } })
            }
            config.cwd?.let { put("cwd", it) }
        }

        is McpServerConfig.Http -> {
            put("type", "http")
            put("url", config.url)
            if (config.headers.isNotEmpty()) {
                put("headers", buildJsonObject { config.headers.forEach { (key, value) -> put(key, value) } })
            }
            config.oauth?.let { oauth ->
                put("oauth", buildJsonObject {
                    oauth.clientId?.let { put("clientId", it) }
                    oauth.clientSecret?.let { put("clientSecret", it) }
                    oauth.callbackPort?.let { put("callbackPort", it) }
                    oauth.callbackUrl?.let { put("callbackUrl", it) }
                    oauth.scope?.let { put("scope", it) }
                    oauth.clientName?.let { put("clientName", it) }
                    oauth.clientRegistration?.let { put("clientRegistration", it) }
                    oauth.authServerMetadataUrl?.let { put("authServerMetadataUrl", it) }
                })
            }
            config.authProvider?.let { provider ->
                put("auth", buildJsonObject { put("provider", provider) })
            }
        }
    }
    put("enabled", enabled)
    put("timeout", timeoutSeconds)
    description?.let { put("description", it) }
    put("exposure", exposure.name.lowercase())
    if (toolExposure.isNotEmpty()) {
        put("toolExposure", buildJsonObject {
            toolExposure.forEach { (tool, value) -> put(tool, value.name.lowercase()) }
        })
    }
}
