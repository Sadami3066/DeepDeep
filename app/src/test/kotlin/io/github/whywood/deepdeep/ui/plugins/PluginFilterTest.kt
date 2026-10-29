package io.github.whywood.deepdeep.ui.plugins

import io.github.whywood.deepdeep.plugin.PluginKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginFilterTest {
    @Test
    fun tabIndexMapsToKind() {
        assertEquals(PluginKind.EXTERNAL, pluginTabKind(0))
        assertEquals(PluginKind.INTERNAL, pluginTabKind(1))
    }

    @Test
    fun emptyQueryMatchesEverything() {
        assertTrue(pluginMatchesQuery("", "feishu", "Feishu", "Connect Feishu"))
        assertTrue(pluginMatchesQuery("   ", "goal", "目标", "推进长期目标"))
    }

    @Test
    fun matchesNameSummaryAndIdCaseInsensitively() {
        assertTrue(pluginMatchesQuery("FEISHU", "feishu", "Feishu", "Sync messages"))
        assertTrue(pluginMatchesQuery("sync", "feishu", "Feishu", "Sync messages"))
        assertTrue(pluginMatchesQuery("fei", "feishu", "Feishu", "Sync messages"))
        assertTrue(pluginMatchesQuery("目标", "goal", "目标", "跨回合推进长期目标"))
        assertFalse(pluginMatchesQuery("missing", "feishu", "Feishu", "Sync messages"))
    }
}
