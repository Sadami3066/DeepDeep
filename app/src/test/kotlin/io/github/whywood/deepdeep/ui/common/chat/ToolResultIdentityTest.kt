package io.github.whywood.deepdeep.ui.common.chat

import io.github.whywood.deepdeep.plugin.api.ToolResultSource
import io.github.whywood.deepdeep.runtime.ToolTrace
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ToolResultIdentityTest {
    @Test
    fun parsesMcpToolName() {
        val identity = toolResultIdentity("mcp__amap__search_places") { false }

        assertEquals(ToolResultSource.MCP, identity.source)
        assertEquals("amap", identity.server)
        assertEquals("search_places", identity.tool)
    }

    @Test
    fun keepsHyphenatedMcpToolNames() {
        val identity = toolResultIdentity("mcp__travel__search-hotels") { false }

        assertEquals("travel", identity.server)
        assertEquals("search-hotels", identity.tool)
    }

    @Test
    fun classifiesPluginAndBuiltinTools() {
        assertEquals(
            ToolResultSource.PLUGIN,
            toolResultIdentity("mail_search") { it == "mail_search" }.source,
        )
        assertEquals(
            ToolResultSource.BUILTIN,
            toolResultIdentity("bash") { false }.source,
        )
    }

    @Test
    fun resolvesInnerMcpToolBehindToolExecute() {
        val trace = ToolTrace(
            id = 3,
            name = "tool_execute",
            arguments = emptyMap(),
            rawArguments = buildJsonObject {
                put("name", "mcp__travel__hotel_search")
                put("arguments", buildJsonObject { put("city", "杭州") })
            },
        )

        val identity = trace.toolResultIdentity { false }

        assertEquals(ToolResultSource.MCP, identity.source)
        assertEquals("travel", identity.server)
        assertEquals("hotel_search", identity.tool)
    }

    @Test
    fun prefersMcpDetailsForToolExecuteResult() {
        val trace = ToolTrace(
            id = 4,
            name = "tool_execute",
            arguments = emptyMap(),
            resultDetails = buildJsonObject {
                put("server", "amap")
                put("tool", "route")
            },
        )

        val identity = trace.toolResultIdentity { false }

        assertEquals("amap", identity.server)
        assertEquals("route", identity.tool)
    }

    @Test
    fun detailsServerAndToolWinOverNameParts() {
        val trace = ToolTrace(
            id = 1,
            name = "mcp__display__get",
            arguments = emptyMap(),
            resultDetails = buildJsonObject {
                put("server", "amap")
                put("tool", "route")
            },
        )

        val identity = trace.toolResultIdentity { false }

        assertEquals("amap", identity.server)
        assertEquals("route", identity.tool)
    }

    @Test
    fun readsStructuredContentFromDetails() {
        val trace = ToolTrace(
            id = 1,
            name = "mcp__amap__route",
            arguments = emptyMap(),
            resultDetails = buildJsonObject {
                put(
                    "deepdeep",
                    buildJsonObject {
                        put("structuredContent", buildJsonObject { put("distance", 1200) })
                    },
                )
            },
        )

        assertEquals(1200, (trace.structuredContent() as kotlinx.serialization.json.JsonObject)["distance"]!!.toString().toInt())
        assertNull(ToolTrace(id = 2, name = "mcp__amap__route", arguments = emptyMap()).structuredContent())
    }
}
