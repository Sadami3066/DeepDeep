package io.github.whywood.deepdeep.ui.chat

import io.github.whywood.deepdeep.runtime.ToolTrace
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PresentCardParseTest {
    @Test
    fun parsesV2Items() {
        val items = requireNotNull(parsePresentedItems(trace()))
        assertEquals(4, items.size)
        assertEquals(
            PresentedItem(
                type = PresentedItemType.IMAGE,
                name = "chart.png",
                path = "/tmp/chart.png",
                mime = "image/png",
            ),
            items[0],
        )
        assertEquals(PresentedItemType.FILE, items[1].type)
        assertEquals("Q3 报告", items[1].title)
        assertEquals("q3.md", items[1].name)
        assertEquals(PresentedItemType.HTML, items[2].type)
        assertEquals("index.html", items[2].name)
        assertEquals(
            PresentedItem(
                type = PresentedItemType.CREATIVE,
                name = "周报看板",
                title = "本周",
                id = "weekly-board",
                icon = "📊",
                version = "1.2.0",
            ),
            items[3],
        )
    }

    @Test
    fun parsesLegacyFiles() {
        val items = requireNotNull(
            parsePresentedItems(
                trace(
                    output = """
                        {"files":[
                          {"path":"reports/q3.md"},
                          {"path":"slides.pptx"},
                          {"path":"out/demo/"},
                          {"path":"photo.png"},
                          {"path":"clips/demo.mp4"},
                          {"path":"pages/index.htm"}
                        ]}
                    """.trimIndent(),
                ),
            ),
        )
        assertEquals(
            listOf(
                PresentedItemType.FILE,
                PresentedItemType.FILE,
                PresentedItemType.FILE,
                PresentedItemType.IMAGE,
                PresentedItemType.VIDEO,
                PresentedItemType.HTML,
            ),
            items.map { it.type },
        )
        assertEquals(
            listOf("q3.md", "slides.pptx", "demo", "photo.png", "demo.mp4", "index.htm"),
            items.map { it.name },
        )
        assertEquals("out/demo/", items[2].path)
    }

    @Test
    fun dropsBadItemsButKeepsRest() {
        val items = requireNotNull(
            parsePresentedItems(
                trace(
                    output = """
                        {"items":[
                          {"type":"image","path":"a.png"},
                          {"type":"video","path":"b.mp4"},
                          {"type":"file"},
                          {"type":"creative"},
                          {"type":"creative","id":"flow","name":"Flow"},
                          {"path":"no-type.md"}
                        ]}
                    """.trimIndent(),
                ),
            ),
        )
        assertEquals(3, items.size)
        assertEquals(PresentedItemType.IMAGE, items[0].type)
        assertEquals("a.png", items[0].name)
        assertEquals(PresentedItemType.VIDEO, items[1].type)
        assertEquals("b.mp4", items[1].name)
        assertEquals(PresentedItemType.CREATIVE, items[2].type)
        assertEquals("flow", items[2].id)
    }

    @Test
    fun derivesNamesAndTrimsTitles() {
        val items = requireNotNull(
            parsePresentedItems(
                trace(
                    output = """
                        {"items":[
                          {"type":"file","path":"  reports/final.pdf  ","title":"  Final  "},
                          {"type":"image","path":"/tmp/photo.jpg","name":" 自定义 "}
                        ]}
                    """.trimIndent(),
                ),
            ),
        )
        assertEquals("final.pdf", items[0].name)
        assertEquals("reports/final.pdf", items[0].path)
        assertEquals("Final", items[0].title)
        assertEquals("自定义", items[1].name)
    }

    @Test
    fun ignoresOtherToolsAndMissingOutput() {
        assertNull(parsePresentedItems(trace(name = "bash")))
        assertNull(parsePresentedItems(trace(name = "workshop_load")))
        assertNull(
            parsePresentedItems(
                ToolTrace(id = 1L, name = "present", arguments = emptyMap(), output = null),
            ),
        )
    }

    @Test
    fun parsesVideoItems() {
        val item = requireNotNull(
            parsePresentedItems(
                trace(
                    output = """{"items":[{"type":"video","path":"/tmp/clip.mp4","name":"clip.mp4","mime":"video/mp4","title":"Demo"}]}""",
                ),
            ),
        ).single()
        assertEquals(PresentedItemType.VIDEO, item.type)
        assertEquals("clip.mp4", item.name)
        assertEquals("Demo", item.title)
    }

    @Test
    fun malformedOrEmptyOutputReturnsNull() {
        assertNull(parsePresentedItems(trace(output = "not json")))
        assertNull(parsePresentedItems(trace(output = "  ")))
        assertNull(parsePresentedItems(trace(output = "{}")))
        assertNull(parsePresentedItems(trace(output = """{"items":[]}""")))
        assertNull(parsePresentedItems(trace(output = """{"items":{}}""")))
        assertNull(parsePresentedItems(trace(output = """{"items":[{}, {"type":"file"}]}""")))
        assertNull(parsePresentedItems(trace(output = """{"files":[]}""")))
        assertNull(parsePresentedItems(trace(output = """{"files":{}}""")))
        assertNull(parsePresentedItems(trace(output = """{"files":[{"path":"  "}]}""")))
    }

    @Test
    fun resolvesRelativeAndWorkspacePaths() {
        val root = File("/data/user/0/app/files/workspace")
        assertEquals(
            File(root, "reports/q3.md").absolutePath,
            resolvePresentedPath("reports/q3.md", root),
        )
        assertEquals(
            File(root, "reports/q3.md").absolutePath,
            resolvePresentedPath("workspace/reports/q3.md", root),
        )
        assertEquals(
            File(root, "notes.md").absolutePath,
            resolvePresentedPath("/workspace/notes.md", root),
        )
        assertTrue(resolvePresentedPath("/tmp/abs.png", root).endsWith("abs.png"))
        assertEquals("/tmp/abs.png", resolvePresentedPath("/tmp/abs.png", root))
    }

    private fun trace(
        output: String? = """
            {"items":[
              {"type":"image","path":"/tmp/chart.png","name":"chart.png","mime":"image/png"},
              {"type":"file","path":"reports/q3.md","name":"q3.md","mime":"text/markdown","title":"Q3 报告"},
              {"type":"html","path":"site/index.html","name":"index.html","mime":"text/html"},
              {"type":"creative","id":"weekly-board","name":"周报看板","icon":"📊","version":"1.2.0","title":"本周"}
            ]}
        """.trimIndent(),
        name: String = "present",
    ): ToolTrace = ToolTrace(id = 1L, name = name, arguments = emptyMap(), output = output)
}
