package io.github.whywood.deepdeep.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GuiNodeTreeTest {
    @Test
    fun boundsFormatCenterAndContains() {
        val bounds = GuiBounds(10, 20, 110, 220)
        assertEquals("[10,20][110,220]", bounds.format())
        assertEquals(60, bounds.centerX)
        assertEquals(120, bounds.centerY)
        assertEquals(100, bounds.width)
        assertEquals(200, bounds.height)
        assertTrue(bounds.contains(10, 20))
        assertTrue(bounds.contains(110, 220))
        assertFalse(bounds.contains(9, 20))
        assertFalse(bounds.contains(60, 221))
    }

    @Test
    fun snapshotUsesDepthFirstRefsAndTrimsDepth() {
        val tree = node(
            className = "root",
            children = listOf(
                node(
                    className = "a",
                    children = listOf(node(className = "a1", children = listOf(node(className = "a2")))),
                ),
                node(className = "b"),
            ),
        )
        val snapshot = GuiNodeTree.buildSnapshot(
            root = tree,
            packageName = "com.example",
            windowTitle = "Main",
            maxDepth = 1,
            maxNodes = 10,
        )
        assertEquals(listOf("0", "1", "2"), snapshot.nodes.map { it.ref })
        assertEquals(listOf("root", "a", "b"), snapshot.nodes.map { it.className })
        assertEquals(listOf(0, 1, 1), snapshot.nodes.map { it.depth })
        assertEquals("com.example", snapshot.packageName)
        assertEquals("Main", snapshot.windowTitle)
        assertTrue(snapshot.truncated)
    }

    @Test
    fun snapshotRespectsNodeBudget() {
        val tree = node(
            className = "root",
            children = listOf(node(className = "a"), node(className = "b"), node(className = "c")),
        )
        val snapshot = GuiNodeTree.buildSnapshot(root = tree, maxDepth = 5, maxNodes = 2)
        assertEquals(listOf("root", "a"), snapshot.nodes.map { it.className })
        assertTrue(snapshot.truncated)
    }

    @Test
    fun snapshotWithoutTruncationKeepsAllNodes() {
        val tree = node(className = "root", children = listOf(node(className = "a")))
        val snapshot = GuiNodeTree.buildSnapshot(root = tree, maxDepth = 5, maxNodes = 10)
        assertEquals(2, snapshot.nodes.size)
        assertFalse(snapshot.truncated)
    }

    @Test
    fun scannedCountsOnlyVisitedNodes() {
        val tree = node(
            className = "root",
            children = listOf(
                node(
                    className = "a",
                    children = listOf(node(className = "a1", children = listOf(node(className = "a2")))),
                ),
                node(className = "b"),
            ),
        )
        val snapshot = GuiNodeTree.buildSnapshot(root = tree, maxDepth = 1, maxNodes = 10)
        assertEquals(3, snapshot.nodes.size)
        assertEquals(snapshot.nodes.size, snapshot.scanned)
        assertTrue(snapshot.truncated)
    }

    @Test
    fun scannedMatchesNodeCountWhenNothingIsCut() {
        val tree = node(className = "root", children = listOf(node(className = "a")))
        val snapshot = GuiNodeTree.buildSnapshot(root = tree, maxDepth = 5, maxNodes = 10)
        assertEquals(2, snapshot.nodes.size)
        assertEquals(2, snapshot.scanned)
        assertFalse(snapshot.truncated)
    }

    @Test
    fun parseAndResolveRef() {
        val snapshot = snapshotOf(node(className = "root", children = listOf(node(className = "a"))))
        assertEquals(1, GuiNodeTree.parseRef("1"))
        assertEquals(1, GuiNodeTree.parseRef(" #1 "))
        assertNull(GuiNodeTree.parseRef("x"))
        assertNull(GuiNodeTree.parseRef(""))
        assertNull(GuiNodeTree.parseRef("-1"))
        assertEquals("1", GuiNodeTree.findRef(snapshot, "1")?.ref)
        assertEquals("1", GuiNodeTree.findRef(snapshot, "#1")?.ref)
        assertNull(GuiNodeTree.findRef(snapshot, "2"))
        assertNull(GuiNodeTree.findRef(snapshot, "abc"))
    }

    @Test
    fun refIndexParsesGenerationRefs() {
        assertEquals(3, GuiNodeTree.refIndex("0:2f4d:3"))
        assertEquals(3, GuiNodeTree.refIndex("#3"))
        assertEquals(0, GuiNodeTree.refIndex("0"))
        assertNull(GuiNodeTree.refIndex("abc"))
        assertNull(GuiNodeTree.refIndex("-1"))
    }

    @Test
    fun counterpartRelocatesByResourceIdAndDescription() {
        val target = snapshotOf(
            node(className = "android.widget.Button", text = "OK", resourceId = "com.example:id/ok", bounds = GuiBounds(0, 0, 100, 50)),
        ).nodes.single()
        val byId = snapshotOf(
            node(
                className = "android.view.View",
                bounds = GuiBounds(0, 0, 100, 400),
                children = listOf(
                    node(className = "android.view.View", text = "Header", bounds = GuiBounds(0, 0, 100, 50)),
                    node(
                        className = "android.widget.Button",
                        text = "OK",
                        resourceId = "com.example:id/ok",
                        bounds = GuiBounds(0, 300, 100, 350),
                    ),
                ),
            ),
        )
        assertEquals("com.example:id/ok", GuiNodeTree.counterpart(byId, target)?.resourceId)

        val described = snapshotOf(
            node(className = "android.widget.ImageButton", desc = "提交订单", bounds = GuiBounds(0, 0, 100, 50)),
        ).nodes.single()
        val afterDescriptionChange = snapshotOf(
            node(className = "android.widget.ImageButton", desc = "提交订单", bounds = GuiBounds(0, 500, 100, 550)),
        )
        assertEquals(500, GuiNodeTree.counterpart(afterDescriptionChange, described)?.bounds?.top)
    }

    @Test
    fun counterpartPrefersClosestRepeatedLabel() {
        val target = snapshotOf(
            node(className = "android.widget.TextView", text = "详情", bounds = GuiBounds(0, 100, 100, 150)),
        ).nodes.single()
        val after = snapshotOf(
            node(
                className = "android.view.View",
                bounds = GuiBounds(0, 0, 100, 1200),
                children = listOf(
                    node(className = "android.widget.TextView", text = "详情", bounds = GuiBounds(0, 1100, 100, 1150)),
                    node(className = "android.widget.TextView", text = "详情", bounds = GuiBounds(0, 120, 100, 170)),
                ),
            ),
        )
        assertEquals(120, GuiNodeTree.counterpart(after, target)?.bounds?.top)
    }

    @Test
    fun counterpartRelocatesUnlabeledNodeByClassAndBounds() {
        val target = snapshotOf(
            node(className = "android.widget.ImageView", bounds = GuiBounds(10, 10, 60, 60)),
        ).nodes.single()
        val overlapping = snapshotOf(
            node(className = "android.widget.ImageView", bounds = GuiBounds(12, 14, 62, 64)),
        )
        assertEquals(overlapping.nodes[0], GuiNodeTree.counterpart(overlapping, target))
        val noSameNode = snapshotOf(
            node(
                className = "android.view.View",
                bounds = GuiBounds(0, 0, 100, 1200),
                children = listOf(
                    node(className = "android.widget.TextView", text = "详情", bounds = GuiBounds(0, 0, 100, 50)),
                    node(className = "android.widget.ImageView", bounds = GuiBounds(500, 500, 550, 550)),
                ),
            ),
        )
        assertNull(GuiNodeTree.counterpart(noSameNode, target))
    }

    @Test
    fun counterpartRejectsDifferentLabelsAndIds() {
        val target = snapshotOf(
            node(className = "android.widget.Button", text = "OK", bounds = GuiBounds(0, 0, 100, 50)),
        ).nodes.single()
        val otherLabel = snapshotOf(
            node(className = "android.widget.Button", text = "Cancel", bounds = GuiBounds(0, 0, 100, 50)),
        )
        assertNull(GuiNodeTree.counterpart(otherLabel, target))

        val identified = snapshotOf(
            node(className = "android.widget.Button", resourceId = "com.example:id/ok", bounds = GuiBounds(0, 0, 100, 50)),
        ).nodes.single()
        val rebuiltId = snapshotOf(
            node(className = "android.widget.Button", resourceId = "com.example:id/cancel", bounds = GuiBounds(0, 0, 100, 50)),
        )
        assertNull(GuiNodeTree.counterpart(rebuiltId, identified))
    }

    @Test
    fun counterpartWithoutCandidatesReturnsNull() {
        val target = snapshotOf(node(className = "root")).nodes.single()
        assertNull(GuiNodeTree.counterpart(GuiSnapshot(null, null, emptyList(), false), target))
    }

    @Test
    fun matchesTextAndDescAsCaseInsensitiveSubstrings() {
        val target = snapshotNode(
            text = "Confirm Order",
            desc = "Submit Button",
        )
        assertTrue(GuiNodeTree.matches(target, GuiSelector.Text("confirm")))
        assertTrue(GuiNodeTree.matches(target, GuiSelector.Text("m o")))
        assertTrue(GuiNodeTree.matches(target, GuiSelector.Text("confi rmo")))
        assertFalse(GuiNodeTree.matches(target, GuiSelector.Text("cancel")))
        assertTrue(GuiNodeTree.matches(target, GuiSelector.Desc("button")))
        assertFalse(GuiNodeTree.matches(target, GuiSelector.Desc("cancel")))
    }

    @Test
    fun textSelectorAlsoMatchesContentDescription() {
        val target = snapshotNode(text = null, desc = "Submit Button")
        assertTrue(GuiNodeTree.matches(target, GuiSelector.Text("submit")))
        assertTrue(GuiNodeTree.matches(target, GuiSelector.Text("Submit Button")))
        assertFalse(GuiNodeTree.matches(snapshotNode(text = "Submit", desc = null), GuiSelector.Text("cancel")))
    }

    @Test
    fun textMatchingIgnoresSpacesAndSeparators() {
        val formatted = snapshotNode(text = "9,801")
        assertTrue(GuiNodeTree.matches(formatted, GuiSelector.Text("9801")))
        assertTrue(GuiNodeTree.matches(formatted, GuiSelector.Text("9 801")))
        assertTrue(GuiNodeTree.matches(formatted, GuiSelector.Text("9,801")))
        val price = snapshotNode(text = "1 234.56 元")
        assertTrue(GuiNodeTree.matches(price, GuiSelector.Text("1234.56")))
        assertTrue(GuiNodeTree.matches(price, GuiSelector.Text("23456元")))
        val desc = snapshotNode(desc = "1，234 次点击")
        assertTrue(GuiNodeTree.matches(desc, GuiSelector.Text("1234次")))
    }

    @Test
    fun separatorOnlyTextMatchesNothing() {
        val target = snapshotNode(text = "9,801")
        assertFalse(GuiNodeTree.matches(target, GuiSelector.Text(",")))
        assertFalse(GuiNodeTree.matches(target, GuiSelector.Text(" ")))
        assertFalse(GuiNodeTree.matches(target, GuiSelector.Desc("...")))
        assertEquals("", normalizedMatchText(" , . 、 "))
        assertEquals("9801", normalizedMatchText(" 9,801 "))
        assertEquals("ab-c", normalizedMatchText("A B-C_"))
    }

    @Test
    fun matchesResourceIdFullAndShortForms() {
        val target = snapshotNode(resourceId = "com.example:id/ok")
        assertTrue(GuiNodeTree.matches(target, GuiSelector.ResourceId("com.example:id/ok")))
        assertTrue(GuiNodeTree.matches(target, GuiSelector.ResourceId("ok")))
        assertFalse(GuiNodeTree.matches(target, GuiSelector.ResourceId("cancel")))
        assertFalse(GuiNodeTree.matches(snapshotNode(resourceId = null), GuiSelector.ResourceId("ok")))
    }

    @Test
    fun matchesClassNameFullAndSimpleForms() {
        val target = snapshotNode(className = "android.widget.Button")
        assertTrue(GuiNodeTree.matches(target, GuiSelector.ClassName("android.widget.Button")))
        assertTrue(GuiNodeTree.matches(target, GuiSelector.ClassName("Button")))
        assertFalse(GuiNodeTree.matches(target, GuiSelector.ClassName("EditText")))
    }

    @Test
    fun matchesPointInsideBounds() {
        val target = snapshotNode(bounds = GuiBounds(10, 20, 110, 220))
        assertTrue(GuiNodeTree.matches(target, GuiSelector.Point(60, 120)))
        assertTrue(GuiNodeTree.matches(target, GuiSelector.Point(10, 20)))
        assertFalse(GuiNodeTree.matches(target, GuiSelector.Point(5, 20)))
    }

    @Test
    fun findPrefersRefAndFirstMatchingNode() {
        val tree = node(
            className = "root",
            children = listOf(
                node(className = "android.widget.Button", text = "OK"),
                node(className = "android.widget.Button", text = "OK"),
            ),
        )
        val snapshot = snapshotOf(tree)
        assertEquals("1", GuiNodeTree.find(snapshot, GuiSelector.Ref("1"))?.ref)
        assertEquals("1", GuiNodeTree.find(snapshot, GuiSelector.Text("ok"))?.ref)
        assertEquals("0", GuiNodeTree.find(snapshot, GuiSelector.ClassName("root"))?.ref)
        assertNull(GuiNodeTree.find(snapshot, GuiSelector.Text("missing")))
    }

    @Test
    fun renderProducesCompactJsonWithBounds() {
        val tree = node(
            className = "android.widget.Button",
            text = "OK",
            resourceId = "com.example:id/ok",
            bounds = GuiBounds(10, 20, 110, 220),
            clickable = true,
        )
        val json = GuiNodeTree.render(snapshotOf(tree).copy(packageName = "com.example", windowTitle = "Main"))
        assertTrue(json.contains("\"package\":\"com.example\""))
        assertTrue(json.contains("\"title\":\"Main\""))
        assertTrue(json.contains("\"count\":1"))
        assertTrue(json.contains("\"truncated\":false"))
        assertTrue(json.contains("\"ref\":\"0\""))
        assertTrue(json.contains("\"class\":\"android.widget.Button\""))
        assertTrue(json.contains("\"text\":\"OK\""))
        assertTrue(json.contains("\"resourceId\":\"com.example:id/ok\""))
        assertTrue(json.contains("\"bounds\":\"[10,20][110,220]\""))
        assertTrue(json.contains("\"clickable\":true"))
        assertFalse(json.contains("\"editable\""))
        assertFalse(json.contains("\"scrollable\""))
        assertFalse(json.contains("\"enabled\""))
        assertFalse(json.contains("\"desc\""))
    }

    @Test
    fun renderReportsDisabledNodes() {
        val tree = node(className = "root", enabled = false)
        val json = GuiNodeTree.render(snapshotOf(tree))
        assertTrue(json.contains("\"enabled\":false"))
    }

    @Test
    fun tokensAreCarriedButNeverRendered() {
        val handle = Any()
        val tree = node(className = "root", token = handle)
        val snapshot = snapshotOf(tree)
        assertSame(handle, snapshot.nodes.single().token)
        assertFalse(GuiNodeTree.render(snapshot).contains("java.lang.Object"))
    }

    @Test
    fun matchesAllRequiresEverySelector() {
        val target = snapshotNode(
            className = "android.widget.Button",
            text = "确定",
            resourceId = "com.example:id/ok",
        )
        assertTrue(
            GuiNodeTree.matchesAll(
                target,
                listOf(
                    GuiSelector.Text("确定"),
                    GuiSelector.ClassName("Button"),
                    GuiSelector.ResourceId("ok"),
                ),
            ),
        )
        assertFalse(
            GuiNodeTree.matchesAll(
                target,
                listOf(GuiSelector.Text("确定"), GuiSelector.ClassName("EditText")),
            ),
        )
        assertFalse(GuiNodeTree.matchesAll(target, emptyList()))
    }

    @Test
    fun findAllFiltersAndHonorsLimit() {
        val tree = node(
            className = "root",
            children = listOf(
                node(className = "android.widget.Button", text = "OK"),
                node(className = "android.widget.Button", text = "OK"),
                node(className = "android.widget.Button", text = "Cancel"),
            ),
        )
        val snapshot = snapshotOf(tree)
        val matches = GuiNodeTree.findAll(snapshot, listOf(GuiSelector.Text("ok")), limit = 10)
        assertEquals(listOf("1", "2"), matches.map { it.ref })
        assertEquals(1, GuiNodeTree.findAll(snapshot, listOf(GuiSelector.Text("ok")), limit = 1).size)
        assertTrue(GuiNodeTree.findAll(snapshot, listOf(GuiSelector.Text("missing")), limit = 10).isEmpty())
    }

    @Test
    fun nodeJsonRendersSingleNode() {
        val node = snapshotNode(
            className = "android.widget.Button",
            text = "OK",
            resourceId = "com.example:id/ok",
            bounds = GuiBounds(10, 20, 110, 220),
        )
        val json = GuiNodeTree.nodeJson(node).toString()
        assertTrue(json.contains("\"ref\":\"0\""))
        assertTrue(json.contains("\"class\":\"android.widget.Button\""))
        assertTrue(json.contains("\"text\":\"OK\""))
        assertTrue(json.contains("\"resourceId\":\"com.example:id/ok\""))
        assertTrue(json.contains("\"bounds\":\"[10,20][110,220]\""))
    }

    private fun node(
        className: String = "android.view.View",
        text: String? = null,
        desc: String? = null,
        resourceId: String? = null,
        bounds: GuiBounds = GuiBounds(0, 0, 100, 200),
        clickable: Boolean = false,
        scrollable: Boolean = false,
        editable: Boolean = false,
        enabled: Boolean = true,
        children: List<GuiNode> = emptyList(),
        token: Any? = null,
    ): GuiNode = GuiNode(
        className = className,
        bounds = bounds,
        text = text,
        desc = desc,
        resourceId = resourceId,
        clickable = clickable,
        scrollable = scrollable,
        editable = editable,
        enabled = enabled,
        children = children,
        token = token,
    )

    private fun snapshotOf(tree: GuiNode): GuiSnapshot =
        GuiNodeTree.buildSnapshot(root = tree, maxDepth = 10, maxNodes = 50)

    private fun snapshotNode(
        className: String = "android.view.View",
        text: String? = null,
        desc: String? = null,
        resourceId: String? = null,
        bounds: GuiBounds = GuiBounds(0, 0, 100, 200),
    ): GuiSnapshotNode = snapshotOf(
        node(
            className = className,
            text = text,
            desc = desc,
            resourceId = resourceId,
            bounds = bounds,
        ),
    ).nodes.single()
}
