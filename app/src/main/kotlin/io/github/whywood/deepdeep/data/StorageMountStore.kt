package io.github.whywood.deepdeep.data

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class StorageMount(
    val id: String,
    val name: String,
    val description: String,
    val path: String,
)

class StorageMountStore(
    private val file: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()
    private val _mounts = MutableStateFlow(readMounts())

    val mounts: StateFlow<List<StorageMount>> = _mounts.asStateFlow()

    fun cached(): List<StorageMount> = _mounts.value

    suspend fun save(mounts: List<StorageMount>): List<StorageMount> = mutex.withLock {
        withContext(ioDispatcher) {
            val normalized = normalizeMounts(mounts)
            write(normalized)
            _mounts.value = normalized
            normalized
        }
    }

    suspend fun add(mount: StorageMount): List<StorageMount> = save(cached() + mount)

    suspend fun remove(id: String): List<StorageMount> = save(cached().filterNot { it.id == id })

    private fun write(mounts: List<StorageMount>) {
        val root = buildJsonObject {
            put(
                "mounts",
                buildJsonArray {
                    mounts.forEach { mount ->
                        add(
                            buildJsonObject {
                                put("id", mount.id)
                                put("name", mount.name)
                                put("description", mount.description)
                                put("path", mount.path)
                            },
                        )
                    }
                },
            )
        }
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
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

    private fun readMounts(): List<StorageMount> {
        if (!file.isFile) return emptyList()
        val root = runCatching { Json.parseToJsonElement(file.readText()).jsonObject }.getOrNull() ?: return emptyList()
        val array = runCatching { root["mounts"]?.jsonArray }.getOrNull() ?: return emptyList()
        return normalizeMounts(
            array.mapNotNull { element ->
                runCatching {
                    val item = element.jsonObject
                    StorageMount(
                        id = item["id"]?.jsonPrimitive?.content.orEmpty(),
                        name = item["name"]?.jsonPrimitive?.content.orEmpty(),
                        description = item["description"]?.jsonPrimitive?.content.orEmpty(),
                        path = item["path"]?.jsonPrimitive?.content.orEmpty(),
                    )
                }.getOrNull()
            },
        )
    }
}

internal fun normalizeMounts(mounts: List<StorageMount>): List<StorageMount> {
    val seen = LinkedHashSet<String>()
    return mounts.mapNotNull { mount ->
        val name = mount.name.trim().take(MAX_NAME_LENGTH)
        val description = mount.description.trim().take(MAX_DESCRIPTION_LENGTH)
        val normalizedPath = mount.path.trim().trimEnd('/')
        if (name.isEmpty()) return@mapNotNull null
        if (!isMountablePath(normalizedPath)) return@mapNotNull null
        if (!seen.add(normalizedPath)) return@mapNotNull null
        StorageMount(
            id = mount.id.trim().ifEmpty { UUID.randomUUID().toString() },
            name = name,
            description = description,
            path = normalizedPath,
        )
    }.take(MAX_MOUNTS)
}

internal fun isMountablePath(path: String): Boolean {
    val prefix = "$PUBLIC_STORAGE_ROOT/"
    return path.startsWith(prefix) && path.length > prefix.length && !path.contains("..")
}

internal fun mountDisplayPath(path: String): String =
    path.removePrefix("$PUBLIC_STORAGE_ROOT/").ifEmpty { path }

internal const val PUBLIC_STORAGE_ROOT = "/storage/emulated/0"
private const val MAX_MOUNTS = 20
private const val MAX_NAME_LENGTH = 32
private const val MAX_DESCRIPTION_LENGTH = 160
