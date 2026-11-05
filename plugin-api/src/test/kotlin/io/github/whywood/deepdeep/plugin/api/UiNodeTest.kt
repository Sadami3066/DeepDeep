package io.github.whywood.deepdeep.plugin.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiNodeTest {
    private fun issues(node: UiNode) = PluginValidation.validateUiNode(node)

    private fun validTree(): UiNode = UiNode(
        UiNodeTypes.COLUMN,
        children = listOf(
            UiNode(UiNodeTypes.TEXT, text = "Hello", props = mapOf("style" to "title")),
            UiNode(UiNodeTypes.IMAGE, props = mapOf("source" to "asset:icon.png")),
            UiNode(UiNodeTypes.BUTTON, text = "Sync", props = mapOf("action" to "sync")),
            UiNode(UiNodeTypes.PROGRESS, props = mapOf("value" to "50")),
            UiNode(UiNodeTypes.QR_CODE, props = mapOf("data" to "https://example.com")),
            UiNode(UiNodeTypes.DIVIDER),
            UiNode(UiNodeTypes.SPACER, props = mapOf("height" to "12")),
            UiNode(UiNodeTypes.LINK, text = "Docs", props = mapOf("url" to "https://example.com")),
            UiNode(UiNodeTypes.MARKDOWN, text = "# Title"),
            UiNode(UiNodeTypes.ROW, children = listOf(UiNode(UiNodeTypes.TEXT, text = "child"))),
        ),
    )

    @Test
    fun validTreeHasNoIssues() {
        assertEquals(emptyList<PluginValidation.Issue>(), issues(validTree()))
    }

    @Test
    fun rejectsUnknownNodeType() {
        val result = issues(UiNode("chart"))
        assertEquals(1, result.size)
        assertEquals(PluginValidation.Code.INVALID_UI_NODE, result.single().code)
        assertTrue(result.single().message.contains("unsupported node type"))
    }

    @Test
    fun textNodeRequiresTextAndRejectsChildren() {
        assertEquals(1, issues(UiNode(UiNodeTypes.TEXT)).size)
        assertEquals(
            1,
            issues(UiNode(UiNodeTypes.TEXT, text = "x", children = listOf(UiNode(UiNodeTypes.TEXT, text = "y")))).size,
        )
    }

    @Test
    fun validatesImageSource() {
        assertTrue(issues(UiNode(UiNodeTypes.IMAGE, props = mapOf("source" to "https://example.com/a.png"))).isEmpty())
        assertTrue(issues(UiNode(UiNodeTypes.IMAGE, props = mapOf("source" to "asset:icon.png"))).isEmpty())
        assertEquals(1, issues(UiNode(UiNodeTypes.IMAGE, props = mapOf("source" to "file:///tmp/a.png"))).size)
        assertEquals(1, issues(UiNode(UiNodeTypes.IMAGE, props = mapOf("source" to "asset:../a.png"))).size)
        assertEquals(1, issues(UiNode(UiNodeTypes.IMAGE)).size)
    }

    @Test
    fun validatesButtonAction() {
        assertEquals(1, issues(UiNode(UiNodeTypes.BUTTON, text = "Run")).size)
        assertEquals(1, issues(UiNode(UiNodeTypes.BUTTON, text = "Run", props = mapOf("action" to "Bad Action"))).size)
        assertTrue(issues(UiNode(UiNodeTypes.BUTTON, text = "Run", props = mapOf("action" to "run"))).isEmpty())
    }

    @Test
    fun validatesLinkUrl() {
        assertTrue(issues(UiNode(UiNodeTypes.LINK, text = "Mail", props = mapOf("url" to "mailto:a@b.com"))).isEmpty())
        assertEquals(1, issues(UiNode(UiNodeTypes.LINK, text = "Ftp", props = mapOf("url" to "ftp://example.com"))).size)
    }

    @Test
    fun validatesBoundedProps() {
        assertEquals(1, issues(UiNode(UiNodeTypes.PROGRESS, props = mapOf("value" to "150"))).size)
        assertEquals(1, issues(UiNode(UiNodeTypes.PROGRESS, props = mapOf("value" to "abc"))).size)
        assertTrue(issues(UiNode(UiNodeTypes.PROGRESS)).isEmpty())
        assertEquals(1, issues(UiNode(UiNodeTypes.SPACER, props = mapOf("height" to "999"))).size)
    }

    @Test
    fun unknownPropsAreWarnings() {
        val result = issues(UiNode(UiNodeTypes.TEXT, text = "x", props = mapOf("bogus" to "1")))
        assertEquals(1, result.size)
        assertEquals(PluginValidation.Severity.WARNING, result.single().severity)
    }

    @Test
    fun rejectsDeeplyNestedTree() {
        var node = UiNode(UiNodeTypes.TEXT, text = "leaf")
        repeat(9) { node = UiNode(UiNodeTypes.COLUMN, children = listOf(node)) }
        val result = issues(node)
        assertEquals(1, result.size)
        assertTrue(result.single().message.contains("nested too deep"))
    }

    @Test
    fun rejectsTooManyNodes() {
        val wide = UiNode(
            UiNodeTypes.COLUMN,
            children = List(PluginValidation.MAX_UI_NODES) { UiNode(UiNodeTypes.TEXT, text = "item") },
        )
        val result = issues(wide)
        assertEquals(1, result.size)
        assertTrue(result.single().message.contains("too many ui nodes"))
    }
}

class SlotIdsTest {
    @Test
    fun buildsSlotIds() {
        assertEquals("setup.content", SlotIds.setupContent())
        assertEquals("setup.content", SlotIds.SETUP_CONTENT)
        assertEquals("plugin.detail.section:account", SlotIds.detailSection("account"))
        assertEquals("tool.card.content:sample_echo", SlotIds.toolCard("sample_echo"))
        assertEquals("chat.message:card", SlotIds.chatMessage("card"))
    }

    @Test
    fun matchesSupportedSlotIds() {
        assertTrue(SlotIds.matches(SlotIds.setupContent()))
        assertTrue(SlotIds.matches(SlotIds.detailSection("account")))
        assertTrue(SlotIds.matches(SlotIds.toolCard("sample_echo")))
        assertTrue(SlotIds.matches(SlotIds.chatMessage("card")))
    }

    @Test
    fun rejectsUnsupportedOrIncompleteSlotIds() {
        listOf(
            "",
            "composer.action",
            "plugin.page",
            "plugin.detail.section",
            "plugin.detail.section:",
            "tool.card.content:",
            "chat.message:",
            "setup.content.extra",
            "tool.card.content:sample_echo.extra",
        ).forEach { slotId ->
            assertFalse(slotId, SlotIds.matches(slotId))
        }
    }
}
