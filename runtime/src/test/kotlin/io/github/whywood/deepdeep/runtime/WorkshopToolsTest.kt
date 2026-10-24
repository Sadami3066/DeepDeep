package io.github.whywood.deepdeep.runtime

import dev.pi.agent.AgentToolResult
import dev.pi.ai.TextContent
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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

class WorkshopToolsTest {
    private lateinit var root: File
    private lateinit var store: WorkshopStore
    private lateinit var tools: Map<String, dev.pi.coding.tools.CodingTool>

    @Before
    fun setUp() {
        root = Files.createTempDirectory("deepdeep-workshop-tools").toFile()
        store = WorkshopStore(File(root, "workshop"))
        tools = workshopTools(store).associateBy { it.name }
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun resultText(result: AgentToolResult): String =
        result.content.filterIsInstance<TextContent>().joinToString("") { it.text }

    private fun resultJson(result: AgentToolResult): JsonObject =
        Json.parseToJsonElement(resultText(result)).jsonObject

    private suspend fun execute(name: String, args: JsonObject): AgentToolResult =
        tools.getValue(name).execute("call-1", args, null, null)

    @Test
    fun catalogContainsTheThreeTools() {
        assertEquals(setOf("workshop_create", "workshop_register", "workshop_list"), tools.keys)
        val create = tools.getValue("workshop_create").parameters
        assertEquals(
            listOf("name", "type"),
            create.getValue("required").jsonArray.map { it.jsonPrimitive.content },
        )
        val properties = create.getValue("properties").jsonObject
        assertEquals(setOf("name", "type", "description", "icon"), properties.keys)
        assertEquals(
            listOf("board", "workstation", "workflow"),
            properties.getValue("type").jsonObject.getValue("enum").jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(
            listOf("path"),
            tools.getValue("workshop_register").parameters.getValue("required").jsonArray
                .map { it.jsonPrimitive.content },
        )
        assertTrue(tools.getValue("workshop_list").parameters.getValue("properties").jsonObject.isEmpty())
    }

    @Test
    fun createReturnsDraftJson() = runTest {
        val result = execute(
            "workshop_create",
            buildJsonObject {
                put("name", "Weekly Board")
                put("type", "board")
                put("description", "A demo board")
            },
        )
        assertNull(result.isError)
        val json = resultJson(result)
        assertTrue(json.getValue("ok").jsonPrimitive.boolean)
        val draft = json.getValue("draft").jsonObject
        assertEquals("weekly-board", draft.getValue("id").jsonPrimitive.content)
        assertEquals("Weekly Board", draft.getValue("name").jsonPrimitive.content)
        assertEquals("board", draft.getValue("type").jsonPrimitive.content)
        val path = draft.getValue("path").jsonPrimitive.content
        assertTrue(File(path).isDirectory)
        val next = json.getValue("next").jsonPrimitive.content
        assertTrue(next.contains("$path/AGENTS.md"))
        assertTrue(next.contains("workshop_register with path=$path"))
        assertEquals(1, store.drafts().size)
    }

    @Test
    fun createRejectsBadArguments() = runTest {
        val missingType = execute("workshop_create", buildJsonObject { put("name", "No Type") })
        assertEquals(true, missingType.isError)
        assertTrue(resultText(missingType).isNotBlank())

        val badType = execute(
            "workshop_create",
            buildJsonObject {
                put("name", "Bad Type")
                put("type", "game")
            },
        )
        assertEquals(true, badType.isError)
        assertTrue(resultText(badType).contains("type"))

        val missingName = execute("workshop_create", buildJsonObject { put("type", "board") })
        assertEquals(true, missingName.isError)
        assertTrue(resultText(missingName).contains("name"))
    }

    @Test
    fun registerInstallsTheDraft() = runTest {
        val draft = store.createDraft("Reg Board", WorkshopType.WORKSTATION)
        val result = execute(
            "workshop_register",
            buildJsonObject { put("path", draft.directory.absolutePath) },
        )
        assertNull(result.isError)
        val json = resultJson(result)
        assertTrue(json.getValue("ok").jsonPrimitive.boolean)
        val app = json.getValue("app").jsonObject
        assertEquals("reg-board", app.getValue("id").jsonPrimitive.content)
        assertEquals("Reg Board", app.getValue("name").jsonPrimitive.content)
        assertEquals("workstation", app.getValue("type").jsonPrimitive.content)
        assertEquals("🧰", app.getValue("icon").jsonPrimitive.content)
        assertEquals("1.0.0", app.getValue("version").jsonPrimitive.content)
        assertEquals(File(store.appsDir, "reg-board").absolutePath, json.getValue("path").jsonPrimitive.content)
        assertTrue(store.load("reg-board") is WorkshopEntry.Valid)
        assertTrue(store.drafts().isEmpty())
    }

    @Test
    fun registerRejectsMissingOrInvalidPaths() = runTest {
        val missingArgument = execute("workshop_register", buildJsonObject {})
        assertEquals(true, missingArgument.isError)
        assertTrue(resultText(missingArgument).contains("path"))

        val missingDraft = execute(
            "workshop_register",
            buildJsonObject { put("path", File(root, "nope").absolutePath) },
        )
        assertEquals(true, missingDraft.isError)

        val draft = store.createDraft("No Entry", WorkshopType.BOARD)
        File(draft.directory, "index.html").delete()
        val brokenDraft = execute(
            "workshop_register",
            buildJsonObject { put("path", draft.directory.absolutePath) },
        )
        assertEquals(true, brokenDraft.isError)
        assertTrue(resultText(brokenDraft).contains("entry"))
    }

    @Test
    fun listReturnsAppsAndDrafts() = runTest {
        val empty = resultJson(execute("workshop_list", buildJsonObject {}))
        assertEquals(0, empty.getValue("apps").jsonArray.size)
        assertEquals(0, empty.getValue("drafts").jsonArray.size)

        store.createInstalled("Listed App", WorkshopType.BOARD)
        val draft = store.createDraft("Pending Draft", WorkshopType.WORKFLOW)
        val broken = File(store.buildDir, "broken-abcde").also { it.mkdirs() }
        File(broken, "manifest.json").writeText("{oops")

        val json = resultJson(execute("workshop_list", buildJsonObject {}))
        val apps = json.getValue("apps").jsonArray
        assertEquals(1, apps.size)
        val app = apps[0].jsonObject
        assertEquals(
            setOf("id", "name", "type", "icon", "version", "path", "description", "permissions"),
            app.keys,
        )
        assertEquals("listed-app", app.getValue("id").jsonPrimitive.content)
        assertEquals("Listed App", app.getValue("name").jsonPrimitive.content)
        assertEquals("board", app.getValue("type").jsonPrimitive.content)
        assertEquals(
            setOf("agent.run", "data.kv"),
            app.getValue("permissions").jsonArray.map { it.jsonPrimitive.content }.toSet(),
        )

        val drafts = json.getValue("drafts").jsonArray
        assertEquals(2, drafts.size)
        val draftJson = drafts.map { it.jsonObject }.first {
            it.getValue("path").jsonPrimitive.content == draft.directory.absolutePath
        }
        assertEquals(setOf("path", "id", "name", "type"), draftJson.keys)
        assertEquals("pending-draft", draftJson.getValue("id").jsonPrimitive.content)
        assertEquals("workflow", draftJson.getValue("type").jsonPrimitive.content)
        val brokenJson = drafts.map { it.jsonObject }.first {
            it.getValue("path").jsonPrimitive.content == broken.absolutePath
        }
        assertEquals(setOf("path"), brokenJson.keys)
    }

    @Test
    fun createThenRegisterFlowProducesListedApp() = runTest {
        val createResult = execute(
            "workshop_create",
            buildJsonObject {
                put("name", "Flow App")
                put("type", "workflow")
            },
        )
        assertNull(createResult.isError)
        val path = resultJson(createResult).getValue("draft").jsonObject
            .getValue("path").jsonPrimitive.content
        val registerResult = execute("workshop_register", buildJsonObject { put("path", path) })
        assertNull(registerResult.isError)
        val listed = resultJson(execute("workshop_list", buildJsonObject {}))
        val ids = listed.getValue("apps").jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content }
        assertEquals(listOf("flow-app"), ids)
        assertEquals(0, listed.getValue("drafts").jsonArray.size)
    }
}
