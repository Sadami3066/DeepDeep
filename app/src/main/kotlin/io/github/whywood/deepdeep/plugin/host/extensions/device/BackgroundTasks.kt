package io.github.whywood.deepdeep.plugin.host.extensions.device

import io.github.whywood.deepdeep.plugin.api.PluginBackgroundKind
import io.github.whywood.deepdeep.plugin.api.PluginBackgroundTaskInfo
import io.github.whywood.deepdeep.plugin.host.external.JsonFiles
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.contentOrNull

internal const val PLUGIN_BACKGROUND_DIR = "host"
internal const val PLUGIN_BACKGROUND_FILE = "background.json"
internal const val EXACT_DEGRADED_KIND = "exact-degraded"

internal fun backgroundStoreFile(pluginRoot: File): File =
    File(File(pluginRoot, PLUGIN_BACKGROUND_DIR), PLUGIN_BACKGROUND_FILE)

internal data class BackgroundTaskRecord(
    val id: String,
    val kind: PluginBackgroundKind,
    val triggerAtMs: Long? = null,
    val intervalMs: Long? = null,
    val requiresNetwork: Boolean = false,
    val requiresCharging: Boolean = false,
    val exact: Boolean = true,
    val lastRunAtMs: Long? = null,
)

internal object BackgroundTasksCodec {
    const val SCHEMA_VERSION: Int = 1

    fun encode(tasks: List<BackgroundTaskRecord>): JsonObject = buildJsonObject {
        put("schemaVersion", SCHEMA_VERSION)
        putJsonArray("tasks") {
            tasks.sortedBy { it.id }.forEach { task -> add(encodeTask(task)) }
        }
    }

    fun decode(root: JsonObject?): List<BackgroundTaskRecord> {
        val schemaVersion = (root?.get("schemaVersion") as? JsonPrimitive)?.intOrNull ?: return emptyList()
        if (schemaVersion != SCHEMA_VERSION) return emptyList()
        val array = root["tasks"] as? JsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val id = item.string("id")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val kind = PluginBackgroundKind.fromWire(item.string("kind").orEmpty()) ?: return@mapNotNull null
            BackgroundTaskRecord(
                id = id,
                kind = kind,
                triggerAtMs = item.long("triggerAtMs"),
                intervalMs = item.long("intervalMs")?.takeIf { it > 0 },
                requiresNetwork = item.boolean("requiresNetwork") ?: false,
                requiresCharging = item.boolean("requiresCharging") ?: false,
                exact = item.boolean("exact") ?: true,
                lastRunAtMs = item.long("lastRunAtMs"),
            )
        }
    }

    private fun encodeTask(task: BackgroundTaskRecord): JsonObject = buildJsonObject {
        put("id", task.id)
        put("kind", task.kind.wire)
        task.triggerAtMs?.let { put("triggerAtMs", it) }
        task.intervalMs?.let { put("intervalMs", it) }
        put("requiresNetwork", task.requiresNetwork)
        put("requiresCharging", task.requiresCharging)
        put("exact", task.exact)
        task.lastRunAtMs?.let { put("lastRunAtMs", it) }
    }

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.long(name: String): Long? = (this[name] as? JsonPrimitive)?.longOrNull

    private fun JsonObject.boolean(name: String): Boolean? = (this[name] as? JsonPrimitive)?.booleanOrNull
}

internal class PluginBackgroundStore(
    private val file: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun load(): List<BackgroundTaskRecord> = mutex.withLock {
        withContext(ioDispatcher) { BackgroundTasksCodec.decode(JsonFiles.read(file)) }
    }

    suspend fun record(id: String): BackgroundTaskRecord? = load().firstOrNull { it.id == id }

    suspend fun upsert(task: BackgroundTaskRecord): List<BackgroundTaskRecord> = mutate { tasks ->
        (tasks.filterNot { it.id == task.id } + task).sortedBy { it.id }
    }

    suspend fun remove(id: String): Boolean {
        var removed = false
        mutate { tasks ->
            removed = tasks.any { it.id == id }
            tasks.filterNot { it.id == id }
        }
        return removed
    }

    suspend fun markRun(task: BackgroundTaskRecord, firedAtMs: Long): List<BackgroundTaskRecord> =
        upsert(task.copy(lastRunAtMs = firedAtMs))

    suspend fun mutate(transform: (List<BackgroundTaskRecord>) -> List<BackgroundTaskRecord>): List<BackgroundTaskRecord> =
        mutex.withLock {
            withContext(ioDispatcher) {
                val updated = transform(BackgroundTasksCodec.decode(JsonFiles.read(file)))
                JsonFiles.write(file, BackgroundTasksCodec.encode(updated))
                updated
            }
        }

    private val mutex: Mutex
        get() = locks.computeIfAbsent(file.absolutePath) { Mutex() }

    private companion object {
        private val locks = ConcurrentHashMap<String, Mutex>()
    }
}

internal fun nextRunAtMs(task: BackgroundTaskRecord): Long? = when (task.kind) {
    PluginBackgroundKind.BOOT -> null
    PluginBackgroundKind.PERIODIC -> when {
        task.intervalMs == null || task.intervalMs <= 0 -> task.triggerAtMs
        task.lastRunAtMs != null -> task.lastRunAtMs + task.intervalMs
        else -> task.triggerAtMs
    }

    else -> task.triggerAtMs
}

internal fun isTaskDue(task: BackgroundTaskRecord, nowMs: Long): Boolean {
    val nextRunAt = nextRunAtMs(task) ?: return false
    return nextRunAt <= nowMs
}

internal fun taskInfo(task: BackgroundTaskRecord): PluginBackgroundTaskInfo = PluginBackgroundTaskInfo(
    id = task.id,
    kind = if (task.kind == PluginBackgroundKind.EXACT && !task.exact) EXACT_DEGRADED_KIND else task.kind.wire,
    nextRunAtMs = nextRunAtMs(task),
    intervalMs = task.intervalMs,
    lastRunAtMs = task.lastRunAtMs,
)

internal fun rescheduleAfterRun(task: BackgroundTaskRecord, firedAtMs: Long): BackgroundTaskRecord? {
    val interval = task.intervalMs ?: return null
    if (interval <= 0) return null
    return task.copy(lastRunAtMs = firedAtMs, triggerAtMs = firedAtMs + interval)
}
