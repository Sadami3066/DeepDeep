package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.sdk.agent.AgentToolResult
import io.github.whywood.deepdeep.sdk.coding.tools.CodingTool
import io.github.whywood.deepdeep.sdk.coding.tools.textResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

private enum class TodoStatus(val wire: String) {
    PENDING("pending"),
    IN_PROGRESS("in_progress"),
    COMPLETED("completed"),
}

private data class TodoEntry(val content: String, val status: TodoStatus)

private val todoParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("todos") {
            put("type", "array")
            put("description", "The complete task list, replacing the previous list.")
            putJsonObject("items") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("content") {
                        put("type", "string")
                        put("description", "Short task description.")
                    }
                    putJsonObject("status") {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            add(JsonPrimitive("pending"))
                            add(JsonPrimitive("in_progress"))
                            add(JsonPrimitive("completed"))
                        })
                    }
                }
                put("required", buildJsonArray {
                    add(JsonPrimitive("content"))
                    add(JsonPrimitive("status"))
                })
            }
        }
    }
    put("required", buildJsonArray { add(JsonPrimitive("todos")) })
}

fun todoWriteTool(
    sessionId: () -> String?,
    post: suspend (String, String, String, String) -> Boolean,
): CodingTool = CodingTool(
    name = "todo_write",
    description = "Update the task list for this session. Send the complete list every time; omitted items are removed.",
    parameters = todoParameters,
    label = "todo_write",
    promptSnippet = "todo_write: maintain the session task list with pending, in-progress and completed items.",
    promptGuidelines = listOf(
        "Use todo_write for multi-step work so the user can follow progress.",
        "Send the complete list on every call; keep at most one item in_progress.",
    ),
    execute = { _, args, _, _ ->
        try {
            val todos = parseTodos(args)
            val id = sessionId() ?: error("todo_write requires an active session")
            val payload = buildJsonObject {
                put("todos", buildJsonArray {
                    todos.forEach { todo ->
                        add(buildJsonObject {
                            put("content", todo.content)
                            put("status", todo.status.wire)
                        })
                    }
                })
            }
            val text = summary(todos)
            if (!post(id, "todo_write", payload.toString(), text)) {
                error("todo_write could not be saved")
            }
            textResult(text)
        } catch (error: Exception) {
            textResult(error.message ?: "todo_write failed", isError = true)
        }
    },
)

private fun parseTodos(args: JsonObject): List<TodoEntry> {
    val raw = args["todos"] as? JsonArray ?: error("todos must be an array")
    if (raw.isEmpty()) error("todos must not be empty")
    val seen = HashSet<String>()
    var inProgress = 0
    val result = raw.mapIndexed { index, element ->
        val item = element as? JsonObject ?: error("todos[$index] must be an object")
        val content = item.string("content")?.trim().orEmpty()
        if (content.isEmpty()) error("todos[$index].content must not be empty")
        if (!seen.add(content)) error("duplicate todo: $content")
        val status = when (val wire = item.string("status")) {
            TodoStatus.PENDING.wire -> TodoStatus.PENDING
            TodoStatus.IN_PROGRESS.wire -> TodoStatus.IN_PROGRESS.also { inProgress++ }
            TodoStatus.COMPLETED.wire -> TodoStatus.COMPLETED
            else -> error("todos[$index].status is invalid: ${wire ?: "missing"}")
        }
        TodoEntry(content, status)
    }
    if (inProgress > 1) error("at most one todo may be in_progress")
    if (result.isEmpty()) error("todos must not be empty")
    return result
}

private fun summary(todos: List<TodoEntry>): String {
    val pending = todos.count { it.status == TodoStatus.PENDING }
    val active = todos.count { it.status == TodoStatus.IN_PROGRESS }
    val completed = todos.count { it.status == TodoStatus.COMPLETED }
    return "Updated todo list: $pending pending, $active in progress, $completed completed."
}

private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull
