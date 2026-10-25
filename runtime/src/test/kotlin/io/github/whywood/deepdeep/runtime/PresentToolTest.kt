package io.github.whywood.deepdeep.runtime

import dev.pi.agent.AgentToolResult
import dev.pi.ai.TextContent
import dev.pi.coding.tools.CodingTool
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PresentToolTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun resultText(result: AgentToolResult): String =
        result.content.filterIsInstance<TextContent>().joinToString("") { it.text }

    private fun item(builder: JsonObjectBuilder.() -> Unit): JsonObject = buildJsonObject(builder)

    private fun itemsArgs(vararg items: JsonElement): JsonObject = buildJsonObject {
        putJsonArray("items") { items.forEach { add(it) } }
    }

    private fun filesArgs(vararg paths: String): JsonObject = buildJsonObject {
        putJsonArray("files") {
            paths.forEach { path -> add(buildJsonObject { put("path", path) }) }
        }
    }

    private fun tool(
        creativeLookup: (suspend (String) -> PresentedCreative?)? = null,
        cwd: File = folder.root,
    ): CodingTool = presentTool(creativeLookup) { BoundSession("session-1", null, cwd.absolutePath) }

    private suspend fun run(tool: CodingTool, args: JsonObject): Pair<AgentToolResult, String> {
        val result = tool.execute("call-1", args, null, null)
        return result to resultText(result)
    }

    @Test
    fun unboundSessionReturnsErrorText() = runTest {
        val tool = presentTool { null }
        val result = tool.execute(
            "call-unbound",
            itemsArgs(item { put("type", "file"); put("path", "report.md") }),
            null,
            null,
        )
        assertEquals(true, result.isError)
        assertEquals("Session is not bound", resultText(result))
    }

    @Test
    fun schemaDescribesTypedItems() {
        val schema = presentTool { null }.parameters.jsonObject
        val properties = schema.getValue("properties").jsonObject
        assertEquals(setOf("items"), properties.keys)
        assertEquals(
            setOf("items"),
            schema.getValue("required").jsonArray.map { (it as JsonPrimitive).content }.toSet(),
        )
        val items = properties.getValue("items").jsonObject
        assertEquals("array", (items.getValue("type") as JsonPrimitive).content)
        assertEquals("1", (items.getValue("minItems") as JsonPrimitive).content)
        assertEquals("4", (items.getValue("maxItems") as JsonPrimitive).content)
        val entry = items.getValue("items").jsonObject
        assertEquals(
            setOf("type", "path", "id", "title"),
            entry.getValue("properties").jsonObject.keys,
        )
        assertEquals(
            setOf("type"),
            entry.getValue("required").jsonArray.map { (it as JsonPrimitive).content }.toSet(),
        )
        assertEquals(
            setOf("file", "image", "html", "creative"),
            entry.getValue("properties").jsonObject.getValue("type").jsonObject
                .getValue("enum").jsonArray.map { (it as JsonPrimitive).content }.toSet(),
        )
    }

    @Test
    fun validItemsNormalizeToAbsolutePaths() = runTest {
        val report = folder.newFile("report.md")
        val picture = folder.newFile("picture.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val page = folder.newFile("page.html").apply { writeText("<h1>hi</h1>") }
        val (result, text) = run(
            tool(),
            itemsArgs(
                item {
                    put("type", "file")
                    put("path", "report.md")
                    put("title", "  Q3 report  ")
                },
                item {
                    put("type", "image")
                    put("path", picture.absolutePath)
                },
                item {
                    put("type", "html")
                    put("path", "page.html")
                },
            ),
        )
        assertNull(result.isError)
        val items = outputItems(text)
        assertEquals(3, items.size)
        assertEquals(listOf("file", "image", "html"), items.map { it.string("type") })
        assertEquals(report.absolutePath, items[0].string("path"))
        assertEquals("report.md", items[0].string("name"))
        assertEquals("text/markdown", items[0].string("mime"))
        assertEquals("Q3 report", items[0].string("title"))
        assertEquals(picture.absolutePath, items[1].string("path"))
        assertEquals("image/png", items[1].string("mime"))
        assertFalse(items[1].containsKey("title"))
        assertEquals(page.absolutePath, items[2].string("path"))
        assertEquals("text/html", items[2].string("mime"))
    }

    @Test
    fun legacyFilesInferItemTypes() = runTest {
        val (result, text) = run(
            tool(),
            filesArgs("reports/q3.md", "photo.jpeg", "site/index.html", "build/demo/"),
        )
        assertNull(result.isError)
        val root = Json.parseToJsonElement(text).jsonObject
        assertFalse(root.containsKey("files"))
        val items = outputItems(text)
        assertEquals(4, items.size)
        assertEquals(listOf("file", "image", "html", "file"), items.map { it.string("type") })
        assertEquals(listOf("q3.md", "photo.jpeg", "index.html", "demo"), items.map { it.string("name") })
        assertEquals(
            listOf(
                File(folder.root, "reports/q3.md").absolutePath,
                File(folder.root, "photo.jpeg").absolutePath,
                File(folder.root, "site/index.html").absolutePath,
                File(folder.root, "build/demo").absolutePath,
            ),
            items.map { it.string("path") },
        )
    }

    @Test
    fun rejectsUnknownType() = runTest {
        val (result, text) = run(
            tool(),
            itemsArgs(item { put("type", "video"); put("path", "clip.mp4") }),
        )
        assertEquals(true, result.isError)
        assertTrue(text.contains("type must be one of"))
    }

    @Test
    fun rejectsMissingType() = runTest {
        val (result, text) = run(tool(), itemsArgs(item { put("path", "report.md") }))
        assertEquals(true, result.isError)
        assertTrue(text.contains("type is required"))
    }

    @Test
    fun rejectsMissingPath() = runTest {
        val (result, text) = run(tool(), itemsArgs(item { put("type", "file") }))
        assertEquals(true, result.isError)
        assertTrue(text.contains("path is required"))
    }

    @Test
    fun rejectsImageWithWrongExtension() = runTest {
        folder.newFile("notes.md")
        val (result, text) = run(
            tool(),
            itemsArgs(item { put("type", "image"); put("path", "notes.md") }),
        )
        assertEquals(true, result.isError)
        assertTrue(text.contains("not an image"))
    }

    @Test
    fun rejectsMissingImageAndHtml() = runTest {
        val (imageResult, imageText) = run(
            tool(),
            itemsArgs(item { put("type", "image"); put("path", "missing.png") }),
        )
        assertEquals(true, imageResult.isError)
        assertTrue(imageText.contains("does not exist"))
        val (htmlResult, htmlText) = run(
            tool(),
            itemsArgs(item { put("type", "html"); put("path", "missing.html") }),
        )
        assertEquals(true, htmlResult.isError)
        assertTrue(htmlText.contains("does not exist"))
    }

    @Test
    fun rejectsDirectories() = runTest {
        val directory = folder.newFolder("site")
        val (fileResult, fileText) = run(
            tool(),
            itemsArgs(item { put("type", "file"); put("path", directory.absolutePath) }),
        )
        assertEquals(true, fileResult.isError)
        assertTrue(fileText.contains("directory"))
        val (htmlResult, htmlText) = run(
            tool(),
            itemsArgs(item { put("type", "html"); put("path", directory.absolutePath) }),
        )
        assertEquals(true, htmlResult.isError)
        assertTrue(htmlText.contains("not an html page"))
    }

    @Test
    fun rejectsTooManyItems() = runTest {
        val items = (1..5).map { index ->
            item { put("type", "file"); put("path", "report-$index.md") }
        }.toTypedArray()
        val (result, text) = run(tool(), itemsArgs(*items))
        assertEquals(true, result.isError)
        assertTrue(text.contains("1..4"))
    }

    @Test
    fun rejectsEmptyItems() = runTest {
        val (result, text) = run(tool(), itemsArgs())
        assertEquals(true, result.isError)
        assertTrue(text.contains("1..4"))
    }

    @Test
    fun rejectsNonObjectItem() = runTest {
        val (result, text) = run(tool(), itemsArgs(JsonPrimitive("report.md")))
        assertEquals(true, result.isError)
        assertTrue(text.contains("must be an object"))
    }

    @Test
    fun creativeNeedsLookupAndKnownApp() = runTest {
        val (unavailableResult, unavailableText) = run(
            tool(),
            itemsArgs(item { put("type", "creative"); put("id", "weekly-board") }),
        )
        assertEquals(true, unavailableResult.isError)
        assertTrue(unavailableText.contains("not available"))
        val missingLookup: suspend (String) -> PresentedCreative? = { null }
        val (missingResult, missingText) = run(
            tool(creativeLookup = missingLookup),
            itemsArgs(item { put("type", "creative"); put("id", "weekly-board") }),
        )
        assertEquals(true, missingResult.isError)
        assertTrue(missingText.contains("workshop app not found"))
    }

    @Test
    fun creativeIsNormalizedFromLookup() = runTest {
        val lookup: suspend (String) -> PresentedCreative? = { id ->
            if (id == "weekly-board") {
                PresentedCreative(id = "weekly-board", name = "周报看板", icon = "📊", version = "1.2.0")
            } else {
                null
            }
        }
        val (result, text) = run(
            tool(creativeLookup = lookup),
            itemsArgs(item { put("type", "creative"); put("id", "weekly-board"); put("title", "本周") }),
        )
        assertNull(result.isError)
        val creative = outputItems(text).single()
        assertEquals("creative", creative.string("type"))
        assertEquals("weekly-board", creative.string("id"))
        assertEquals("周报看板", creative.string("name"))
        assertEquals("📊", creative.string("icon"))
        assertEquals("1.2.0", creative.string("version"))
        assertEquals("本周", creative.string("title"))
        assertFalse(creative.containsKey("path"))
    }

    @Test
    fun creativeRequiresId() = runTest {
        val lookup: suspend (String) -> PresentedCreative? = { PresentedCreative(it, "A") }
        val (result, text) = run(tool(creativeLookup = lookup), itemsArgs(item { put("type", "creative") }))
        assertEquals(true, result.isError)
        assertTrue(text.contains("id is required"))
    }

    @Test
    fun fileTitleIsOptional() = runTest {
        folder.newFile("data.json")
        val (result, text) = run(
            tool(),
            itemsArgs(item { put("type", "file"); put("path", "data.json") }),
        )
        assertNull(result.isError)
        val entry = outputItems(text).single()
        assertEquals("application/json", entry.string("mime"))
        assertFalse(entry.containsKey("title"))
    }

    private fun outputItems(text: String): List<JsonObject> =
        Json.parseToJsonElement(text).jsonObject.getValue("items").jsonArray.map { it.jsonObject }

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content
}
