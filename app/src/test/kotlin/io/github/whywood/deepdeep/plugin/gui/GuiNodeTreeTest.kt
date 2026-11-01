package io.github.whywood.deepdeep.plugin.gui

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
    fun scannedCountsNodesBeyondDepthBudget() {
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
        assertEquals(5, snapshot.scanned)
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
    fun matchesTextAndDescAsCaseInsensitiveSubstrings() {
        val target = snapshotNode(
            text = "Confirm Order",
            desc = "Submit Button",
        )
        assertTrue(GuiNodeTree.matches(target, GuiSelector.Text("confirm")))
        assertTrue(GuiNodeTree.matches(target, GuiSelector.Text("m o")))
        assertFalse(GuiNodeTree.matches(target, GuiSelector.Text("cancel")))
        assertTrue(GuiNodeTree.matches(target, GuiSelector.Desc("button")))
        assertFalse(GuiNodeTree.matches(target, GuiSelector.Desc("cancel")))
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
