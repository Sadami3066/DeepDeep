package io.github.whywood.deepdeep.screen

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test

class AdbUiAutomatorParserTest {
    private fun snapshot() = GuiNodeTree.buildSnapshot(
        GuiNode("EditText", GuiBounds(0, 0, 100, 100), text = "设置", desc = "设置按钮", resourceId = "com.example:id/title", editable = true),
        packageName = "com.example",
    )

    @Test
    fun virtualSnapshotPreservesNodeFields() {
        val cached = GuiSnapshotCache().store(7, snapshot())
        assertEquals("com.example", cached.packageName)
        assertEquals("设置", cached.nodes.single().text)
        assertEquals("设置按钮", cached.nodes.single().desc)
        assertEquals("com.example:id/title", cached.nodes.single().resourceId)
        assertTrue(cached.nodes.single().editable)
    }

    @Test
    fun refsAreStableWithinSnapshotAndChangeOnObserve() {
        val cache = GuiSnapshotCache()
        val first = cache.store(7, snapshot()).nodes.single().ref
        assertEquals(first, cache.find(7, GuiSelector.Ref(first)).ref)
        val second = cache.store(7, snapshot()).nodes.single().ref
        assertNotEquals(first, second)
        assertThrows(IllegalArgumentException::class.java) { cache.find(7, GuiSelector.Ref(first)) }
    }

    @Test
    fun refCannotCrossDisplay() {
        val cache = GuiSnapshotCache()
        val ref = cache.store(7, snapshot()).nodes.single().ref
        cache.store(8, snapshot())
        assertThrows(IllegalArgumentException::class.java) { cache.find(8, GuiSelector.Ref(ref)) }
    }

    @Test
    fun numericLegacyRefCannotSelectNewNode() {
        val cache = GuiSnapshotCache()
        cache.store(7, snapshot())
        assertThrows(IllegalArgumentException::class.java) { cache.find(7, GuiSelector.Ref("0")) }
    }

    @Test
    fun clearedScreenCannotReuseRefs() {
        val cache = GuiSnapshotCache()
        val ref = cache.store(7, snapshot()).nodes.single().ref
        cache.clear()
        assertThrows(IllegalStateException::class.java) { cache.find(7, GuiSelector.Ref(ref)) }
        cache.store(7, snapshot())
        assertThrows(IllegalArgumentException::class.java) { cache.find(7, GuiSelector.Ref(ref)) }
    }

    @Test
    fun snapshotRejectsNegativeDisplay() {
        assertThrows(IllegalArgumentException::class.java) { GuiSnapshotCache().store(-1, snapshot()) }
    }

    @Test
    fun parsesSupportedKeysAndDurations() {
        assertEquals("back", parseScreenKey(buildJsonObject { put("key", "BACK") }))
        assertEquals(60, tapDurationOf(buildJsonObject {}))
        assertEquals(300L, swipeDurationOf(buildJsonObject {}))
        for (name in listOf("home", "recents", "notifications", "volume_up", "volume_down")) {
            assertEquals(name, parseScreenKey(buildJsonObject { put("key", name) }))
        }
    }
}
