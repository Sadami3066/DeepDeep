package io.github.whywood.deepdeep.floating

import io.github.whywood.deepdeep.ui.chat.PresentedItemType
import io.github.whywood.deepdeep.ui.chat.parsePresentedItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FloatingDeliveryTest {
    @Test
    fun parsesDeliveredItemsWithTitleAndMime() {
        val raw = """
            {"items":[{"type":"file","path":"/tmp/report.md","name":"report.md","mime":"text/markdown","title":"季度报告"}]}
        """.trimIndent()
        val items = parsePresentedItems(raw) ?: error("expected parsed items")
        assertEquals(1, items.size)
        val item = items.single()
        assertEquals(PresentedItemType.FILE, item.type)
        assertEquals("report.md", item.name)
        assertEquals("/tmp/report.md", item.path)
        assertEquals("季度报告", item.title)
        assertEquals("text/markdown", item.mime)
    }

    @Test
    fun parsesImageAndCreativeItems() {
        val raw = """
            {"items":[
              {"type":"image","path":"/tmp/cover.png"},
              {"type":"creative","id":"weather","name":"天气助手","icon":"☀","version":"1.2"}
            ]}
        """.trimIndent()
        val items = parsePresentedItems(raw) ?: error("expected parsed items")
        assertEquals(2, items.size)
        assertEquals(PresentedItemType.IMAGE, items[0].type)
        assertEquals(PresentedItemType.CREATIVE, items[1].type)
        assertEquals("weather", items[1].id)
        assertEquals("天气助手", items[1].name)
    }

    @Test
    fun parsesLegacyFileLists() {
        val raw = """{"files":[{"path":"/tmp/slide.pptx"}]}"""
        val items = parsePresentedItems(raw) ?: error("expected parsed items")
        assertEquals(1, items.size)
        assertEquals("slide.pptx", items.single().name)
        assertEquals(PresentedItemType.FILE, items.single().type)
    }

    @Test
    fun malformedPayloadIsRejected() {
        assertNull(parsePresentedItems("not json"))
        assertNull(parsePresentedItems("""{"items":[]}"""))
        assertNull(parsePresentedItems("""{"items":[{"type":"unknown","path":"/tmp/a"}]}"""))
        assertNull(parsePresentedItems("""{"items":[{"type":"file"}]}"""))
    }
}
