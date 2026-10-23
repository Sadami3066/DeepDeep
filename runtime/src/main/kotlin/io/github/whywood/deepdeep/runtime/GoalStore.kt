package io.github.whywood.deepdeep.runtime

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

class CorruptGoalDataException(message: String, cause: Throwable? = null) : Exception(message, cause)

class GoalStore(
    private val directory: File,
) {
    private val lock = Any()
    private val file = File(directory, FILE_NAME)

    fun read(): Map<String, Goal> = synchronized(lock) { load() }

    fun replace(goals: Map<String, Goal>) = synchronized(lock) { persist(goals) }

    fun remove(sessionId: String): Boolean = synchronized(lock) {
        val current = load()
        if (!current.containsKey(sessionId)) return@synchronized false
        persist(current - sessionId)
        true
    }

    private fun load(): Map<String, Goal> {
        if (!file.exists()) return emptyMap()
        val text = try {
            file.readText()
        } catch (error: Exception) {
            throw CorruptGoalDataException("Unable to read goal data", error)
        }
        if (text.isBlank()) return emptyMap()
        val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject
            ?: throw CorruptGoalDataException("Goal data is not a JSON object")
        return root.mapValues { (sessionId, element) ->
            val item = element as? JsonObject
                ?: throw CorruptGoalDataException("Goal entry for $sessionId is not an object")
            goalFromJson(item)
        }
    }

    private fun persist(goals: Map<String, Goal>) {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("Unable to create goal directory: ${directory.absolutePath}")
        }
        val root = buildJsonObject {
            goals.forEach { (sessionId, goal) -> put(sessionId, goal.toJson()) }
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

    companion object {
        private const val FILE_NAME = "goals.json"
    }
}

private fun Goal.toJson(): JsonObject = buildJsonObject {
    put("id", id)
    put("revision", revision)
    put("objective", objective)
    put("phase", phase.name)
    put("roundsStarted", roundsStarted)
    put("maxRounds", maxRounds)
    if (blockedReason == null) {
        put("blockedReason", JsonNull)
    } else {
        put("blockedReason", blockedReason)
    }
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
}

private fun goalFromJson(json: JsonObject): Goal = Goal(
    id = json.requiredString("id"),
    revision = json.requiredInt("revision"),
    objective = json.requiredString("objective"),
    phase = json.requiredPhase("phase"),
    roundsStarted = json.requiredInt("roundsStarted"),
    maxRounds = json.requiredInt("maxRounds"),
    blockedReason = json.stringOrNull("blockedReason"),
    createdAt = json.requiredLong("createdAt"),
    updatedAt = json.requiredLong("updatedAt"),
)

private fun JsonObject.stringOrNull(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.requiredString(name: String): String =
    stringOrNull(name) ?: throw CorruptGoalDataException("Missing goal field: $name")

private fun JsonObject.requiredInt(name: String): Int =
    stringOrNull(name)?.toIntOrNull() ?: throw CorruptGoalDataException("Invalid goal field: $name")

private fun JsonObject.requiredLong(name: String): Long =
    stringOrNull(name)?.toLongOrNull() ?: throw CorruptGoalDataException("Invalid goal field: $name")

private fun JsonObject.requiredPhase(name: String): GoalPhase {
    val raw = requiredString(name)
    return runCatching { GoalPhase.valueOf(raw) }
        .getOrElse { throw CorruptGoalDataException("Invalid goal phase: $raw") }
}
