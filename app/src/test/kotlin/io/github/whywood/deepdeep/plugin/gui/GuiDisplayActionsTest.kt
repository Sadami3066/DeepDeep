package io.github.whywood.deepdeep.plugin.gui

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GuiDisplayActionsTest {
    private fun candidate(
        kind: GuiWindowKind,
        layer: Int = 0,
        readable: Boolean = true,
    ): GuiWindowCandidate = GuiWindowCandidate(kind = kind, layer = layer, readable = readable)

    @Test
    fun accessibilityActionsImplementLegacyAndDisplayContracts() {
        val actions = GuiAccessibilityActions { null }

        assertTrue(actions is GuiActions)
        assertTrue(actions is GuiDisplayActions)
        assertFalse(actions.connected)
    }

    @Test
    fun displayObserveWithoutServiceFailsWithUnavailableMessage() {
        val actions = GuiAccessibilityActions { null }

        val error = assertThrows(GuiUnavailableException::class.java) {
            runBlocking { actions.observe(9, GUI_DEFAULT_MAX_DEPTH, GUI_DEFAULT_MAX_NODES) }
        }

        assertTrue(error.message.orEmpty().contains("无障碍服务未连接"))
    }

    @Test
    fun mainScreenObserveWithoutServiceFailsWithUnavailableMessage() {
        val actions = GuiAccessibilityActions { null }

        val error = assertThrows(GuiUnavailableException::class.java) {
            runBlocking { actions.observe(GUI_DEFAULT_MAX_DEPTH, GUI_DEFAULT_MAX_NODES) }
        }

        assertTrue(error.message.orEmpty().contains("无障碍服务未连接"))
    }

    @Test
    fun missingSnapshotMessageNamesTheDisplay() {
        assertEquals("请先调用 gui_observe 读取当前界面", missingSnapshotMessage(GUI_MAIN_DISPLAY))
        assertTrue(missingSnapshotMessage(7).contains("屏幕 7"))
    }

    @Test
    fun selectsReadableApplicationOrSystemWindowWithHighestLayer() {
        val single = GuiWindowSelection.topmostReadable(listOf(candidate(GuiWindowKind.APPLICATION, layer = 1)))
        assertEquals(0, single)

        val systemAboveApp = GuiWindowSelection.topmostReadable(
            listOf(
                candidate(GuiWindowKind.APPLICATION, layer = 1),
                candidate(GuiWindowKind.SYSTEM, layer = 5),
            ),
        )
        assertEquals(1, systemAboveApp)

        val unreadableOnTop = GuiWindowSelection.topmostReadable(
            listOf(
                candidate(GuiWindowKind.APPLICATION, layer = 9, readable = false),
                candidate(GuiWindowKind.APPLICATION, layer = 2),
            ),
        )
        assertEquals(1, unreadableOnTop)

        val readableSystemAboveUnreadableApp = GuiWindowSelection.topmostReadable(
            listOf(
                candidate(GuiWindowKind.SYSTEM, layer = 6),
                candidate(GuiWindowKind.APPLICATION, layer = 1, readable = false),
            ),
        )
        assertEquals(0, readableSystemAboveUnreadableApp)
    }

    @Test
    fun layerOrderWinsOverWindowListOrder() {
        val appAboveSystem = GuiWindowSelection.topmostReadable(
            listOf(
                candidate(GuiWindowKind.APPLICATION, layer = 7),
                candidate(GuiWindowKind.SYSTEM, layer = 3),
            ),
        )
        assertEquals(0, appAboveSystem)

        val systemAboveApp = GuiWindowSelection.topmostReadable(
            listOf(
                candidate(GuiWindowKind.APPLICATION, layer = 3),
                candidate(GuiWindowKind.SYSTEM, layer = 7),
            ),
        )
        assertEquals(1, systemAboveApp)
    }

    @Test
    fun excludesImeAccessibilityAndMagnificationOverlays() {
        val candidates = listOf(
            candidate(GuiWindowKind.APPLICATION, layer = 2),
            candidate(GuiWindowKind.INPUT_METHOD, layer = 9),
            candidate(GuiWindowKind.ACCESSIBILITY_OVERLAY, layer = 10),
            candidate(GuiWindowKind.MAGNIFICATION_OVERLAY, layer = 11),
        )

        val index = GuiWindowSelection.topmostReadable(candidates)

        assertEquals(0, index)
    }

    @Test
    fun returnsNullWhenNoApplicationOrSystemWindowIsReadable() {
        assertNull(GuiWindowSelection.topmostReadable(emptyList()))
        assertNull(
            GuiWindowSelection.topmostReadable(
                listOf(
                    candidate(GuiWindowKind.INPUT_METHOD, layer = 4),
                    candidate(GuiWindowKind.ACCESSIBILITY_OVERLAY, layer = 3),
                    candidate(GuiWindowKind.MAGNIFICATION_OVERLAY, layer = 2),
                    candidate(GuiWindowKind.OTHER, layer = 1),
                ),
            ),
        )
        assertNull(
            GuiWindowSelection.topmostReadable(
                listOf(
                    candidate(GuiWindowKind.APPLICATION, layer = 3, readable = false),
                    candidate(GuiWindowKind.SYSTEM, layer = 2, readable = false),
                ),
            ),
        )
    }
}
