package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.sdk.coding.paths.AgentPaths
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.OffsetDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okio.Path.Companion.toPath

data class IndexedSession(
    val sessionId: String,
    val name: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val filePath: String,
    val cwd: String,
    val messageCount: Int,
)

class SessionIndex(
    private val agentDir: File,
    indexFile: File = defaultIndexFile(agentDir),
    private val json: Json = Json,
) {
    private class Entry(
        val size: Long,
        val mtime: Long,
        val sessionId: String,
        val cwd: String,
        val createdAt: Long,
        val name: String?,
        val messageCount: Int,
    ) {
        val valid: Boolean get() = sessionId.isNotEmpty()

        fun session(filePath: String): IndexedSession = IndexedSession(
            sessionId = sessionId,
            name = name,
            createdAt = createdAt,
            updatedAt = maxOf(createdAt, mtime),
            filePath = filePath,
            cwd = cwd,
            messageCount = messageCount,
        )
    }

    private val indexFile: File = indexFile.absoluteFile
    private val mutex = Mutex()
    private val entries = mutableMapOf<String, Entry>()
    private var loaded = false
    private var dirty = false

    internal var parsedFileCount: Int = 0
        private set

    internal var lastLoadSucceeded: Boolean = true
        private set

    suspend fun list(cwd: String): List<IndexedSession> = withContext(Dispatchers.IO) {
        mutex.withLock {
            load()
            val sessions = scan(sessionsDirectory(cwd))
            persist()
            sessions
        }
    }

    suspend fun listAll(cwds: List<String>): List<IndexedSession> = withContext(Dispatchers.IO) {
        mutex.withLock {
            load()
            val seen = mutableSetOf<String>()
            val result = mutableListOf<IndexedSession>()
            for (cwd in cwds) {
                for (session in scan(sessionsDirectory(cwd))) {
                    if (seen.add(session.sessionId)) result.add(session)
                }
            }
            persist()
            result.sortedWith(UPDATED_DESC)
        }
    }

    private fun sessionsDirectory(cwd: String): File =
        AgentPaths(cwd.toPath(), agentDir.absolutePath.toPath()).sessionsForCwd().toFile()

    private fun scan(dir: File): List<IndexedSession> {
        val files = dir.listFiles { file -> file.isFile && file.name.endsWith(SESSION_EXTENSION) }.orEmpty()
        val present = mutableSetOf<String>()
        val result = mutableListOf<IndexedSession>()
        for (file in files) {
            val path = file.absolutePath
            present.add(path)
            val size = file.length()
            val mtime = file.lastModified()
            val cached = entries[path]
            val entry = if (cached != null && cached.size == size && cached.mtime == mtime) {
                cached
            } else {
                val parsed = parse(file, size, mtime) ?: Entry(size, mtime, "", "", 0, null, 0)
                parsedFileCount += 1
                entries[path] = parsed
                dirty = true
                parsed
            }
            if (entry.valid) result.add(entry.session(path))
        }
        val prefix = dir.absolutePath + File.separator
        val stale = entries.keys.filter { path -> path.startsWith(prefix) && path !in present }
        if (stale.isNotEmpty()) {
            stale.forEach { entries.remove(it) }
            dirty = true
        }
        return result.sortedWith(UPDATED_DESC)
    }

    private fun parse(file: File, size: Long, mtime: Long): Entry? {
        var sessionId: String? = null
        var cwd = ""
        var createdAt = 0L
        var name: String? = null
        var messageCount = 0
        try {
            file.bufferedReader(Charsets.UTF_8).use { reader ->
                var first = true
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    if (first) {
                        first = false
                        val header = parseObject(line) ?: return null
                        if (header.string("type") != "session") return null
                        sessionId = header.string("id") ?: return null
                        cwd = header.string("cwd").orEmpty()
                        createdAt = parseTimestamp(header.string("timestamp"))
                        continue
                    }
                    when {
                        line.startsWith(MESSAGE_PREFIX) -> messageCount += 1
                        line.startsWith(SESSION_INFO_PREFIX) -> {
                            name = parseObject(line)?.string("name")?.trim()?.takeIf { it.isNotEmpty() } ?: name
                        }

                        line.length < SESSION_INFO_SCAN_LIMIT && line.contains(SESSION_INFO_MARKER) -> {
                            val entry = parseObject(line)
                            if (entry?.string("type") == "session_info") {
                                name = entry.string("name")?.trim()?.takeIf { it.isNotEmpty() } ?: name
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            return null
        }
        val id = sessionId ?: return null
        return Entry(
            size = size,
            mtime = mtime,
            sessionId = id,
            cwd = cwd,
            createdAt = createdAt,
            name = name,
            messageCount = messageCount,
        )
    }

    private fun parseObject(line: String): JsonObject? =
        runCatching { json.parseToJsonElement(line) as? JsonObject }.getOrNull()

    private fun parseTimestamp(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        return runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrDefault(0L)
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun load() {
        if (loaded) return
        loaded = true
        if (!indexFile.exists()) return
        val root = runCatching {
            json.parseToJsonElement(indexFile.readText()) as? JsonObject
        }.getOrNull()
        if (root == null || root.string("schemaVersion")?.toIntOrNull() != SCHEMA_VERSION) {
            lastLoadSucceeded = false
            dirty = true
            return
        }
        val stored = root["entries"] as? JsonObject
        if (stored == null) {
            lastLoadSucceeded = false
            dirty = true
            return
        }
        stored.forEach { (path, element) ->
            val obj = element as? JsonObject ?: return@forEach
            val entry = decodeEntry(obj) ?: return@forEach
            entries[path] = entry
        }
    }

    private fun decodeEntry(obj: JsonObject): Entry? {
        val size = obj.string("size")?.toLongOrNull() ?: return null
        val mtime = obj.string("mtime")?.toLongOrNull() ?: return null
        val sessionId = obj.string("sessionId") ?: return null
        return Entry(
            size = size,
            mtime = mtime,
            sessionId = sessionId,
            cwd = obj.string("cwd").orEmpty(),
            createdAt = obj.string("createdAt")?.toLongOrNull() ?: 0L,
            name = obj.string("name"),
            messageCount = obj.string("messageCount")?.toIntOrNull() ?: 0,
        )
    }

    private fun persist() {
        if (!dirty) return
        val root = buildJsonObject {
            put("schemaVersion", SCHEMA_VERSION)
            put(
                "entries",
                buildJsonObject {
                    entries.forEach { (path, entry) -> put(path, entry.toJson()) }
                },
            )
        }
        runCatching {
            val parent = indexFile.parentFile
            parent?.mkdirs()
            val temp = File(parent, indexFile.name + ".tmp")
            temp.writeText(root.toString())
            try {
                Files.move(
                    temp.toPath(),
                    indexFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), indexFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }.onSuccess {
            dirty = false
            lastLoadSucceeded = true
        }
    }

    private fun Entry.toJson(): JsonObject = buildJsonObject {
        put("size", size)
        put("mtime", mtime)
        put("sessionId", sessionId)
        put("cwd", cwd)
        put("createdAt", createdAt)
        put("name", name)
        put("messageCount", messageCount)
    }

    companion object {
        const val SCHEMA_VERSION = 1
        const val INDEX_FILE_NAME = "session-index.json"

        private const val SESSION_EXTENSION = ".jsonl"
        private const val MESSAGE_PREFIX = "{\"type\":\"message\""
        private const val SESSION_INFO_PREFIX = "{\"type\":\"session_info\""
        private const val SESSION_INFO_MARKER = "\"type\":\"session_info\""
        private const val SESSION_INFO_SCAN_LIMIT = 4096

        private val UPDATED_DESC = compareByDescending<IndexedSession> { it.updatedAt }

        private fun defaultIndexFile(agentDir: File): File =
            File(agentDir.absoluteFile.parentFile ?: agentDir.absoluteFile, INDEX_FILE_NAME)
    }
}
