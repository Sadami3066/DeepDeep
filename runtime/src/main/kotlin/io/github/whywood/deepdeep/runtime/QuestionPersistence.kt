package io.github.whywood.deepdeep.runtime

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

class QuestionPersistence(
    private val directory: File,
) {
    private val lock = Any()
    private val file = File(directory, FILE_NAME)

    internal fun load(): List<InterruptedQuestion> = synchronized(lock) { loadLocked() }

    internal fun append(question: InterruptedQuestion) {
        synchronized(lock) {
            write(loadLocked() + question)
        }
    }

    internal fun remove(requestId: String) {
        synchronized(lock) {
            val current = loadLocked()
            if (current.none { it.requestId == requestId }) return
            write(current.filterNot { it.requestId == requestId })
        }
    }

    internal fun clear() {
        synchronized(lock) {
            if (!file.isFile && !directory.exists()) return
            write(emptyList())
        }
    }

    private fun loadLocked(): List<InterruptedQuestion> {
        if (!file.isFile) return emptyList()
        val text = runCatching { file.readText() }.getOrNull() ?: return emptyList()
        if (text.isBlank()) return emptyList()
        val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject
            ?: return quarantine()
        val version = (root["schemaVersion"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
        val entries = root["questions"] as? JsonArray
        if (version != SCHEMA_VERSION || entries == null) return quarantine()
        return entries.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            runCatching { interruptedQuestionFromJson(item) }.getOrNull()
        }
    }

    private fun quarantine(): List<InterruptedQuestion> {
        runCatching {
            if (file.isFile) file.renameTo(File(directory, "$FILE_NAME.corrupt"))
        }
        return emptyList()
    }

    private fun write(questions: List<InterruptedQuestion>) {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("无法创建待答问题目录：${directory.absolutePath}")
        }
        val root = buildJsonObject {
            put("schemaVersion", SCHEMA_VERSION)
            putJsonArray("questions") { questions.forEach { add(it.toJsonObject()) } }
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

    private companion object {
        const val SCHEMA_VERSION = 1
        const val FILE_NAME = "pending.json"
    }
}

private fun InterruptedQuestion.toJsonObject(): JsonObject = buildJsonObject {
    put("requestId", requestId)
    put("sessionId", sessionId)
    put("toolCallId", toolCallId)
    put("createdAt", createdAt)
    putJsonArray("questions") { questions.forEach { add(it.toJsonObject()) } }
}

private fun UserQuestion.toJsonObject(): JsonObject = buildJsonObject {
    put("id", id)
    if (header == null) {
        put("header", JsonNull)
    } else {
        put("header", header)
    }
    put("question", question)
    put("multiSelect", multiSelect)
    putJsonArray("options") {
        options.forEach { option ->
            add(buildJsonObject {
                put("label", option.label)
                if (option.description == null) {
                    put("description", JsonNull)
                } else {
                    put("description", option.description)
                }
            })
        }
    }
}

private fun interruptedQuestionFromJson(json: JsonObject): InterruptedQuestion = InterruptedQuestion(
    requestId = json.requiredQuestionString("requestId"),
    sessionId = json.requiredQuestionString("sessionId"),
    toolCallId = json.requiredQuestionString("toolCallId"),
    questions = (json["questions"] as? JsonArray).orEmpty().map { element ->
        userQuestionFromJson(element as? JsonObject ?: throw IllegalArgumentException("question entry is not an object"))
    },
    createdAt = json.requiredQuestionLong("createdAt"),
)

private fun userQuestionFromJson(json: JsonObject): UserQuestion = UserQuestion(
    id = json.requiredQuestionString("id"),
    header = json.optionalQuestionString("header"),
    question = json.requiredQuestionString("question"),
    options = (json["options"] as? JsonArray).orEmpty().map { element ->
        val option = element as? JsonObject ?: throw IllegalArgumentException("option entry is not an object")
        QuestionOption(
            label = option.requiredQuestionString("label"),
            description = option.optionalQuestionString("description"),
        )
    },
    multiSelect = json.optionalQuestionString("multiSelect")?.toBooleanStrictOrNull() ?: false,
)

private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()

private fun JsonObject.requiredQuestionString(name: String): String =
    optionalQuestionString(name) ?: throw IllegalArgumentException("missing question field: $name")

private fun JsonObject.optionalQuestionString(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.requiredQuestionLong(name: String): Long =
    optionalQuestionString(name)?.toLongOrNull()
        ?: throw IllegalArgumentException("invalid question field: $name")
