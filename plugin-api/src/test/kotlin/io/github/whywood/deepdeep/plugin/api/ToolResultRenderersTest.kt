package io.github.whywood.deepdeep.plugin.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolResultRenderersTest {
    @Test
    fun emptyMatchTargetsEveryTool() {
        val match = toolResultMatch()

        assertTrue(match.matches(ToolResultSource.MCP, "amap", "search_places"))
        assertTrue(match.matches(ToolResultSource.BUILTIN, null, "bash"))
    }

    @Test
    fun sourceServerAndToolAreConjunctive() {
        val match = toolResultMatch(source = ToolResultSource.MCP, server = "amap", tool = "search_places")

        assertTrue(match.matches(ToolResultSource.MCP, "amap", "search_places"))
        assertFalse(match.matches(ToolResultSource.PLUGIN, "amap", "search_places"))
        assertFalse(match.matches(ToolResultSource.MCP, "weather", "search_places"))
        assertFalse(match.matches(ToolResultSource.MCP, "amap", "route"))
    }

    @Test
    fun toolPrefixMatchesOnlyPrefix() {
        val match = toolResultMatch(source = ToolResultSource.MCP, toolPrefix = "hotel_")

        assertTrue(match.matches(ToolResultSource.MCP, "travel", "hotel_search"))
        assertFalse(match.matches(ToolResultSource.MCP, "travel", "search_hotel"))
    }

    @Test
    fun serverWideMatchIgnoresToolName() {
        val match = toolResultMatch(source = ToolResultSource.MCP, server = "travel")

        assertTrue(match.matches(ToolResultSource.MCP, "travel", "any_tool"))
        assertFalse(match.matches(ToolResultSource.MCP, "other", "any_tool"))
    }

    @Test
    fun specificityGrowsWithMoreConstraints() {
        assertEquals(0, toolResultMatch().specificity())
        assertTrue(toolResultMatch(source = ToolResultSource.MCP).specificity() < toolResultMatch(source = ToolResultSource.MCP, server = "amap").specificity())
        assertTrue(
            toolResultMatch(source = ToolResultSource.MCP, server = "amap").specificity() <
                toolResultMatch(source = ToolResultSource.MCP, server = "amap", tool = "route").specificity(),
        )
    }
}
