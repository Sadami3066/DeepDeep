package io.github.whywood.deepdeep.screen

import io.github.whywood.deepdeep.plugin.gui.GuiBounds
import io.github.whywood.deepdeep.plugin.gui.GuiNode
import io.github.whywood.deepdeep.plugin.gui.GuiNodeTree
import io.github.whywood.deepdeep.plugin.gui.GuiScrollDirection
import io.github.whywood.deepdeep.plugin.gui.GuiSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenBoundsTest {
    private val display = GuiBounds(0, 0, 1080, 2400)

    @Test
    fun usableBoundsMustBePositiveAndIntersectDisplay() {
        assertTrue(GuiBounds(0, 0, 1080, 2400).isUsableOn(display))
        assertTrue(GuiBounds(1000, 100, 1500, 300).isUsableOn(display))
        assertFalse(GuiBounds(79, 2433, 202, 2400).isUsableOn(display))
        assertFalse(GuiBounds(300, 100, 200, 400).isUsableOn(display))
        assertFalse(GuiBounds(1080, 157, 1080, 2400).isUsableOn(display))
        assertFalse(GuiBounds(0, 2400, 500, 2500).isUsableOn(display))
        assertFalse(GuiBounds(-500, 0, -100, 200).isUsableOn(display))
        assertFalse(GuiBounds(0, 0, 0, 0).isUsableOn(display))
    }

    @Test
    fun pruningDropsUnlaidOutNodesAndKeepsOriginalRefs() {
        val snapshot = GuiNodeTree.buildSnapshot(
            root = GuiNode(
                className = "android.widget.FrameLayout",
                bounds = GuiBounds(0, 0, 1080, 2400),
                children = listOf(
                    GuiNode(
                        className = "androidx.viewpager.widget.ViewPager",
                        bounds = GuiBounds(79, 2433, 202, 2400),
                        text = "未布局页",
                    ),
                    GuiNode(
                        className = "android.widget.FrameLayout",
                        bounds = GuiBounds(1080, 157, 1080, 2400),
                    ),
                    GuiNode(
                        className = "android.widget.Button",
                        bounds = GuiBounds(100, 50, 300, 150),
                        text = "可见按钮",
                    ),
                ),
            ),
            maxDepth = SCREEN_DEFAULT_MAX_DEPTH,
            maxNodes = 400,
        )
        assertEquals(4, snapshot.nodes.size)
        val pruned = snapshot.usableOn(display)
        assertEquals(listOf("0", "3"), pruned.nodes.map { it.ref })
        assertEquals(2, snapshot.nodes.size - pruned.nodes.size)
        assertEquals("可见按钮", findScreenNode(pruned, GuiSelector.Ref("3"))?.text)
        assertNull(findScreenNode(pruned, GuiSelector.Ref("1")))
    }

    @Test
    fun findScreenNodeMatchesSparseRefsAndSelectors() {
        val snapshot = GuiNodeTree.buildSnapshot(
            root = GuiNode(
                className = "root",
                bounds = GuiBounds(0, 0, 100, 100),
                children = listOf(
                    GuiNode(className = "a", bounds = GuiBounds(0, 0, 10, 10), text = "first"),
                    GuiNode(className = "b", bounds = GuiBounds(10, 10, 20, 20), text = "second"),
                ),
            ),
        )
        val pruned = snapshot.usableOn(GuiBounds(0, 0, 100, 100)).copy(nodes = snapshot.nodes.drop(1))
        assertEquals("1", findScreenNode(pruned, GuiSelector.Ref("#1"))?.ref)
        assertEquals("2", findScreenNode(pruned, GuiSelector.Text("second"))?.ref)
        assertNull(findScreenNode(pruned, GuiSelector.Ref("9")))
        assertNull(findScreenNode(pruned, GuiSelector.Ref("not-a-ref")))
    }

    @Test
    fun clampedSwipePathStaysInsideDisplay() {
        assertEquals(
            listOf(500, 1500, 500, 500),
            clampedSwipePath(GuiScrollDirection.DOWN, GuiBounds(0, 0, 1000, 2000), display)?.toList(),
        )
        val clamped = clampedSwipePath(GuiScrollDirection.DOWN, GuiBounds(1000, 2000, 1500, 2400), display)
        assertNotNull(clamped)
        assertEquals(listOf(1079, 2300, 1079, 2100), clamped?.toList())
    }

    @Test
    fun clampedSwipePathRejectsUnusableOrDegenerateTargets() {
        assertNull(clampedSwipePath(GuiScrollDirection.DOWN, GuiBounds(79, 2433, 202, 2400), display))
        assertNull(clampedSwipePath(GuiScrollDirection.DOWN, GuiBounds(0, 2400, 500, 2500), display))
        assertNull(clampedSwipePath(GuiScrollDirection.DOWN, GuiBounds(0, 0, 1, 1), display))
    }

    @Test
    fun centerWithinClampsToDisplay() {
        assertEquals(1079 to 2399, GuiBounds(1000, 2300, 1500, 3000).centerWithin(display))
        assertEquals(500 to 500, GuiBounds(0, 0, 1000, 1000).centerWithin(display))
    }

    @Test
    fun tapCoordinateValidationNamesDisplaySize() {
        assertNull(invalidTapCoordinate(0, 0, display))
        assertNull(invalidTapCoordinate(1079, 2399, display))
        listOf(1080 to 0, -1 to 0, 0 to 2400, 0 to -1).forEach { (x, y) ->
            val error = invalidTapCoordinate(x, y, display)
            assertNotNull(error)
            assertTrue(error.orEmpty().contains("1080×2400"))
            assertTrue(error.orEmpty().contains("点击坐标超出屏幕范围"))
        }
    }

    @Test
    fun swipeCoordinateValidationRejectsOutOfRangeAndZeroLength() {
        assertNull(invalidSwipeCoordinates(0, 0, 1080 - 1, 2400 - 1, display))
        assertEquals("滑动坐标超出屏幕范围", invalidSwipeCoordinates(0, 0, 1080, 10, display)?.substringBefore("："))
        assertTrue(invalidSwipeCoordinates(0, 0, 1080, 10, display).orEmpty().contains("1080×2400"))
        assertTrue(invalidSwipeCoordinates(10, 10, 10, 10, display).orEmpty().contains("起点与终点相同"))
    }

    @Test
    fun defaultDepthCoversFullTree() {
        assertEquals(SCREEN_DEFAULT_MAX_DEPTH, 20)
        val snapshot = GuiNodeTree.buildSnapshot(
            root = deepRoot(18),
            maxDepth = SCREEN_DEFAULT_MAX_DEPTH,
            maxNodes = 400,
        )
        assertFalse(snapshot.truncated)
        assertEquals(19, snapshot.nodes.size)
    }

    private fun deepRoot(depth: Int): GuiNode {
        var node = GuiNode(className = "leaf", bounds = GuiBounds(0, 0, 10, 10), text = "leaf")
        repeat(depth) {
            node = GuiNode(className = "level", bounds = GuiBounds(0, 0, 100, 100), children = listOf(node))
        }
        return node
    }
}
