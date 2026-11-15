package com.wangzan101.mika.core.uistate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TreeQualityTest {
    private fun el(i: Int, name: String, bounds: List<Int>, clickable: Boolean = false) =
        UiElement("@e$i", "TextView", name, 0, 0, bounds, null, clickable, false, false)

    /** 竖着排一列有名字的元素,每个 60 高。 */
    private fun rows(n: Int, top: Int = 100) = (0 until n).map { i -> el(i + 1, "第${i + 1}行", listOf(50, top + i * 60, 950, top + i * 60 + 50), clickable = true) }

    private fun shot(elements: List<UiElement>, nodes: List<UiTreeNode> = emptyList(), protected: Boolean = false, unreadable: String? = null) =
        ScreenSnapshot("p1", "com.example", elements, 1080, 2400, protected = protected, unreadableApp = unreadable,
            windows = if (nodes.isEmpty()) emptyList() else listOf(UiWindowTree(1, "application", 1, null, "com.example", true, true, nodes)))

    private fun line(text: String, top: Int, confidence: Float? = null) = VisibleTextLine(text, listOf(100, top, 600, top + 40), confidence)

    // ---- 只看树 ----

    @Test fun protectedEmptyAndUnreadableTreesAreClassifiedWithoutOcr() {
        assertEquals(TreeCheck(TreeQuality.Protected), TreeAdequacy.ofTree(shot(rows(10), protected = true)))
        assertEquals(TreeCheck(TreeQuality.Unusable, "empty_elements"), TreeAdequacy.ofTree(shot(emptyList())))
        assertEquals(TreeCheck(TreeQuality.Unusable, "unreadable_window"), TreeAdequacy.ofTree(shot(emptyList(), unreadable = "com.example")))
        // 没有窗口也没有 App:空屏,没什么可补
        assertEquals(TreeQuality.Adequate, TreeAdequacy.ofTree(ScreenSnapshot("p", null, emptyList(), 1080, 2400, hasWindow = false)).quality)
    }

    @Test fun unlabeledControlsArePartialOrUnusable() {
        val blank = (1..4).map { el(it, "", listOf(0, it * 100, 100, it * 100 + 50), clickable = true) }
        assertEquals(TreeCheck(TreeQuality.Unusable, "unlabeled_controls"), TreeAdequacy.ofTree(shot(blank)))
        assertEquals(TreeCheck(TreeQuality.Partial, "unlabeled_controls"), TreeAdequacy.ofTree(shot(blank + el(9, "标题", listOf(0, 0, 999, 80)))))
        // 只有一个没名字的可滚动容器:树里一个字也没有
        val list = UiElement("@e1", "RecyclerView", "", 0, 0, listOf(0, 0, 999, 999), null, false, false, true)
        assertEquals(TreeCheck(TreeQuality.Unusable, "no_text"), TreeAdequacy.ofTree(shot(listOf(list))))
    }

    @Test fun richTreeIsAdequateButSparseTreeNeedsOcr() {
        assertEquals(TreeCheck(TreeQuality.Adequate), TreeAdequacy.ofTree(shot(rows(8))))
        assertEquals(TreeCheck(null), TreeAdequacy.ofTree(shot(rows(3))))
    }

    @Test fun largeTextlessAreaNeedsOcrUnlessItOnlyBacksNamedElements() {
        val named = rows(8)
        val textNodes = named.map { UiTreeNode("TextView", it.bounds, text = it.name) }
        // 屏幕下半一大块没有字的画布,有名字的元素都在上面
        val canvas = UiTreeNode("View", listOf(0, 600, 999, 999))
        assertEquals(TreeCheck(null), TreeAdequacy.ofTree(shot(named, textNodes + canvas)))
        // 垫在整屏下面的背景图:有名字的元素都落在它里面,不算盲区
        val backdrop = UiTreeNode("ImageView", listOf(0, 0, 999, 999))
        assertEquals(TreeCheck(TreeQuality.Adequate), TreeAdequacy.ofTree(shot(named, listOf(backdrop) + textNodes)))
    }

    // ---- 读字之后 ----

    @Test fun ocrTextOutsideTheTreeDecidesPartialOrAdequate() {
        val unsure = TreeCheck(null)
        assertEquals(TreeCheck(TreeQuality.Partial, "text_outside_tree"), TreeAdequacy.withText(unsure, listOf(line("甲", 100), line("乙", 200), line("丙", 300))))
        assertEquals(TreeCheck(TreeQuality.Adequate), TreeAdequacy.withText(unsure, listOf(line("甲", 100))))
        // 没读到字:照旧判不准,不写 treeQuality
        assertEquals(unsure, TreeAdequacy.withText(unsure, null))
        // 树已经判定的不改
        val empty = TreeCheck(TreeQuality.Unusable, "empty_elements")
        assertEquals(empty, TreeAdequacy.withText(empty, emptyList()))
    }
}
