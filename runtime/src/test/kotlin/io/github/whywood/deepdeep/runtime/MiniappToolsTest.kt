package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.sdk.agent.AgentToolResult
import io.github.whywood.deepdeep.sdk.ai.TextContent
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

class MiniappToolsTest {
    private lateinit var root: File
    private lateinit var store: MiniappStore
    private lateinit var tools: Map<String, io.github.whywood.deepdeep.sdk.coding.tools.CodingTool>

    @Before
    fun setUp() {
        root = Files.createTempDirectory("deepdeep-miniapp-tools").toFile()
        store = MiniappStore(File(root, "miniapp"))
        tools = miniappTools(store).associateBy { it.name }
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

    private suspend fun createDraft(name: String): String {
        val result = execute("miniapp_create", buildJsonObject { put("name", name) })
        assertNull(result.isError)
        return resultJson(result).getValue("draft").jsonObject.getValue("path").jsonPrimitive.content
    }

    @Test
    fun catalogContainsTheFourTools() {
        assertEquals(
            setOf("miniapp_create", "miniapp_load", "miniapp_list", "miniapp_delete"),
            tools.keys,
        )
        val create = tools.getValue("miniapp_create").parameters
        assertEquals(listOf("name"), create.getValue("required").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(setOf("name", "description", "icon"), create.getValue("properties").jsonObject.keys)
        val load = tools.getValue("miniapp_load").parameters
        assertEquals(listOf("path"), load.getValue("required").jsonArray.map { it.jsonPrimitive.content })
        assertTrue(tools.getValue("miniapp_list").parameters.getValue("properties").jsonObject.isEmpty())
        val delete = tools.getValue("miniapp_delete").parameters
        assertEquals(listOf("app"), delete.getValue("required").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(setOf("app"), delete.getValue("properties").jsonObject.keys)
    }

    @Test
    fun oneShotLoadReturnsArtifactWithoutChatDeliveryInstructions() = runTest {
        val path = createDraft("Page Artifact")
        File(path, "index.html").writeText("<html><body>neutral</body></html>")
        val tool = miniappTools(store, supportsChatDelivery = false).first { it.name == "miniapp_load" }
        val result = tool.execute("page", buildJsonObject { put("path", path) }, null, null)
        assertNull(resultText(result), result.isError)
        val json = resultJson(result)
        assertTrue(!json.containsKey("delivery"))
        assertTrue(!resultText(result).contains("present"))
        assertTrue(File(json.getValue("path").jsonPrimitive.content, "index.html").isFile)
        assertTrue(json.getValue("next").jsonPrimitive.content.contains("calling page"))
    }

    @Test
    fun createReturnsDraftJson() = runTest {
        val result = execute(
            "miniapp_create",
            buildJsonObject {
                put("name", "Weekly Board")
                put("description", "A demo board")
                put("icon", "🗂️")
            },
        )
        assertNull(result.isError)
        val json = resultJson(result)
        assertTrue(json.getValue("ok").jsonPrimitive.boolean)
        val draft = json.getValue("draft").jsonObject
        assertEquals(setOf("id", "name", "path"), draft.keys)
        assertEquals("weekly-board", draft.getValue("id").jsonPrimitive.content)
        assertEquals("Weekly Board", draft.getValue("name").jsonPrimitive.content)
        val path = draft.getValue("path").jsonPrimitive.content
        assertTrue(File(path).isDirectory)
        assertEquals(listOf("manifest.json"), File(path).listFiles()!!.map { it.name })
        val next = json.getValue("next").jsonPrimitive.content
        assertTrue(next.contains("Write the app files under $path"))
        assertTrue(next.contains("entry defaults to index.html"))
        assertTrue(next.contains("miniapp_load with path=$path"))
        val manifest = MiniappManifest.parse(File(path, "manifest.json").readText())
        assertEquals("🗂️", manifest.icon)
        assertEquals("A demo board", manifest.description)
        val expected = "{\"ok\":true,\"draft\":{\"id\":\"weekly-board\",\"name\":\"Weekly Board\",\"path\":\"$path\"},\"next\":\"Write the app files under $path with absolute paths using read/write/edit (entry defaults to index.html). Follow the miniapp skill. Then call miniapp_load with path=$path.\"}"
        assertEquals(expected, resultText(result))
    }

    @Test
    fun createRejectsBadArguments() = runTest {
        val missingName = execute("miniapp_create", buildJsonObject {})
        assertEquals(true, missingName.isError)
        assertTrue(resultText(missingName).contains("name"))

        val longName = execute("miniapp_create", buildJsonObject { put("name", "a".repeat(41)) })
        assertEquals(true, longName.isError)
        assertTrue(resultText(longName).contains("name"))

        val badIcon = execute(
            "miniapp_create",
            buildJsonObject {
                put("name", "Bad Icon")
                put("icon", "123456789")
            },
        )
        assertEquals(true, badIcon.isError)
        assertTrue(resultText(badIcon).contains("icon"))
    }

    @Test
    fun createWriteLoadFlow() = runTest {
        val path = createDraft("Flow App")
        File(path, "index.html").writeText("<!DOCTYPE html><html><body>hi</body></html>")
        File(path, "app.js").writeText("console.log('hi');")
        val result = execute("miniapp_load", buildJsonObject { put("path", path) })
        assertNull(result.isError)
        val json = resultJson(result)
        assertTrue(json.getValue("ok").jsonPrimitive.boolean)
        val app = json.getValue("app").jsonObject
        assertEquals(setOf("id", "name", "icon", "version"), app.keys)
        assertEquals("flow-app", app.getValue("id").jsonPrimitive.content)
        assertEquals("Flow App", app.getValue("name").jsonPrimitive.content)
        assertEquals("", app.getValue("icon").jsonPrimitive.content)
        assertEquals("1.0.0", app.getValue("version").jsonPrimitive.content)
        val appDirectory = File(store.appsDir, "flow-app")
        assertEquals(appDirectory.absolutePath, json.getValue("path").jsonPrimitive.content)
        assertEquals("present", json.getValue("delivery").jsonPrimitive.content)
        assertEquals(
            "Deliver this app with present: {items:[{type:miniapp, id:flow-app}]}. Do not restate the app details in your reply.",
            json.getValue("next").jsonPrimitive.content,
        )
        assertTrue(File(appDirectory, "index.html").isFile)
        assertTrue(File(appDirectory, "app.js").isFile)
        assertTrue(store.load("flow-app") is MiniappEntry.Valid)
        assertTrue(store.drafts().isEmpty())
        val expected = "{\"ok\":true,\"delivery\":\"present\",\"app\":{\"id\":\"flow-app\",\"name\":\"Flow App\",\"icon\":\"\",\"version\":\"1.0.0\"},\"path\":\"${appDirectory.absolutePath}\",\"next\":\"Deliver this app with present: {items:[{type:miniapp, id:flow-app}]}. Do not restate the app details in your reply.\"}"
        assertEquals(expected, resultText(result))
    }

    @Test
    fun loadRejectsMissingOrInvalidPaths() = runTest {
        val missingArgument = execute("miniapp_load", buildJsonObject {})
        assertEquals(true, missingArgument.isError)
        assertTrue(resultText(missingArgument).contains("path"))

        val missingDraft = execute(
            "miniapp_load",
            buildJsonObject { put("path", File(root, "nope").absolutePath) },
        )
        assertEquals(true, missingDraft.isError)

        val path = createDraft("No Entry")
        val noEntry = execute("miniapp_load", buildJsonObject { put("path", path) })
        assertEquals(true, noEntry.isError)
        assertTrue(resultText(noEntry).contains("entry"))
    }

    @Test
    fun loadRejectsRelativePathWithAbsolutePathHint() = runTest {
        val result = execute(
            "miniapp_load",
            buildJsonObject { put("path", "miniapp/build/my-app-abcde") },
        )
        assertEquals(true, result.isError)
        val text = resultText(result)
        assertTrue(text.contains("must be absolute"))
        assertTrue(text.contains(store.buildDir.absolutePath))
        assertTrue(text.contains("my-app-abcde"))
    }

    @Test
    fun loadMissingEntryReportsDraftFolderAndFiles() = runTest {
        val path = createDraft("No Entry")
        File(path, "notes.txt").writeText("draft notes")
        val result = execute("miniapp_load", buildJsonObject { put("path", path) })
        assertEquals(true, result.isError)
        val text = resultText(result)
        assertTrue(text.contains("draft entry is missing: index.html"))
        assertTrue(text.contains(File(path).canonicalPath))
        assertTrue(text.contains("manifest.json"))
        assertTrue(text.contains("notes.txt"))
    }

    @Test
    fun deleteRemovesAppDirectoryAndData() = runTest {
        val path = createDraft("Delete Me")
        File(path, "index.html").writeText("<html></html>")
        val loaded = resultJson(execute("miniapp_load", buildJsonObject { put("path", path) }))
        val appId = loaded.getValue("app").jsonObject.getValue("id").jsonPrimitive.content
        val dataFile = store.dataFile(appId)
        dataFile.writeText("{}")
        File(dataFile.path + "-wal").writeText("{}")

        val result = execute("miniapp_delete", buildJsonObject { put("app", appId) })

        assertNull(result.isError)
        assertEquals("{\"ok\":true,\"deleted\":true,\"app\":\"delete-me\"}", resultText(result))
        assertTrue(!File(store.appsDir, appId).exists())
        assertTrue(!dataFile.exists())
        assertTrue(!File(dataFile.path + "-wal").exists())
        assertTrue(store.load(appId) is MiniappEntry.Missing)
    }

    @Test
    fun deleteRejectsMissingApp() = runTest {
        val missingArgument = execute("miniapp_delete", buildJsonObject {})
        assertEquals(true, missingArgument.isError)
        assertTrue(resultText(missingArgument).contains("app"))

        val missing = execute("miniapp_delete", buildJsonObject { put("app", "no-such-app") })
        assertEquals(true, missing.isError)
        assertEquals(
            "miniapp not found: no-such-app (use miniapp_list to see installed apps)",
            resultText(missing),
        )
    }

    @Test
    fun listReturnsAppsAndDrafts() = runTest {
        val empty = resultJson(execute("miniapp_list", buildJsonObject {}))
        assertEquals(0, empty.getValue("apps").jsonArray.size)
        assertEquals(0, empty.getValue("drafts").jsonArray.size)
        assertEquals("{\"apps\":[],\"drafts\":[]}", resultText(execute("miniapp_list", buildJsonObject {})))

        val flowPath = createDraft("Listed App")
        File(flowPath, "index.html").writeText("<html></html>")
        execute("miniapp_load", buildJsonObject { put("path", flowPath) })
        val draft = store.createDraft("Pending Draft", description = "draft description", icon = "🧰")
        val broken = File(store.buildDir, "broken-abcde").also { it.mkdirs() }
        File(broken, "manifest.json").writeText("{oops")

        val json = resultJson(execute("miniapp_list", buildJsonObject {}))
        val apps = json.getValue("apps").jsonArray
        assertEquals(1, apps.size)
        val app = apps[0].jsonObject
        assertEquals(setOf("id", "name", "icon", "version", "path", "description"), app.keys)
        assertEquals("listed-app", app.getValue("id").jsonPrimitive.content)
        assertEquals("Listed App", app.getValue("name").jsonPrimitive.content)
        assertEquals("", app.getValue("icon").jsonPrimitive.content)
        assertEquals("1.0.0", app.getValue("version").jsonPrimitive.content)
        assertEquals(File(store.appsDir, "listed-app").absolutePath, app.getValue("path").jsonPrimitive.content)

        val drafts = json.getValue("drafts").jsonArray
        assertEquals(2, drafts.size)
        val draftJson = drafts.map { it.jsonObject }.first {
            it.getValue("path").jsonPrimitive.content == draft.directory.absolutePath
        }
        assertEquals(setOf("path", "id", "name"), draftJson.keys)
        assertEquals("pending-draft", draftJson.getValue("id").jsonPrimitive.content)
        assertEquals("Pending Draft", draftJson.getValue("name").jsonPrimitive.content)
        val brokenJson = drafts.map { it.jsonObject }.first {
            it.getValue("path").jsonPrimitive.content == broken.absolutePath
        }
        assertEquals(setOf("path"), brokenJson.keys)
    }

    @Test
    fun iconAndDescriptionReachTheList() = runTest {
        val path = createDraft("Styled App")
        val manifest = MiniappManifest.parse(File(path, "manifest.json").readText())
        File(path, "manifest.json").writeText(
            manifest.copy(icon = "🚀", description = "Rocket app").toJson(),
        )
        File(path, "index.html").writeText("<html></html>")
        val loaded = execute("miniapp_load", buildJsonObject { put("path", path) })
        assertNull(loaded.isError)
        assertEquals("🚀", resultJson(loaded).getValue("app").jsonObject.getValue("icon").jsonPrimitive.content)
        val listed = resultJson(execute("miniapp_list", buildJsonObject {}))
        val app = listed.getValue("apps").jsonArray[0].jsonObject
        assertEquals("🚀", app.getValue("icon").jsonPrimitive.content)
        assertEquals("Rocket app", app.getValue("description").jsonPrimitive.content)
    }
}
