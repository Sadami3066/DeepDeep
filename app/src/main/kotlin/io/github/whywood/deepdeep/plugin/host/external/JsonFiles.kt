package io.github.whywood.deepdeep.plugin.host.external

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

internal object JsonFiles {
    fun read(file: File): JsonObject? {
        if (!file.isFile) return null
        val text = runCatching { file.readText() }.getOrNull() ?: return null
        if (text.isBlank()) return null
        return runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
    }

    fun write(file: File, root: JsonObject) {
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

    fun backup(file: File, suffix: String): File? {
        if (!file.isFile) return null
        val target = File(file.parentFile, file.name + suffix)
        return runCatching {
            if (target.exists()) target.delete()
            file.copyTo(target, overwrite = true)
            target
        }.getOrNull()
    }
}
