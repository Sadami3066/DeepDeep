package io.github.whywood.deepdeep.data

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class CapabilitySettingsStore(
    private val file: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()
    private val _screenOps = MutableStateFlow(readValues()[SCREEN_OPS_KEY] ?: false)

    val screenOps: StateFlow<Boolean> = _screenOps.asStateFlow()

    suspend fun setScreenOps(enabled: Boolean) = mutex.withLock {
        withContext(ioDispatcher) {
            _screenOps.value = enabled
            write(SCREEN_OPS_KEY, enabled)
        }
    }

    suspend fun toggleScreenOps(): Boolean = mutex.withLock {
        withContext(ioDispatcher) {
            val next = !_screenOps.value
            _screenOps.value = next
            write(SCREEN_OPS_KEY, next)
            next
        }
    }

    private fun write(key: String, value: Boolean) {
        val root = buildJsonObject {
            readValues().forEach { (existingKey, existingValue) -> put(existingKey, existingValue) }
            put(key, value)
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

    private fun readValues(): Map<String, Boolean> {
        if (!file.isFile) return emptyMap()
        val root = runCatching { Json.parseToJsonElement(file.readText()).jsonObject }.getOrNull() ?: return emptyMap()
        return root.mapNotNull { (key, element) ->
            runCatching { element.jsonPrimitive.booleanOrNull }.getOrNull()?.let { key to it }
        }.toMap()
    }

    private companion object {
        const val SCREEN_OPS_KEY = "screenOps"
    }
}
