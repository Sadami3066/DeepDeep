package io.github.whywood.deepdeep.runtime

import dev.pi.agent.AgentToolResult
import dev.pi.ai.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TodoToolsTest {
    private val posts = mutableListOf<Post>()

    private data class Post(
        val sessionId: String,
        val type: String,
        val payload: String,
        val text: String,
    )

    private fun tool(sessionId: String? = "session-1") = todoWriteTool(
        sessionId = { sessionId },
        post = { id, type, payload, text ->
            posts += Post(id, type, payload, text)
            true
        },
    )

    @Test
    fun todoWritePublishesWholeList() = runTest {
        val result = tool().execute(
            "call-1",
            buildJsonObject {
                put("todos", buildJsonArray {
                    add(todo("inspect code", "completed"))
                    add(todo("write patch", "in_progress"))
                    add(todo("run tests", "pending"))
                })
            },
            null,
            null,
        )
        assertNull(result.isError)
        assertEquals("Updated todo list: 1 pending, 1 in progress, 1 completed.", resultText(result))
        assertEquals(1, posts.size)
        val post = posts.single()
        assertEquals("session-1", post.sessionId)
        assertEquals("todo_write", post.type)
        assertEquals("Updated todo list: 1 pending, 1 in progress, 1 completed.", post.text)
        val todos = Json.parseToJsonElement(post.payload).jsonObject["todos"]!!.jsonArray
        assertEquals(3, todos.size)
        assertEquals("inspect code", todos[0].jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("completed", todos[0].jsonObject["status"]!!.jsonPrimitive.content)
        assertEquals("write patch", todos[1].jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("in_progress", todos[1].jsonObject["status"]!!.jsonPrimitive.content)
    }

    @Test
    fun invalidListsReturnErrorWithoutPosting() = runTest {
        val cases = listOf(
            buildJsonObject { put("todos", buildJsonArray { }) },
            buildJsonObject {
                put("todos", buildJsonArray {
                    add(todo("same", "pending"))
                    add(todo("same", "pending"))
                })
            },
            buildJsonObject {
                put("todos", buildJsonArray {
                    add(todo("first", "in_progress"))
                    add(todo("second", "in_progress"))
                })
            },
            buildJsonObject {
                put("todos", buildJsonArray { add(todo("bad status", "done")) })
            },
        )
        cases.forEach { args ->
            val result = tool().execute("call-1", args, null, null)
            assertTrue(result.isError == true)
            assertTrue(resultText(result).isNotBlank())
        }
        assertTrue(posts.isEmpty())
    }

    @Test
    fun missingSessionReturnsError() = runTest {
        val result = tool(sessionId = null).execute(
            "call-1",
            buildJsonObject {
                put("todos", buildJsonArray { add(todo("task", "pending")) })
            },
            null,
            null,
        )
        assertTrue(result.isError == true)
        assertTrue(resultText(result).contains("active session"))
        assertTrue(posts.isEmpty())
    }

    private fun todo(content: String, status: String): JsonObject = buildJsonObject {
        put("content", content)
        put("status", status)
    }

    private fun resultText(result: AgentToolResult): String =
        result.content.filterIsInstance<TextContent>().joinToString("") { it.text }
}
