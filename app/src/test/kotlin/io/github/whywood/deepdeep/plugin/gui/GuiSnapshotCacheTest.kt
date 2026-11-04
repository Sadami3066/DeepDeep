package io.github.whywood.deepdeep.plugin.gui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GuiSnapshotCacheTest {
    private val cache = GuiSnapshotCache()

    @Test
    fun storeStampsDisplayGenerationRefsAndResolvesSelectors() {
        val stored = cache.store(3, snapshot("OK"))
        assertTrue(stored.nodes.first().ref.startsWith("3:"))
        assertEquals("OK", cache.find(3, GuiSelector.Text("OK"))?.text)
        assertNull(cache.findOrNull(3, GuiSelector.Text("Missing")))
    }

    @Test
    fun oldGenerationRefsStillResolveAsRelocationTargets() {
        val first = cache.store(3, snapshot("OK"))
        val oldRef = first.nodes.single().ref
        val second = cache.store(3, snapshot("Cancel"))
        assertNull(cache.findOrNull(3, GuiSelector.Ref(oldRef)))
        assertEquals("OK", cache.findTarget(3, GuiSelector.Ref(oldRef))?.text)
        assertEquals("Cancel", cache.findOrNull(3, GuiSelector.Ref(second.nodes.single().ref))?.text)
    }

    @Test
    fun unknownRefsBeforeAnyObservationHaveNoTarget() {
        assertNull(cache.findOrNull(4, GuiSelector.Ref("4:abc:0")))
        assertNull(cache.findTarget(4, GuiSelector.Ref("4:abc:0")))
        assertNull(cache.snapshot(4))
    }

    @Test
    fun findThrowsForMissingSnapshotOrNode() {
        assertThrows(IllegalStateException::class.java) {
            cache.find(4, GuiSelector.Text("OK"))
        }
        cache.store(4, snapshot("OK"))
        assertThrows(IllegalArgumentException::class.java) {
            cache.find(4, GuiSelector.Text("Missing"))
        }
        assertNotNull(cache.find(4, GuiSelector.Text("OK")))
    }

    @Test
    fun removeKeepsKnownIdentityButClearDropsIt() {
        val stored = cache.store(5, snapshot("OK"))
        val ref = stored.nodes.single().ref
        cache.remove(5)
        assertNull(cache.findOrNull(5, GuiSelector.Ref(ref)))
        assertEquals("OK", cache.findTarget(5, GuiSelector.Ref(ref))?.text)
        cache.clear()
        assertNull(cache.findTarget(5, GuiSelector.Ref(ref)))
    }

    @Test
    fun negativeDisplayIdsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { cache.store(-1, snapshot("OK")) }
        assertThrows(IllegalArgumentException::class.java) { cache.findOrNull(-1, GuiSelector.Text("OK")) }
    }

    private fun snapshot(text: String): GuiSnapshot = GuiNodeTree.buildSnapshot(
        root = GuiNode(
            className = "android.widget.TextView",
            bounds = GuiBounds(0, 0, 100, 100),
            text = text,
        ),
        packageName = "com.example",
        maxDepth = 4,
        maxNodes = 20,
    )
}
