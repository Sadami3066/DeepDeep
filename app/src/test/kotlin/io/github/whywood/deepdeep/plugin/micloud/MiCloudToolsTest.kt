package io.github.whywood.deepdeep.plugin.micloud

import dev.pi.agent.AgentToolResult
import dev.pi.ai.TextContent
import dev.pi.coding.tools.CodingTool
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MiCloudToolsTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val settings = FakeSettings(MiCloudCapabilities())
    private val readApi = FakeReadApi()
    private val writeApi = FakeWriteApi()
    private lateinit var downloadDir: File

    private fun ensureDownloadDir(): File {
        if (!this::downloadDir.isInitialized) downloadDir = temporaryFolder.newFolder("recordings")
        return downloadDir
    }

    private fun tools(): List<CodingTool> = buildMiCloudTools(
        client = readApi,
        settings = settings,
        writer = writeApi,
        downloadDir = ensureDownloadDir(),
    )

    private fun tool(name: String): CodingTool = tools().first { it.name == name }

    private suspend fun run(tool: CodingTool, args: JsonObject = buildJsonObject { }): AgentToolResult =
        tool.execute("test-call", args, null, null)

    private fun AgentToolResult.text(): String = (content.single() as TextContent).text

    private fun AgentToolResult.json(): JsonObject = Json.parseToJsonElement(text()).jsonObject

    @Test
    fun injectsOnlyFeaturesWithTheirReadSwitchOn() {
        assertEquals(emptyList<String>(), tools().map { it.name })

        settings.update(MiCloudCapabilities(notesRead = true, todosWrite = true))
        assertEquals(listOf("mi_cloud_notes"), tools().map { it.name })

        settings.update(
            MiCloudCapabilities(
                notesRead = true,
                todosRead = true,
                smsRead = true,
                contactsRead = true,
                galleryRead = true,
                recordingsRead = true,
                accountRead = true,
            ),
        )
        assertEquals(
            listOf(
                "mi_cloud_account",
                "mi_cloud_notes",
                "mi_cloud_todo",
                "mi_cloud_sms",
                "mi_cloud_contacts",
                "mi_cloud_gallery",
                "mi_cloud_recordings",
            ),
            tools().map { it.name },
        )
    }

    @Test
    fun notesWriteRequiresWriteSwitch() = runTest {
        settings.update(MiCloudCapabilities(notesRead = true))
        val denied = run(tool("mi_cloud_notes"), buildJsonObject {
            put("action", "create")
            put("subject", "标题")
        })
        assertEquals(true, denied.isError)
        assertTrue(denied.text().contains("笔记写入未开启"))
        assertTrue(writeApi.calls.isEmpty())

        settings.update(MiCloudCapabilities(notesRead = true, notesWrite = true))
        val allowed = run(tool("mi_cloud_notes"), buildJsonObject {
            put("action", "create")
            put("subject", "标题")
            put("content", "正文")
        })
        assertFalse(allowed.isError == true)
        assertEquals("/note/note", writeApi.calls.single().first)
        val entry = Json.parseToJsonElement(writeApi.calls.single().second.getValue("entry")).jsonObject
        assertEquals("标题", entry["subject"]!!.jsonPrimitive.content)
        assertEquals("正文", entry["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun todoWriteRequiresWriteSwitch() = runTest {
        settings.update(MiCloudCapabilities(todosRead = true))
        val denied = run(tool("mi_cloud_todo"), buildJsonObject {
            put("action", "create")
            put("content", "买牛奶")
        })
        assertEquals(true, denied.isError)
        assertTrue(denied.text().contains("待办写入未开启"))
        assertTrue(writeApi.calls.isEmpty())

        settings.update(MiCloudCapabilities(todosRead = true, todosWrite = true))
        val allowed = run(tool("mi_cloud_todo"), buildJsonObject {
            put("action", "create")
            put("content", "买牛奶")
            put("remindAt", 1_700_000_000_000L)
        })
        assertFalse(allowed.isError == true)
        assertEquals("/todo/v1/user/records", writeApi.calls.single().first)
        val record = Json.parseToJsonElement(writeApi.calls.single().second.getValue("record")).jsonObject
        val contentJson = record["contentJson"]!!.jsonObject
        assertEquals("entity", record["type"]!!.jsonPrimitive.content)
        assertEquals("买牛奶", contentJson["content"]!!.jsonPrimitive.content)
        assertEquals(2, contentJson["remindType"]!!.jsonPrimitive.int)
        assertEquals(1_700_000_000_000L, contentJson["remindTime"]!!.jsonPrimitive.long)
    }

    @Test
    fun listsNotesFromResponseFixture() = runTest {
        readApi.respond(
            "/note/full/page/",
            """{"data":{"entries":[{"id":"n1","subject":"A","snippet":"s","modifyDate":100,"tag":"t1","folderId":"0"}],"lastPage":false,"syncTag":"42"}}""",
        )
        settings.update(MiCloudCapabilities(notesRead = true))
        val result = run(tool("mi_cloud_notes"), buildJsonObject {
            put("action", "list")
            put("limit", 2)
        })
        assertFalse(result.isError == true)
        val path = readApi.requestedPaths.single()
        assertTrue(path.startsWith("/note/full/page/?ts="))
        assertTrue(path.contains("&limit=2"))
        val payload = result.json()
        assertEquals(1, payload["count"]!!.jsonPrimitive.int)
        assertEquals("n1", payload["items"]!!.jsonArray[0].jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals("42", payload["nextSyncTag"]!!.jsonPrimitive.content)
        assertFalse(payload["lastPage"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun readsNoteDetail() = runTest {
        readApi.respond(
            "/note/note/n1",
            """{"D":{"entry":{"id":"n1","subject":"S","content":"正文","tag":"t","modifyDate":1}}}""",
        )
        settings.update(MiCloudCapabilities(notesRead = true))
        val result = run(tool("mi_cloud_notes"), buildJsonObject {
            put("action", "read")
            put("id", "n1")
        })
        assertFalse(result.isError == true)
        assertEquals("正文", result.json()["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun listsTodosFromResponseFixture() = runTest {
        readApi.respond(
            "/todo/v1/user/records",
            """{"data":{"records":[{"id":11,"eTag":5,"contentJson":{"content":"买牛奶","isFinish":0,"is_finish":false,"remindTime":0,"lastModifiedTime":100}}],"hasMore":true,"syncToken":{"watermark":9}}}""",
        )
        settings.update(MiCloudCapabilities(todosRead = true))
        val result = run(tool("mi_cloud_todo"), buildJsonObject { put("action", "list") })
        assertFalse(result.isError == true)
        val payload = result.json()
        assertEquals(1, payload["count"]!!.jsonPrimitive.int)
        val item = payload["items"]!!.jsonArray[0].jsonObject
        assertEquals("11", item["id"]!!.jsonPrimitive.content)
        assertEquals("买牛奶", item["content"]!!.jsonPrimitive.content)
        assertFalse(item["done"]!!.jsonPrimitive.boolean)
        assertTrue(payload["hasMore"]!!.jsonPrimitive.boolean)
        assertNotNull(payload["next"])
    }

    @Test
    fun updatesTodoWithPreviousEtag() = runTest {
        readApi.respond(
            "/todo/v1/user/records/77",
            """{"data":{"record":{"id":77,"eTag":12,"contentJson":{"content":"old","isFinish":0,"is_finish":false,"lastModifiedTime":1}}}}""",
        )
        settings.update(MiCloudCapabilities(todosRead = true, todosWrite = true))
        val result = run(tool("mi_cloud_todo"), buildJsonObject {
            put("action", "update")
            put("id", "77")
            put("done", true)
        })
        assertFalse(result.isError == true)
        val call = writeApi.calls.single()
        assertEquals("/todo/v1/user/records/77/update", call.first)
        assertEquals("12", call.second["previousETag"])
        val record = Json.parseToJsonElement(call.second.getValue("record")).jsonObject
        val contentJson = record["contentJson"]!!.jsonObject
        assertEquals(1, contentJson["isFinish"]!!.jsonPrimitive.int)
        assertTrue(contentJson["is_finish"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun deletesTodoWithPrevEtag() = runTest {
        readApi.respond(
            "/todo/v1/user/records/77",
            """{"data":{"record":{"id":77,"eTag":12,"contentJson":{"content":"old"}}}}""",
        )
        settings.update(MiCloudCapabilities(todosRead = true, todosWrite = true))
        val result = run(tool("mi_cloud_todo"), buildJsonObject {
            put("action", "delete")
            put("id", "77")
        })
        assertFalse(result.isError == true)
        val call = writeApi.calls.single()
        assertEquals("/todo/v1/user/records/77/delete", call.first)
        assertEquals("12", call.second["prevETag"])
    }

    @Test
    fun deletesNoteWithTag() = runTest {
        readApi.respond(
            "/note/note/n1",
            """{"data":{"entry":{"id":"n1","subject":"S","content":"正文","tag":"tag1"}}}""",
        )
        settings.update(MiCloudCapabilities(notesRead = true, notesWrite = true))
        val result = run(tool("mi_cloud_notes"), buildJsonObject {
            put("action", "delete")
            put("id", "n1")
        })
        assertFalse(result.isError == true)
        val call = writeApi.calls.single()
        assertEquals("/note/full/n1/delete", call.first)
        assertEquals("tag1", call.second["tag"])
        assertEquals("1", call.second["purge"])
    }

    @Test
    fun smsListUsesUserIdAndPagingParams() = runTest {
        settings.update(MiCloudCapabilities(smsRead = true))
        run(tool("mi_cloud_sms"), buildJsonObject {
            put("limit", 5)
            put("withPhoneCall", false)
        })
        val path = readApi.requestedPaths.single()
        assertTrue(path.startsWith("/sms/u1/full/thread?limit=5&readMode=older&withPhoneCall=false&_dc="))
        assertTrue(path.endsWith("&syncTag=0&syncThreadTag=0"))
    }

    @Test
    fun missingUserIdReportsLoginError() = runTest {
        readApi.userId = null
        settings.update(MiCloudCapabilities(smsRead = true))
        val result = run(tool("mi_cloud_sms"))
        assertEquals(true, result.isError)
        assertTrue(result.text().contains("登录已失效"))
    }

    @Test
    fun authFailureIsReportedAsLoginError() = runTest {
        readApi.failure = MiCloudAuthException("expired")
        settings.update(MiCloudCapabilities(notesRead = true))
        val result = run(tool("mi_cloud_notes"), buildJsonObject { put("action", "list") })
        assertEquals(true, result.isError)
        assertTrue(result.text().contains("登录已失效"))
    }

    @Test
    fun contactsSearchFiltersLocally() = runTest {
        readApi.respond(
            "/contacts/u1/initdata",
            """{"data":{"content":[{"id":"c1","name":"张三","phone":"138"},{"id":"c2","name":"李四","phone":"139"}]}}""",
        )
        settings.update(MiCloudCapabilities(contactsRead = true))
        val result = run(tool("mi_cloud_contacts"), buildJsonObject {
            put("action", "search")
            put("query", "张")
        })
        assertFalse(result.isError == true)
        val payload = result.json()
        assertEquals(1, payload["count"]!!.jsonPrimitive.int)
        assertEquals("张三", payload["items"]!!.jsonArray[0].jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun galleryListTrimsToLimitAndReportsLastPage() = runTest {
        readApi.respond(
            "/gallery/user/galleries",
            """{"data":{"galleries":[{"id":1,"fileName":"a.jpg"},{"id":2,"fileName":"b.jpg"},{"id":3,"fileName":"c.jpg"}],"isLastPage":false,"indexHash":"h1"}}""",
        )
        settings.update(MiCloudCapabilities(galleryRead = true))
        val result = run(tool("mi_cloud_gallery"), buildJsonObject {
            put("action", "list")
            put("limit", 2)
        })
        assertFalse(result.isError == true)
        val payload = result.json()
        assertEquals(2, payload["count"]!!.jsonPrimitive.int)
        assertFalse(payload.containsKey("nextIndexHash"))
        assertFalse(payload["isLastPage"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun galleryAlbumsAreListed() = runTest {
        readApi.respond(
            "/gallery/user/album/list",
            """{"data":{"albums":[{"id":1,"name":"相机"}]}}""",
        )
        settings.update(MiCloudCapabilities(galleryRead = true))
        val result = run(tool("mi_cloud_gallery"), buildJsonObject { put("action", "albums") })
        assertFalse(result.isError == true)
        assertEquals(1, result.json()["count"]!!.jsonPrimitive.int)
    }

    @Test
    fun downloadsRecordingIntoWorkspace() = runTest {
        readApi.respond(
            "/sfs/u1/ns/recorder/dir/0/list",
            """{"data":{"list":[{"id":"f1","fileName":"call 01.amr","size":9}]}}""",
        )
        settings.update(MiCloudCapabilities(recordingsRead = true))
        val result = run(tool("mi_cloud_recordings"), buildJsonObject {
            put("action", "download")
            put("fileId", "f1")
        })
        assertFalse(result.isError == true)
        val payload = result.json()
        assertEquals("call_01.amr", payload["name"]!!.jsonPrimitive.content)
        assertTrue(payload["path"]!!.jsonPrimitive.content.startsWith("/"))
        val hostPath = payload["path"]!!.jsonPrimitive.content
        assertFalse(payload.containsKey("hostPath"))
        assertTrue(File(hostPath).isFile)
        assertTrue(hostPath.endsWith("call_01.amr"))
        assertTrue(readApi.requestedPaths.any { it.startsWith("/sfs/u1/ns/recorder/file/f1/storage") })
    }

    @Test
    fun recordingsListReturnsNextOffset() = runTest {
        readApi.respond(
            "/sfs/u1/ns/recorder/dir/0/list",
            """{"data":{"list":[{"id":"f1","fileName":"a.amr"},{"id":"f2","fileName":"b.amr"}]}}""",
        )
        settings.update(MiCloudCapabilities(recordingsRead = true))
        val result = run(tool("mi_cloud_recordings"), buildJsonObject {
            put("action", "list")
            put("offset", 0)
            put("limit", 2)
        })
        assertFalse(result.isError == true)
        assertEquals(2, result.json()["nextOffset"]!!.jsonPrimitive.int)
    }

    @Test
    fun invalidLimitIsRejectedBeforeRequesting() = runTest {
        settings.update(MiCloudCapabilities(notesRead = true))
        val zero = run(tool("mi_cloud_notes"), buildJsonObject {
            put("action", "list")
            put("limit", 0)
        })
        val tooBig = run(tool("mi_cloud_notes"), buildJsonObject {
            put("action", "list")
            put("limit", 10_000)
        })
        assertEquals(true, zero.isError)
        assertEquals(true, tooBig.isError)
        assertTrue(readApi.requestedPaths.isEmpty())
    }

    @Test
    fun toolTextFollowsTaskLanguage() {
        settings.update(
            MiCloudCapabilities(
                notesRead = true,
                todosRead = true,
                smsRead = true,
                contactsRead = true,
                galleryRead = true,
                recordingsRead = true,
                accountRead = true,
            ),
        )
        val all = tools()
        assertEquals(7, all.size)
        all.forEach { tool ->
            assertNull(tool.promptSnippet)
            assertNull(tool.promptGuidelines)
            val texts = buildList {
                add(tool.description)
                val properties = tool.parameters["properties"] as JsonObject
                properties.values.forEach { property ->
                    val description = ((property as? JsonObject)?.get("description") as? JsonPrimitive)?.contentOrNull
                    description?.let(::add)
                }
            }
            texts.forEach { text ->
                bannedWords.forEach { word ->
                    assertFalse("$word in ${tool.name}: $text", text.contains(word, ignoreCase = true))
                }
            }
        }
    }

    @Test
    fun toolErrorsAreChineseOnly() = runTest {
        assertEquals("小米云登录已失效：请打开 DeepDeep → 插件 → 小米云 → 重新登录", TOOL_AUTH_ERROR)
        assertEquals("请求小米云失败：", TOOL_REQUEST_FAILED)
        settings.update(MiCloudCapabilities(notesRead = true, accountRead = true))
        val invalidAction = run(tool("mi_cloud_account"), buildJsonObject { put("action", "other") })
        assertEquals(true, invalidAction.isError)
        assertTrue(invalidAction.text().contains("action 无效"))
        assertFalse(invalidAction.text().contains(" / "))
        val invalidLimit = run(tool("mi_cloud_notes"), buildJsonObject {
            put("action", "list")
            put("limit", 0)
        })
        assertEquals(true, invalidLimit.isError)
        assertTrue(invalidLimit.text().contains("limit 必须在"))
        assertFalse(invalidLimit.text().contains(" / "))
    }

    @Test
    fun buildsEndpointPaths() {
        assertEquals("/note/full/page/?ts=1000&limit=20", notesPagePath(1000, 20, null))
        assertEquals("/note/full/page/?ts=1000&limit=20&syncTag=42", notesPagePath(1000, 20, "42"))
        assertEquals("/note/note/n1/?ts=1000", noteDetailPath("n1", 1000))
        assertEquals("/note/note/n1", noteUpdatePath("n1"))
        assertEquals("/note/full/n1/delete", noteDeletePath("n1"))
        assertEquals("/todo/v1/user/records?ts=1000&limit=20", todoRecordsPath(1000, 20, null))
        assertEquals("/todo/v1/user/records/77", todoRecordPath("77"))
        assertEquals("/todo/v1/user/records/77/update", todoUpdatePath("77"))
        assertEquals("/todo/v1/user/records/77/delete", todoDeletePath("77"))
        assertEquals(
            "/sms/u1/full/thread?limit=5&readMode=older&withPhoneCall=true&_dc=1000&syncTag=0&syncThreadTag=0",
            smsThreadPath("u1", 1000, 5, true, "0", "0"),
        )
        assertEquals(
            "/contacts/u1/initdata?limit=100&syncTag=0&syncIgnoreTag=0&ts=1000",
            contactsPath("u1", 1000, 100),
        )
        assertEquals("/gallery/user/galleries?ts=1000", galleriesPath(1000))
        assertEquals("/gallery/user/album/list?isShared=false&ts=1000", albumsPath(1000))
        assertEquals(
            "/sfs/u1/ns/recorder/dir/0/list?offset=20&limit=10&ts=1000",
            recordingsPath("u1", 1000, 20, 10),
        )
        assertEquals("/sfs/u1/ns/recorder/file/f1/storage", recordingDownloadPath("u1", "f1"))
    }

    @Test
    fun buildsWriteForms() {
        val note = buildNoteEntry("标题", "正文", "0", 3, 1000)
        assertEquals("标题", note["subject"]!!.jsonPrimitive.content)
        assertEquals("正文", note["content"]!!.jsonPrimitive.content)
        assertEquals(1000L, note["createDate"]!!.jsonPrimitive.long)
        assertEquals(3, note["colorId"]!!.jsonPrimitive.int)
        assertEquals(setOf("entry"), noteCreateForm(note).keys)
        assertEquals(setOf("entry"), noteUpdateForm(note).keys)
        assertEquals("t", noteDeleteForm("t", true)["tag"])
        assertEquals("1", noteDeleteForm("t", true)["purge"])
        assertEquals("0", noteDeleteForm("t", false)["purge"])

        val todo = buildTodoRecord("买牛奶", 1000, 1_700_000_000_000L)
        val contentJson = todo["contentJson"]!!.jsonObject
        assertEquals("entity", todo["type"]!!.jsonPrimitive.content)
        assertEquals("买牛奶", contentJson["content"]!!.jsonPrimitive.content)
        assertEquals(2, contentJson["remindType"]!!.jsonPrimitive.int)
        assertEquals(1_700_000_000_000L, contentJson["firstRemindTime"]!!.jsonPrimitive.long)
        val updateForm = todoUpdateForm("12", todo)
        assertEquals("12", updateForm["previousETag"])
        assertEquals(setOf("previousETag", "record"), updateForm.keys)
        assertEquals("12", todoDeleteForm("12")["prevETag"])
    }

    @Test
    fun updatedNoteEntryKeepsOtherFields() {
        val existing = Json.parseToJsonElement(
            """{"id":"n1","subject":"旧","snippet":"旧","content":"旧正文","tag":"t","folderId":"0"}""",
        ).jsonObject
        val updated = updatedNoteEntry(existing, "新", "新正文", 2000)
        assertEquals("新", updated["subject"]!!.jsonPrimitive.content)
        assertEquals("新正文", updated["content"]!!.jsonPrimitive.content)
        assertEquals("t", updated["tag"]!!.jsonPrimitive.content)
        assertEquals("0", updated["folderId"]!!.jsonPrimitive.content)
        assertEquals(2000L, updated["modifyDate"]!!.jsonPrimitive.long)
    }

    @Test
    fun updatedTodoRecordMergesChanges() {
        val existing = Json.parseToJsonElement(
            """{"id":7,"eTag":3,"contentJson":{"content":"旧","isFinish":0,"is_finish":false,"remindTime":0,"lastModifiedTime":1}}""",
        ).jsonObject
        val updated = updatedTodoRecord(existing, "新", true, 1_700_000_000_000L, 2000)
        val contentJson = updated["contentJson"]!!.jsonObject
        assertEquals("新", contentJson["content"]!!.jsonPrimitive.content)
        assertEquals(1, contentJson["isFinish"]!!.jsonPrimitive.int)
        assertTrue(contentJson["is_finish"]!!.jsonPrimitive.boolean)
        assertEquals(2, contentJson["remindType"]!!.jsonPrimitive.int)
        assertEquals(1_700_000_000_000L, contentJson["remindTime"]!!.jsonPrimitive.long)
        assertEquals(2000L, contentJson["markFinishTime"]!!.jsonPrimitive.long)
        assertEquals(3, updated["eTag"]!!.jsonPrimitive.int)
    }

    @Test
    fun sanitizesDownloadFileNames() {
        assertEquals("call_01.amr", safeFileName("call 01.amr"))
        assertEquals("recording", safeFileName("   "))
    }

    private val bannedWords = listOf(
        "helper",
        "daemon",
        "uid",
        "uiautomator",
        "displayId",
        "taskDisplayId",
        "nodeBackend",
        "inputBackend",
        "CalendarContract",
        "backend",
        "engine",
    )

    private class FakeSettings(initial: MiCloudCapabilities) : MiCloudSettings {
        private val mutable = MutableStateFlow(initial)
        override val capabilities: StateFlow<MiCloudCapabilities> = mutable

        fun update(capabilities: MiCloudCapabilities) {
            mutable.value = capabilities
        }

        override suspend fun setRead(feature: MiCloudFeature, enabled: Boolean) {
            mutable.value = mutable.value.withRead(feature, enabled)
        }

        override suspend fun setWrite(feature: MiCloudFeature, enabled: Boolean) {
            mutable.value = mutable.value.withWrite(feature, enabled)
        }

        override suspend fun clear() {
            mutable.value = MiCloudCapabilities()
        }
    }

    private class FakeReadApi : MiCloudReadApi {
        var userId: String? = "u1"
        var failure: Exception? = null
        val responses = linkedMapOf<String, JsonElement>()
        val requestedPaths = mutableListOf<String>()

        fun respond(prefix: String, json: String) {
            responses[prefix] = Json.parseToJsonElement(json)
        }

        override suspend fun userId(): String? = userId

        override suspend fun getJson(pathWithQuery: String): JsonElement {
            requestedPaths += pathWithQuery
            failure?.let { throw it }
            return responses.entries
                .firstOrNull { (prefix, _) -> pathWithQuery.startsWith(prefix) }
                ?.value
                ?: JsonObject(emptyMap())
        }

        override suspend fun download(pathWithQuery: String, dest: File): File {
            requestedPaths += pathWithQuery
            failure?.let { throw it }
            dest.parentFile?.mkdirs()
            dest.writeText("audio-bytes")
            return dest
        }
    }

    private class FakeWriteApi : MiCloudWriteApi {
        val calls = mutableListOf<Pair<String, Map<String, String>>>()
        var response = "{\"code\":0}"

        override suspend fun post(path: String, form: Map<String, String>): String {
            calls += path to form
            return response
        }
    }
}
