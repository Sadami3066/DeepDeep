package io.github.whywood.deepdeep.tool

import dev.pi.agent.AgentToolResult
import dev.pi.ai.TextContent
import dev.pi.coding.tools.CodingTool
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsToolsTest {
    private val errors = SmsToolErrors(
        disabled = "disabled",
        permissionMissing = "no-permission",
        queryFailed = { reason -> "query-failed:$reason" },
    )

    @Test
    fun startsWithDefaultInboxQuery() = runTest {
        val reader = FakeSmsReader(messages(12))
        val result = run(tool(reader), buildJsonObject {})
        assertNull(result.isError)
        assertEquals(SmsBox.INBOX, reader.lastBox)
        assertEquals(SMS_DEFAULT_LIMIT, reader.lastLimit)
        assertNull(reader.lastAddress)
        assertNull(reader.lastSince)
        assertEquals(SMS_DEFAULT_LIMIT, jsonOf(result).size)
    }

    @Test
    fun passesExplicitFilters() = runTest {
        val reader = FakeSmsReader(messages(8))
        run(
            tool(reader),
            buildJsonObject {
                put("limit", 3)
                put("box", "sent")
                put("address", "10086")
                put("since", 1_700_000_000_000L)
            },
        )
        assertEquals(SmsBox.SENT, reader.lastBox)
        assertEquals(3, reader.lastLimit)
        assertEquals("10086", reader.lastAddress)
        assertEquals(1_700_000_000_000L, reader.lastSince)
    }

    @Test
    fun allBoxReadsEveryMessage() = runTest {
        val reader = FakeSmsReader(messages(2))
        val result = run(tool(reader), buildJsonObject { put("box", "all") })
        assertEquals(SmsBox.ALL, reader.lastBox)
        assertEquals(2, jsonOf(result).size)
    }

    @Test
    fun clampsResultsToRequestedLimit() = runTest {
        val reader = FakeSmsReader(messages(8))
        val result = run(tool(reader), buildJsonObject { put("limit", 2) })
        assertEquals(2, jsonOf(result).size)
        assertEquals(2, reader.lastLimit)
    }

    @Test
    fun rejectsLimitOutOfRange() = runTest {
        val reader = FakeSmsReader(messages(3))
        val tool = tool(reader)
        assertTrue(run(tool, buildJsonObject { put("limit", 0) }).isError == true)
        assertTrue(run(tool, buildJsonObject { put("limit", SMS_MAX_LIMIT + 1) }).isError == true)
        assertTrue(run(tool, buildJsonObject { put("limit", "many") }).isError == true)
        assertEquals(0, reader.calls)
    }

    @Test
    fun rejectsUnknownBox() = runTest {
        assertTrue(run(tool(FakeSmsReader(emptyList())), buildJsonObject { put("box", "drafts") }).isError == true)
    }

    @Test
    fun rejectsNegativeSince() = runTest {
        assertTrue(run(tool(FakeSmsReader(emptyList())), buildJsonObject { put("since", -1L) }).isError == true)
    }

    @Test
    fun serializesMessageFields() = runTest {
        val message = SmsMessage(
            address = "+8613800138000",
            date = 1_710_000_000_000L,
            read = false,
            body = "验证码 123456",
        )
        val item = jsonOf(run(tool(FakeSmsReader(listOf(message))), buildJsonObject {}))
            .single()
            .jsonObject
        assertEquals("+8613800138000", item["address"]!!.jsonPrimitive.content)
        assertEquals(1_710_000_000_000L, item["date"]!!.jsonPrimitive.long)
        assertFalse(item["read"]!!.jsonPrimitive.boolean)
        assertEquals("验证码 123456", item["body"]!!.jsonPrimitive.content)
    }

    @Test
    fun truncatesBodyAndNormalizesNewlines() = runTest {
        val body = "\r\n" + "a".repeat(3000) + "\r"
        val item = jsonOf(
            run(
                tool(FakeSmsReader(listOf(SmsMessage("10086", 1L, true, body)))),
                buildJsonObject { put("limit", 1) },
            ),
        ).single().jsonObject
        val normalized = item["body"]!!.jsonPrimitive.content
        assertEquals(SMS_BODY_LIMIT, normalized.length)
        assertFalse(normalized.contains('\r'))
        assertTrue(normalized.startsWith("\n"))
    }

    @Test
    fun emptyInboxReturnsEmptyArray() = runTest {
        val result = run(tool(FakeSmsReader(emptyList())), buildJsonObject {})
        assertNull(result.isError)
        assertEquals("[]", result.text())
    }

    @Test
    fun disabledSwitchReturnsSettingsError() = runTest {
        val reader = FakeSmsReader(messages(2))
        val result = run(tool(reader, enabled = false), buildJsonObject {})
        assertEquals(true, result.isError)
        assertEquals("disabled", result.text())
        assertEquals(0, reader.calls)
    }

    @Test
    fun missingPermissionReturnsSettingsError() = runTest {
        val reader = FakeSmsReader(messages(2))
        val result = run(tool(reader, permission = false), buildJsonObject {})
        assertEquals(true, result.isError)
        assertEquals("no-permission", result.text())
        assertEquals(0, reader.calls)
    }

    @Test
    fun queryFailureIsReported() = runTest {
        val reader = FakeSmsReader(messages(2))
        reader.failWith = IllegalStateException("boom")
        val result = run(tool(reader), buildJsonObject {})
        assertEquals(true, result.isError)
        assertEquals("query-failed:boom", result.text())
    }

    @Test
    fun securityExceptionMapsToPermissionError() = runTest {
        val reader = FakeSmsReader(messages(2))
        reader.failWith = SecurityException("denied")
        val result = run(tool(reader), buildJsonObject {})
        assertEquals(true, result.isError)
        assertEquals("no-permission", result.text())
    }

    private fun tool(
        reader: FakeSmsReader,
        enabled: Boolean = true,
        permission: Boolean = true,
    ): CodingTool = readSmsTool(reader, { enabled }, { permission }, errors)

    private suspend fun run(tool: CodingTool, args: JsonObject): AgentToolResult =
        tool.execute("test-call", args, null, null)

    private fun AgentToolResult.text(): String = (content.single() as TextContent).text

    private fun jsonOf(result: AgentToolResult): JsonArray =
        Json.parseToJsonElement(result.text()).jsonArray

    private fun messages(count: Int): List<SmsMessage> = (0 until count).map { index ->
        SmsMessage(
            address = "+1000000000$index",
            date = 1_700_000_000_000L + index,
            read = index % 2 == 0,
            body = "body-$index",
        )
    }

    private class FakeSmsReader(private val messages: List<SmsMessage>) : SmsReader {
        var calls = 0
        var failWith: Exception? = null
        var lastBox: SmsBox? = null
        var lastLimit: Int? = null
        var lastAddress: String? = null
        var lastSince: Long? = null

        override suspend fun query(
            box: SmsBox,
            limit: Int,
            address: String?,
            since: Long?,
        ): List<SmsMessage> {
            calls++
            failWith?.let { throw it }
            lastBox = box
            lastLimit = limit
            lastAddress = address
            lastSince = since
            return messages
        }
    }
}
