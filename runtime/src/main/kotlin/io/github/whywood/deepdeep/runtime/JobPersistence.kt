package io.github.whywood.deepdeep.runtime

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

internal data class PersistedJob(
    val id: String,
    val sessionId: String,
    val label: String,
    val command: String,
    val status: JobStatus,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val exitCode: Int? = null,
    val output: String = "",
    val dropped: Boolean = false,
)

class JobPersistence(
    private val directory: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val lock = Any()
    private val file = File(directory, FILE_NAME)

    internal fun load(): List<PersistedJob> = synchronized(lock) {
        if (!file.isFile) return emptyList()
        val text = runCatching { file.readText() }.getOrNull() ?: return emptyList()
        if (text.isBlank()) return emptyList()
        val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject
            ?: return quarantine()
        val version = (root["schemaVersion"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
        val entries = root["jobs"] as? JsonArray
        if (version != SCHEMA_VERSION || entries == null) return quarantine()
        entries.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            runCatching { persistedJobFromJson(item) }.getOrNull()
        }.takeLast(MAX_RECORDS)
    }

    internal suspend fun save(jobs: List<PersistedJob>) = withContext(ioDispatcher) {
        synchronized(lock) {
            val running = jobs.filter { it.status == JobStatus.RUNNING }
            val settled = jobs.filter { it.status != JobStatus.RUNNING }
            val retained = if (running.size >= MAX_RECORDS) {
                running.takeLast(MAX_RECORDS)
            } else {
                settled.takeLast(MAX_RECORDS - running.size) + running
            }
            writeRoot(buildJsonObject {
                put("schemaVersion", SCHEMA_VERSION)
                putJsonArray("jobs") { retained.forEach { add(it.toJsonObject()) } }
            })
        }
    }

    private fun writeRoot(root: JsonObject) {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("无法创建后台任务目录：${directory.absolutePath}")
        }
        val temp = File(directory, "$FILE_NAME.tmp")
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

    private fun quarantine(): List<PersistedJob> {
        runCatching {
            if (file.isFile) file.renameTo(File(directory, "$FILE_NAME.corrupt"))
            writeRoot(buildJsonObject {
                put("schemaVersion", SCHEMA_VERSION)
                putJsonArray("jobs") {}
            })
        }
        return emptyList()
    }

    private companion object {
        const val SCHEMA_VERSION = 1
        const val FILE_NAME = "jobs.json"
        const val MAX_RECORDS = 50
    }
}

private fun PersistedJob.toJsonObject(): JsonObject {
    val tail = outputTail(output)
    return buildJsonObject {
        put("id", id)
        put("sessionId", sessionId)
        put("label", label)
        put("command", command)
        put("status", status.name)
        if (exitCode == null) {
            put("exitCode", JsonNull)
        } else {
            put("exitCode", exitCode)
        }
        put("startedAt", startedAt)
        if (finishedAt == null) {
            put("finishedAt", JsonNull)
        } else {
            put("finishedAt", finishedAt)
        }
        put("output", tail)
        put("dropped", dropped || tail.length != output.length)
    }
}

private fun persistedJobFromJson(json: JsonObject): PersistedJob {
    val status = json.requiredJobString("status")
    val rawOutput = json.optionalJobString("output").orEmpty()
    val output = outputTail(rawOutput)
    return PersistedJob(
        id = json.requiredJobString("id"),
        sessionId = json.requiredJobString("sessionId"),
        label = json.requiredJobString("label"),
        command = json.requiredJobString("command"),
        status = runCatching { JobStatus.valueOf(status) }
            .getOrElse { throw IllegalArgumentException("invalid job status: $status") },
        startedAt = json.requiredJobLong("startedAt"),
        finishedAt = json.optionalJobLong("finishedAt"),
        exitCode = json.optionalJobLong("exitCode")?.toInt(),
        output = output,
        dropped = json.optionalJobString("dropped")?.toBooleanStrictOrNull() == true || output.length != rawOutput.length,
    )
}

private const val JOB_OUTPUT_LIMIT = 16 * 1024

private fun outputTail(output: String): String {
    val bytes = output.toByteArray(Charsets.UTF_8)
    if (bytes.size <= JOB_OUTPUT_LIMIT) return output
    var start = bytes.size - JOB_OUTPUT_LIMIT
    while (start < bytes.size && (bytes[start].toInt() and 0xC0) == 0x80) start++
    return String(bytes, start, bytes.size - start, Charsets.UTF_8)
}

private fun JsonObject.requiredJobString(name: String): String =
    (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }
        ?: throw IllegalArgumentException("missing job field: $name")

private fun JsonObject.optionalJobString(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.requiredJobLong(name: String): Long =
    optionalJobLong(name) ?: throw IllegalArgumentException("missing job field: $name")

private fun JsonObject.optionalJobLong(name: String): Long? {
    val element = this[name] ?: return null
    if (element is JsonNull) return null
    return (element as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
}
