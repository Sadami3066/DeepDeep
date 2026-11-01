package io.github.whywood.deepdeep.screen

import io.github.whywood.deepdeep.plugin.gui.GuiNodeTree
import io.github.whywood.deepdeep.plugin.gui.GuiSelector
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbUiAutomatorParserTest {
    private val xml = """
        <?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
        <hierarchy rotation="0">
          <node index="0" text="" resource-id="" class="android.widget.FrameLayout" package="com.example" content-desc="" clickable="false" enabled="true" bounds="[0,0][1080,2400]">
            <node index="0" text="设置" resource-id="com.example:id/title" class="android.widget.TextView" package="com.example" content-desc="设置按钮" clickable="true" enabled="true" bounds="[100,200][300,280]" />
            <node index="1" text="" resource-id="" class="android.widget.EditText" package="com.example" content-desc="" clickable="false" enabled="true" bounds="[50,300][1000,400]" />
          </node>
        </hierarchy>
    """.trimIndent()

    @Test
    fun parsesHierarchyIntoNodeTree() {
        val parsed = AdbUiAutomatorParser.parse(xml) ?: error("expected parsed hierarchy")
        assertEquals("com.example", parsed.packageName)
        assertEquals("android.widget.FrameLayout", parsed.root.className)
        assertEquals(2, parsed.root.children.size)
        val title = parsed.root.children[0]
        assertEquals("设置", title.text)
        assertEquals("设置按钮", title.desc)
        assertEquals("com.example:id/title", title.resourceId)
        assertTrue(title.clickable)
        assertEquals(100, title.bounds.left)
        assertEquals(280, title.bounds.bottom)
        val edit = parsed.root.children[1]
        assertTrue(edit.editable)
    }

    @Test
    fun buildsSnapshotWithStableRefs() {
        val parsed = AdbUiAutomatorParser.parse(xml) ?: error("expected parsed hierarchy")
        val snapshot = GuiNodeTree.buildSnapshot(
            root = parsed.root,
            packageName = parsed.packageName,
            maxDepth = 8,
            maxNodes = 120,
        )
        assertEquals(3, snapshot.nodes.size)
        assertEquals("0", snapshot.nodes[0].ref)
        assertEquals("1", snapshot.nodes[1].ref)
        assertEquals("设置", snapshot.nodes[1].text)
        val found = GuiNodeTree.find(snapshot, GuiSelector.Text("设置"))
        assertEquals("1", found?.ref)
        val byPoint = GuiNodeTree.find(snapshot, GuiSelector.Point(200, 240))
        assertTrue(byPoint?.bounds?.contains(200, 240) == true)
    }

    @Test
    fun missingPackageFallsBackToChildPackage() {
        val nested = """
            <hierarchy>
              <node text="" class="android.widget.FrameLayout" bounds="[0,0][10,10]">
                <node text="hi" class="android.widget.TextView" package="com.nested" bounds="[0,0][5,5]" />
              </node>
            </hierarchy>
        """.trimIndent()
        val parsed = AdbUiAutomatorParser.parse(nested) ?: error("expected parsed hierarchy")
        assertEquals("com.nested", parsed.packageName)
    }

    @Test
    fun invalidXmlReturnsNull() {
        assertNull(AdbUiAutomatorParser.parse(""))
        assertNull(AdbUiAutomatorParser.parse("not xml at all"))
    }

    @Test
    fun uiDumpCommandDropsStaleSnapshotBeforeDumping() {
        val command = adbUiDumpCommand()
        assertTrue(command.startsWith("rm -f $ADB_UI_DUMP_PATH; "))
        assertTrue(command.contains("uiautomator dump $ADB_UI_DUMP_PATH"))
        assertTrue(command.contains("&& cat $ADB_UI_DUMP_PATH"))
        assertFalse(command.contains("; cat"))
    }

    @Test
    fun buildsSafeAdbInputCommand() {
        assertEquals("input text 'hello%sworld'", adbInputTextCommand("hello world"))
        assertEquals("input text 'it'\\''s'", adbInputTextCommand("it's"))
    }

    @Test
    fun parsesTargetAndDurations() {
        assertEquals(ScreenTarget.MAIN, targetOf(buildJsonObject { }))
        assertEquals(ScreenTarget.MAIN, targetOf(buildJsonObject { put("target", "main") }))
        assertEquals(ScreenTarget.VIRTUAL, targetOf(buildJsonObject { put("target", "VIRTUAL") }))
        try {
            targetOf(buildJsonObject { put("target", "second") })
            error("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("target"))
        }
    }
}
