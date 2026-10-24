package io.github.whywood.deepdeep.runtime

import dev.pi.agent.AgentToolResult
import dev.pi.ai.TextContent
import dev.pi.coding.tools.CodingTool
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

class WorkshopDataToolsTest {
    private lateinit var root: File
    private lateinit var store: WorkshopStore
    private lateinit var tools: Map<String, CodingTool>

    @Before
    fun setUp() {
        root = Files.createTempDirectory("deepdeep-workshop-data-tools").toFile()
        store = WorkshopStore(File(root, "workshop"))
        MemoryWorkshopData.reset()
        tools = workshopDataTools(store) { file -> MemoryWorkshopData(file) }.associateBy { it.name }
    }

    @After
    fun tearDown() {
        MemoryWorkshopData.reset()
        root.deleteRecursively()
    }

    private fun resultText(result: AgentToolResult): String =
        result.content.filterIsInstance<TextContent>().joinToString("") { it.text }

    private fun resultJson(result: AgentToolResult): JsonObject =
        Json.parseToJsonElement(resultText(result)).jsonObject

    private suspend fun execute(name: String, args: JsonObject): AgentToolResult =
        tools.getValue(name).execute("call-1", args, null, null)

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
        execute(
            "workshop_data_set",
            buildJsonObject {
                put("app", app)
                put("key", key)
                put("value", value)
            },
        )

    private suspend fun getValue(app: String, key: String): AgentToolResult =
        execute(
            "workshop_data_get",
            buildJsonObject {
                put("app", app)
                put("key", key)
            },
        )

    @Test
    fun catalogContainsTheFourDataTools() {
        assertEquals(
            setOf("workshop_data_get", "workshop_data_set", "workshop_data_delete", "workshop_data_keys"),
            tools.keys,
        )
        val get = tools.getValue("workshop_data_get").parameters
        assertEquals(listOf("app", "key"), get.getValue("required").jsonArray.map { it.jsonPrimitive.content })
        val set = tools.getValue("workshop_data_set").parameters
        assertEquals(
            listOf("app", "key", "value"),
            set.getValue("required").jsonArray.map { it.jsonPrimitive.content },
        )
        assertTrue(
            set.getValue("properties").jsonObject.getValue("value").jsonObject.isEmpty(),
        )
        assertEquals(
            listOf("app", "key"),
            tools.getValue("workshop_data_delete").parameters.getValue("required").jsonArray
                .map { it.jsonPrimitive.content },
        )
        assertEquals(
            listOf("app"),
            tools.getValue("workshop_data_keys").parameters.getValue("required").jsonArray
                .map { it.jsonPrimitive.content },
        )
        assertEquals(
            setOf("workshop_create", "workshop_load", "workshop_list"),
            workshopTools(store).map { it.name }.toSet(),
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
        val deleted = execute(
            "workshop_data_delete",
            buildJsonObject {
                put("app", app)
                put("key", "draft")
            },
        )
        assertNull(deleted.isError)
        assertEquals(
            "{\"ok\":true,\"app\":\"delete-app\",\"key\":\"draft\",\"deleted\":true}",
            resultText(deleted),
        )
        val again = execute(
            "workshop_data_delete",
            buildJsonObject {
                put("app", app)
                put("key", "draft")
            },
        )
        assertEquals(false, resultJson(again).getValue("deleted").jsonPrimitive.boolean)
        assertEquals(JsonNull, resultJson(getValue(app, "draft")).getValue("value"))
    }

    @Test
    fun keysAreSorted() = runTest {
        val app = createApp("Keys App")
        listOf("zebra", "alpha", "middle").forEach { key -> setValue(app, key, JsonPrimitive(key)) }
        val result = execute("workshop_data_keys", buildJsonObject { put("app", app) })
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
            resultText(execute("workshop_data_keys", buildJsonObject { put("app", emptyApp) })),
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
        assertEquals("workshop app not found: no-such-app", resultText(missing))

        val malformed = getValue("../escape", "key")
        assertEquals(true, malformed.isError)
        assertTrue(resultText(malformed).contains("not found"))

        createInvalidApp("broken-app")
        val invalid = execute(
            "workshop_data_keys",
            buildJsonObject { put("app", "broken-app") },
        )
        assertEquals(true, invalid.isError)
        assertTrue(resultText(invalid).contains("invalid"))
        assertTrue(resultText(invalid).contains("manifest_invalid"))
    }

    @Test
    fun invalidKeysAreErrors() = runTest {
        val app = createApp("Key Rules App")
        listOf("Key", "-key", "a".repeat(65), "has space").forEach { key ->
            val result = execute(
                "workshop_data_get",
                buildJsonObject {
                    put("app", app)
                    put("key", key)
                },
            )
            assertEquals(true, result.isError)
            assertTrue(resultText(result).contains("invalid key"))
        }
        val missingKey = execute("workshop_data_get", buildJsonObject { put("app", app) })
        assertEquals(true, missingKey.isError)
        assertTrue(resultText(missingKey).contains("key"))
    }

    @Test
    fun oversizeValueIsRejected() = runTest {
        val app = createApp("Size App")
        val oversize = setValue(app, "big", JsonPrimitive("x".repeat(256 * 1024)))
        assertEquals(true, oversize.isError)
        assertEquals("value must be at most 262144 characters", resultText(oversize))
        assertTrue(MemoryWorkshopData.instances.isEmpty())

        val allowed = setValue(app, "big", JsonPrimitive("x".repeat(262140)))
        assertNull(allowed.isError)
        val stored = resultJson(getValue(app, "big")).getValue("value").jsonPrimitive.content
        assertEquals(262140, stored.length)
    }

    @Test
    fun missingArgumentsAreErrors() = runTest {
        val missingApp = execute("workshop_data_get", buildJsonObject { put("key", "k") })
        assertEquals(true, missingApp.isError)
        assertTrue(resultText(missingApp).contains("app"))

        val app = createApp("Missing Value App")
        val missingValue = execute(
            "workshop_data_set",
            buildJsonObject {
                put("app", app)
                put("key", "k")
            },
        )
        assertEquals(true, missingValue.isError)
        assertTrue(resultText(missingValue).contains("value"))
    }

    @Test
    fun dataStoreIsClosedAfterEachCall() = runTest {
        val app = createApp("Close App")
        setValue(app, "alpha", JsonPrimitive("a"))
        getValue(app, "alpha")
        assertEquals(2, MemoryWorkshopData.instances.size)
        assertTrue(MemoryWorkshopData.instances.all { it.closed })
        assertTrue(MemoryWorkshopData.instances.all { it.file == store.dataFile("close-app") })
    }

    private class MemoryWorkshopData(val file: File) : WorkshopData {
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
            val instances = mutableListOf<MemoryWorkshopData>()

            fun reset() {
                data.clear()
                instances.clear()
            }
        }
    }
}
