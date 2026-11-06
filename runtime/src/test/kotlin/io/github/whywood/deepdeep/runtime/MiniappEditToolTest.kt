package io.github.whywood.deepdeep.runtime

import dev.pi.agent.AgentToolResult
import dev.pi.ai.TextContent
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MiniappEditToolTest {
    private lateinit var root: File
    private lateinit var store: MiniappStore
    private lateinit var tool: dev.pi.coding.tools.CodingTool

    @Before
    fun setUp() {
        root = Files.createTempDirectory("deepdeep-miniapp-edit-tool").toFile()
        store = MiniappStore(File(root, "miniapp"))
        MemoryMiniappData.reset()
        tool = miniappEditTool(store) { file -> MemoryMiniappData(file) }
    }

    @After
    fun tearDown() {
        MemoryMiniappData.reset()
        root.deleteRecursively()
    }

    private fun resultText(result: AgentToolResult): String =
        result.content.filterIsInstance<TextContent>().joinToString("") { it.text }

    private fun resultJson(result: AgentToolResult): JsonObject =
        Json.parseToJsonElement(resultText(result)).jsonObject

    private suspend fun execute(args: JsonObject): AgentToolResult =
        tool.execute("call-1", args, null, null)

    private suspend fun execute(
        action: String,
        app: String,
        key: String? = null,
        value: JsonElement? = null,
    ): AgentToolResult = execute(
        buildJsonObject {
            put("action", action)
            put("app", app)
            key?.let { put("key", it) }
            value?.let { put("value", it) }
        },
    )

    private suspend fun createApp(name: String): String {
        val draft = store.createDraft(name)
        File(draft.directory, "index.html").writeText("<!DOCTYPE html><html><body>hi</body></html>")
        return store.loadDraft(draft.directory.absolutePath).manifest.id
    }

    private fun createInvalidApp(id: String) {
        val directory = File(store.appsDir, id)
        directory.mkdirs()
        File(directory, "manifest.json").writeText("{oops")
        File(directory, "index.html").writeText("<html></html>")
    }

    private suspend fun setValue(app: String, key: String, value: JsonElement): AgentToolResult =
        execute("set", app, key, value)

    private suspend fun getValue(app: String, key: String): AgentToolResult =
        execute("get", app, key)

    @Test
    fun catalogContainsSingleEditTool() {
        assertEquals("miniapp_edit", tool.name)
        val parameters = tool.parameters
        assertEquals(
            listOf("action", "app"),
            parameters.getValue("required").jsonArray.map { it.jsonPrimitive.content },
        )
        val properties = parameters.getValue("properties").jsonObject
        assertEquals(setOf("action", "app", "key", "value"), properties.keys)
        assertEquals(
            listOf("get", "set", "delete", "keys"),
            properties.getValue("action").jsonObject.getValue("enum").jsonArray.map { it.jsonPrimitive.content },
        )
        assertTrue(properties.getValue("value").jsonObject["type"] == null)
        assertEquals(
            setOf("miniapp_create", "miniapp_load", "miniapp_list", "miniapp_delete"),
            miniappTools(store).map { it.name }.toSet(),
        )
    }

    @Test
    fun setThenGetArbitraryJson() = runTest {
        val app = createApp("Data App")
        val value = Json.parseToJsonElement(
            """{"courses":[{"name":"Math","done":false}],"count":3,"active":true,"note":null}""",
        )
        val setResult = setValue(app, "state", value)
        assertNull(setResult.isError)
        assertEquals("{\"ok\":true,\"app\":\"data-app\",\"key\":\"state\"}", resultText(setResult))

        val getResult = getValue(app, "state")
        assertNull(getResult.isError)
        assertEquals(
            "{\"ok\":true,\"app\":\"data-app\",\"key\":\"state\",\"value\":$value}",
            resultText(getResult),
        )
        val json = resultJson(getResult)
        assertTrue(json.getValue("ok").jsonPrimitive.boolean)
        assertEquals("data-app", json.getValue("app").jsonPrimitive.content)
        assertEquals("state", json.getValue("key").jsonPrimitive.content)
        assertEquals(value, json.getValue("value"))
    }

    @Test
    fun setOverwritesExistingValue() = runTest {
        val app = createApp("Overwrite App")
        assertNull(setValue(app, "title", JsonPrimitive("first")).isError)
        assertNull(setValue(app, "title", JsonPrimitive("second")).isError)
        assertEquals(JsonPrimitive("second"), resultJson(getValue(app, "title")).getValue("value"))
        setValue(app, "count", JsonPrimitive(7))
        assertEquals(Json.parseToJsonElement("7"), resultJson(getValue(app, "count")).getValue("value"))
    }

    @Test
    fun deleteReturnsDeletedFlag() = runTest {
        val app = createApp("Delete App")
        setValue(app, "draft", JsonPrimitive("text"))
        val deleted = execute("delete", app, "draft")
        assertNull(deleted.isError)
        assertEquals(
            "{\"ok\":true,\"app\":\"delete-app\",\"key\":\"draft\",\"deleted\":true}",
            resultText(deleted),
        )
        val again = execute("delete", app, "draft")
        assertEquals(false, resultJson(again).getValue("deleted").jsonPrimitive.boolean)
        assertEquals(JsonNull, resultJson(getValue(app, "draft")).getValue("value"))
    }

    @Test
    fun keysAreSorted() = runTest {
        val app = createApp("Keys App")
        listOf("zebra", "alpha", "middle").forEach { key -> setValue(app, key, JsonPrimitive(key)) }
        val result = execute("keys", app)
        assertNull(result.isError)
        assertEquals(
            "{\"ok\":true,\"app\":\"keys-app\",\"keys\":[\"alpha\",\"middle\",\"zebra\"]}",
            resultText(result),
        )
        assertEquals(
            listOf("alpha", "middle", "zebra"),
            resultJson(result).getValue("keys").jsonArray.map { it.jsonPrimitive.content },
        )
        val emptyApp = createApp("Empty App")
        assertEquals(
            "{\"ok\":true,\"app\":\"empty-app\",\"keys\":[]}",
            resultText(execute("keys", emptyApp)),
        )
    }

    @Test
    fun getMissingKeyReturnsNull() = runTest {
        val app = createApp("Missing Key App")
        val result = getValue(app, "absent")
        assertNull(result.isError)
        assertEquals(
            "{\"ok\":true,\"app\":\"missing-key-app\",\"key\":\"absent\",\"value\":null}",
            resultText(result),
        )
        assertEquals(JsonNull, resultJson(result).getValue("value"))
    }

    @Test
    fun invalidAppIdsAreErrors() = runTest {
        val missing = getValue("no-such-app", "key")
        assertEquals(true, missing.isError)
        assertEquals("miniapp not found: no-such-app", resultText(missing))

        val malformed = getValue("../escape", "key")
        assertEquals(true, malformed.isError)
        assertTrue(resultText(malformed).contains("not found"))

        createInvalidApp("broken-app")
        val invalid = execute("keys", "broken-app")
        assertEquals(true, invalid.isError)
        assertTrue(resultText(invalid).contains("invalid"))
        assertTrue(resultText(invalid).contains("manifest_invalid"))
    }

    @Test
    fun invalidKeysAreErrors() = runTest {
        val app = createApp("Key Rules App")
        listOf("Key", "-key", "a".repeat(65), "has space").forEach { key ->
            val result = getValue(app, key)
            assertEquals(true, result.isError)
            assertTrue(resultText(result).contains("invalid key"))
        }
    }

    @Test
    fun keyIsRequiredForGetSetAndDelete() = runTest {
        val app = createApp("Missing Key Argument App")
        listOf("get", "set", "delete").forEach { action ->
            val result = execute(action, app)
            assertEquals(true, result.isError)
            assertTrue(resultText(result).contains("key"))
        }
    }

    @Test
    fun valueIsRequiredForSet() = runTest {
        val app = createApp("Missing Value App")
        val result = execute("set", app, "k")
        assertEquals(true, result.isError)
        assertTrue(resultText(result).contains("value"))
    }

    @Test
    fun actionIsRequiredAndValidated() = runTest {
        val app = createApp("Action App")
        val missing = execute(buildJsonObject { put("app", app) })
        assertEquals(true, missing.isError)
        assertTrue(resultText(missing).contains("action"))

        val unknown = execute("rename", app)
        assertEquals(true, unknown.isError)
        assertEquals("action must be get, set, delete or keys", resultText(unknown))
    }

    @Test
    fun appIsRequired() = runTest {
        val result = execute(buildJsonObject { put("action", "keys") })
        assertEquals(true, result.isError)
        assertTrue(resultText(result).contains("app"))
    }

    @Test
    fun oversizeValueIsRejected() = runTest {
        val app = createApp("Size App")
        val oversize = setValue(app, "big", JsonPrimitive("x".repeat(256 * 1024)))
        assertEquals(true, oversize.isError)
        assertEquals("value must be at most 262144 characters", resultText(oversize))
        assertTrue(MemoryMiniappData.instances.isEmpty())

        val allowed = setValue(app, "big", JsonPrimitive("x".repeat(262140)))
        assertNull(allowed.isError)
        val stored = resultJson(getValue(app, "big")).getValue("value").jsonPrimitive.content
        assertEquals(262140, stored.length)
    }

    @Test
    fun dataStoreIsClosedAfterEachCall() = runTest {
        val app = createApp("Close App")
        setValue(app, "alpha", JsonPrimitive("a"))
        getValue(app, "alpha")
        assertEquals(2, MemoryMiniappData.instances.size)
        assertTrue(MemoryMiniappData.instances.all { it.closed })
        assertTrue(MemoryMiniappData.instances.all { it.file == store.dataFile("close-app") })
    }

    private class MemoryMiniappData(val file: File) : MiniappData {
        var closed = false
            private set

        init {
            instances += this
        }

        override suspend fun get(key: String): JsonElement? = values()[key]

        override suspend fun set(key: String, value: JsonElement) {
            values()[key] = value
        }

        override suspend fun remove(key: String): Boolean = values().remove(key) != null

        override suspend fun keys(): List<String> = values().keys.toList()

        override fun close() {
            closed = true
        }

        private fun values(): MutableMap<String, JsonElement> =
            data.getOrPut(file.canonicalPath) { mutableMapOf() }

        companion object {
            private val data = mutableMapOf<String, MutableMap<String, JsonElement>>()
            val instances = mutableListOf<MemoryMiniappData>()

            fun reset() {
                data.clear()
                instances.clear()
            }
        }
    }
}
