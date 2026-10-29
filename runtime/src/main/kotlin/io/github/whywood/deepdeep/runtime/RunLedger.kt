package io.github.whywood.deepdeep.runtime

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

class RunLedger(
    private val directory: File? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val state = MutableStateFlow<List<InterruptedRun>>(emptyList())
    private val writeMutex = Mutex()
    private val file = directory?.let { File(it, FILE_NAME) }

    val interruptedRuns: StateFlow<List<InterruptedRun>> = state.asStateFlow()

    suspend fun begin(sessionId: String, runId: String, startedAt: Long, source: TurnSource) {
        val target = file ?: return
        writeMutex.withLock {
            withContext(ioDispatcher) {
                val current = readEntries(target)
                val next = (current.filterNot { it.runId == runId } + InterruptedRun(sessionId, runId, startedAt, source))
                    .sortedBy { it.startedAt }
                    .takeLast(MAX_RECORDS)
                writeEntries(target, next)
            }
        }
    }

    suspend fun clear(runId: String) {
        val target = file ?: return
        writeMutex.withLock {
            withContext(ioDispatcher) {
                val current = readEntries(target)
                if (current.none { it.runId == runId }) return@withContext
                writeEntries(target, current.filterNot { it.runId == runId })
            }
        }
    }

    suspend fun loadLeftovers(): List<InterruptedRun> {
        val target = file ?: return emptyList()
        val leftovers = writeMutex.withLock {
            withContext(ioDispatcher) {
                val current = readEntries(target)
                writeEntries(target, emptyList())
                current.sortedBy { it.startedAt }
            }
        }
        if (leftovers.isNotEmpty()) state.value = leftovers
        return leftovers
    }

    fun dismiss(sessionId: String) {
        state.update { current -> current.filterNot { it.sessionId == sessionId } }
    }

    private fun readEntries(target: File): List<InterruptedRun> {
        if (!target.isFile) return emptyList()
        val text = runCatching { target.readText() }.getOrNull() ?: return emptyList()
        if (text.isBlank()) return emptyList()
        val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject
            ?: return quarantine(target)
        val version = (root["schemaVersion"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
        val entries = root["runs"] as? JsonArray
        if (version != SCHEMA_VERSION || entries == null) return quarantine(target)
        return entries.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            runCatching { interruptedRunFromJson(item) }.getOrNull()
        }.takeLast(MAX_RECORDS)
    }

    private fun quarantine(target: File): List<InterruptedRun> {
        runCatching { target.renameTo(File(target.parentFile, "$FILE_NAME.corrupt")) }
        return emptyList()
    }

    private fun writeEntries(target: File, entries: List<InterruptedRun>) {
        val directory = target.parentFile ?: return
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("无法创建运行记录目录：${directory.absolutePath}")
        }
        val root = buildJsonObject {
            put("schemaVersion", SCHEMA_VERSION)
            putJsonArray("runs") { entries.forEach { add(it.toJsonObject()) } }
        }
        val temp = File(directory, "$FILE_NAME.tmp")
        temp.writeText(root.toString())
        try {
            Files.move(
                temp.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private companion object {
        const val SCHEMA_VERSION = 1
        const val FILE_NAME = "runs.json"
        const val MAX_RECORDS = 64
    }
}

private fun InterruptedRun.toJsonObject(): JsonObject = buildJsonObject {
    put("sessionId", sessionId)
    put("runId", runId)
    put("startedAt", startedAt)
    put("source", source.name)
}

private fun interruptedRunFromJson(json: JsonObject): InterruptedRun {
    val source = json.requiredRunString("source")
    return InterruptedRun(
        sessionId = json.requiredRunString("sessionId"),
        runId = json.requiredRunString("runId"),
        startedAt = json.requiredRunLong("startedAt"),
        source = runCatching { TurnSource.valueOf(source) }
            .getOrElse { throw IllegalArgumentException("invalid run source: $source") },
    )
}

private fun JsonObject.requiredRunString(name: String): String =
    (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }
        ?: throw IllegalArgumentException("missing run field: $name")

private fun JsonObject.requiredRunLong(name: String): Long =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
        ?: throw IllegalArgumentException("invalid run field: $name")
