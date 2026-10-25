package io.github.whywood.deepdeep.plugin.micloud

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiCloudParsersTest {
    @Test
    fun parsesCookieHeaderKeepingValuesWithEquals() {
        val cookies = parseCookie("serviceToken=abc=def; userId=1001; uLocale=zh_CN")
        assertEquals("abc=def", cookies["serviceToken"])
        assertEquals("1001", cookies["userId"])
        assertEquals("zh_CN", cookies["uLocale"])
    }

    @Test
    fun skipsMalformedCookieParts() {
        assertEquals(mapOf("ok" to "1"), parseCookie(" ; broken; =empty; ok=1;"))
    }

    @Test
    fun detectsAuthFailureInJsonEnvelope() {
        assertTrue(
            isMiCloudAuthFailure(
                Json.parseToJsonElement("""{"R":401,"S":"Err","D":"https://account.xiaomi.com/pass"}"""),
            ),
        )
        assertTrue(isMiCloudAuthFailure(Json.parseToJsonElement("""{"code":403}""")))
        assertFalse(isMiCloudAuthFailure(Json.parseToJsonElement("""{"data":{}}""")))
        assertFalse(isMiCloudAuthFailure(Json.parseToJsonElement("""[]""")))
    }

    @Test
    fun parsesProfileFromDataEnvelope() {
        val profile = parseProfile(
            Json.parseToJsonElement(
                """{"data":{"nickname":"小明","userId":1001,"icon":"https://i.mi.com/avatar.png"}}""",
            ),
        )
        assertEquals("小明", profile?.nickname)
        assertEquals("1001", profile?.userId)
        assertEquals("https://i.mi.com/avatar.png", profile?.icon)
    }

    @Test
    fun parsesProfileFromUpperEnvelope() {
        val profile = parseProfile(Json.parseToJsonElement("""{"R":200,"S":"OK","D":{"nickname":"阿云"}}"""))
        assertEquals("阿云", profile?.nickname)
        assertNull(profile?.userId)
    }

    @Test
    fun rejectsProfileWithoutIdentity() {
        assertNull(parseProfile(Json.parseToJsonElement("""{"data":{}}""")))
        assertNull(parseProfile(Json.parseToJsonElement("""[]""")))
    }

    @Test
    fun parsesNotesPage() {
        val page = parseNotesPage(Json.parseToJsonElement(NOTES_PAGE))!!
        assertEquals(2, page.notes.size)
        assertEquals("note-1", page.notes[0].id)
        assertEquals("购物清单", page.notes[0].subject)
        assertEquals("牛奶、鸡蛋", page.notes[0].snippet)
        assertEquals("0", page.notes[0].folderId)
        assertEquals(1_792_890_000_000L, page.notes[0].modifyDate)
        assertEquals("note-2", page.notes[1].id)
        assertTrue(page.lastPage)
        assertEquals("sync-tag-9", page.syncTag)
    }

    @Test
    fun returnsNullWhenNotesPageHasNoEntries() {
        assertNull(parseNotesPage(Json.parseToJsonElement("""{"data":{"lastPage":true}}""")))
    }

    @Test
    fun parsesNoteDetail() {
        val note = parseNote(
            Json.parseToJsonElement(
                """{"data":{"entry":{"id":"note-1","subject":"购物清单","content":"牛奶\n鸡蛋"}}}""",
            ),
        )
        assertEquals("note-1", note?.id)
        assertEquals("购物清单", note?.subject)
        assertEquals("牛奶\n鸡蛋", note?.content)
    }

    @Test
    fun parsesTodosWithBothFinishFlags() {
        val todos = parseTodos(Json.parseToJsonElement(TODO_PAGE))
        assertEquals(2, todos.size)
        assertEquals("14420342151254080", todos[0].id)
        assertEquals("买牛奶", todos[0].content)
        assertFalse(todos[0].finished)
        assertEquals(1_792_890_000_000L, todos[0].createTime)
        assertEquals(1_792_890_600_000L, todos[0].modifyTime)
        assertEquals("12", todos[0].eTag)
        assertEquals("99", todos[1].id)
        assertTrue(todos[1].finished)
        assertNull(todos[1].eTag)
    }

    @Test
    fun returnsEmptyTodosWithoutRecords() {
        assertEquals(emptyList<MiCloudTodo>(), parseTodos(Json.parseToJsonElement("""{"data":{}}""")))
        assertEquals(emptyList<MiCloudTodo>(), parseTodos(Json.parseToJsonElement("""{"R":401}""")))
    }

    private companion object {
        const val NOTES_PAGE = """
            {
              "data": {
                "entries": [
                  {
                    "id": "note-1",
                    "tag": "tag-1",
                    "type": "note",
                    "subject": "购物清单",
                    "snippet": "牛奶、鸡蛋",
                    "createDate": 1792890000000,
                    "modifyDate": 1792890000000,
                    "deleteTime": 0,
                    "colorId": 0,
                    "folderId": "0",
                    "status": "normal"
                  },
                  {
                    "id": "note-2",
                    "type": "note",
                    "subject": "周报",
                    "snippet": "本周完成",
                    "modifyDate": 1792890100000,
                    "folderId": "3"
                  }
                ],
                "folders": [],
                "lastPage": true,
                "syncTag": "sync-tag-9"
              }
            }
        """

        const val TODO_PAGE = """
            {
              "data": {
                "records": [
                  {
                    "id": 14420342151254080,
                    "eTag": 12,
                    "type": "entity",
                    "contentJson": {
                      "content": "买牛奶",
                      "plainText": "买牛奶",
                      "isFinish": 0,
                      "markFinishTime": 0,
                      "createTime": 1792890000000,
                      "lastModifiedTime": 1792890600000,
                      "is_finish": false
                    }
                  },
                  {
                    "id": "99",
                    "type": "entity",
                    "contentJson": {
                      "content": "交房租",
                      "is_finish": true,
                      "createTime": 1792890000001,
                      "lastModifiedTime": 1792890600001
                    }
                  }
                ],
                "hasMore": false,
                "syncToken": {"watermark": "w-1"}
              }
            }
        """
    }
}
