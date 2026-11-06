package io.github.whywood.deepdeep.screen

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
        active: Boolean = false,
        focused: Boolean = false,
        area: Int = FULL_AREA,
        packageName: String? = "com.example",
        title: String? = null,
    ): GuiWindowCandidate = GuiWindowCandidate(
        kind = kind,
        layer = layer,
        readable = readable,
        active = active,
        focused = focused,
        area = area,
        packageName = packageName,
        title = title,
    )

    @Test
    fun accessibilityActionsImplementLegacyAndDisplayContracts() {
        val actions = GuiAccessibilityActions { null }

        assertTrue(actions is GuiActions)
        assertTrue(GuiActions::class.java.methods.none { it.name == "screenshot" || it.name == "global" })
        assertFalse(actions.connected)
    }

    @Test
    fun displayObserveWithoutServiceFailsWithUnavailableMessage() {
        val actions = GuiAccessibilityActions { null }

        val error = assertThrows(GuiUnavailableException::class.java) {
            runBlocking { actions.observe(9, GUI_DEFAULT_MAX_DEPTH, GUI_DEFAULT_MAX_NODES) }
        }

        assertTrue(error.message.orEmpty().contains("Accessibility service is not connected"))
    }

    @Test
    fun negativeScreenObserveIsRejected() {
        val actions = GuiAccessibilityActions { null }

        val error = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { actions.observe(-1, GUI_DEFAULT_MAX_DEPTH, GUI_DEFAULT_MAX_NODES) }
        }

        assertTrue(error.message.orEmpty().contains("must not be negative"))
    }

    @Test
    fun missingSnapshotMessageNamesTheDisplay() {
        assertTrue(missingSnapshotMessage(7).contains("is stale"))
        assertTrue(missingSnapshotMessage(7).contains("display 7"))
    }

    @Test
    fun selectsReadableApplicationWindowOverSystemWindow() {
        val single = GuiWindowSelection.topmostReadable(listOf(candidate(GuiWindowKind.APPLICATION, layer = 1)))
        assertEquals(0, single)

        val systemAboveApp = GuiWindowSelection.topmostReadable(
            listOf(
                candidate(GuiWindowKind.APPLICATION, layer = 1),
                candidate(GuiWindowKind.SYSTEM, layer = 5, focused = true),
            ),
        )
        assertEquals(0, systemAboveApp)

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
    fun prefersApplicationWindowOverGuideBubbleOrSystemPopup() {
        val bubbleAboveApp = GuiWindowSelection.topmostReadable(
            listOf(
                candidate(GuiWindowKind.APPLICATION, layer = 1, area = FULL_AREA),
                candidate(GuiWindowKind.SYSTEM, layer = 20, area = 320 * 200, focused = true),
            ),
        )
        assertEquals(0, bubbleAboveApp)

        val systemDialogAboveApp = GuiWindowSelection.topmostReadable(
            listOf(
                candidate(GuiWindowKind.APPLICATION, layer = 1, area = FULL_AREA),
                candidate(GuiWindowKind.SYSTEM, layer = 20, area = 900 * 600, focused = true),
            ),
        )
        assertEquals(0, systemDialogAboveApp)
    }

    @Test
    fun layerOrderBreaksTiesWithinTheSameWindowKind() {
        val appAboveSystem = GuiWindowSelection.topmostReadable(
            listOf(
                candidate(GuiWindowKind.APPLICATION, layer = 7),
                candidate(GuiWindowKind.APPLICATION, layer = 3),
            ),
        )
        assertEquals(0, appAboveSystem)

        val systemAboveApp = GuiWindowSelection.topmostReadable(
            listOf(
                candidate(GuiWindowKind.SYSTEM, layer = 3),
                candidate(GuiWindowKind.SYSTEM, layer = 7),
            ),
        )
        assertEquals(1, systemAboveApp)
    }

    @Test
    fun packageFilterSelectsOnlyThatPackagesWindow() {
        val candidates = listOf(
            candidate(GuiWindowKind.APPLICATION, area = FULL_AREA, packageName = "com.example"),
            candidate(GuiWindowKind.APPLICATION, area = 900 * 600, focused = true, packageName = "com.other"),
        )
        assertEquals(1, GuiWindowSelection.select(candidates, expectedPackage = "com.other"))
        assertEquals(0, GuiWindowSelection.select(candidates, expectedPackage = "com.example"))
        assertNull(GuiWindowSelection.select(candidates, expectedPackage = "com.missing"))
    }

    @Test
    fun packageFilterCanSelectSystemDialogOfThatPackage() {
        val candidates = listOf(
            candidate(GuiWindowKind.APPLICATION, area = FULL_AREA),
            candidate(
                GuiWindowKind.SYSTEM,
                area = 900 * 600,
                focused = true,
                packageName = "com.android.permissioncontroller",
            ),
        )
        assertEquals(
            1,
            GuiWindowSelection.select(candidates, expectedPackage = "com.android.permissioncontroller"),
        )
    }

    @Test
    fun emptyPackageNameFallbackKeepsSmallWindowOfTheExpectedPackage() {
        val candidates = listOf(
            candidate(GuiWindowKind.APPLICATION, area = FULL_AREA, packageName = "com.example"),
            candidate(GuiWindowKind.SYSTEM, area = 320 * 200, packageName = "com.bubble"),
        )
        assertEquals(1, GuiWindowSelection.select(candidates, expectedPackage = "com.bubble"))
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
    fun prefersActiveForegroundWindowOverHigherLayerBottomStrip() {
        val index = GuiWindowSelection.topmostReadable(
            listOf(
                candidate(GuiWindowKind.APPLICATION, layer = 12, area = 1080 * 68),
                candidate(GuiWindowKind.APPLICATION, layer = 3, area = FULL_AREA, active = true, focused = true),
            ),
        )
        assertEquals(1, index)
    }

    @Test
    fun prefersFocusedDialogOverLargerBackgroundWindow() {
        val index = GuiWindowSelection.topmostReadable(
            listOf(
                candidate(GuiWindowKind.APPLICATION, layer = 2, area = FULL_AREA),
                candidate(GuiWindowKind.APPLICATION, layer = 6, area = 900 * 600, active = true, focused = true),
            ),
        )
        assertEquals(1, index)
    }

    @Test
    fun prefersActiveWindowOverFocusedWindow() {
        val index = GuiWindowSelection.topmostReadable(
            listOf(
                candidate(GuiWindowKind.SYSTEM, layer = 4, area = FULL_AREA, focused = true),
                candidate(GuiWindowKind.APPLICATION, layer = 9, area = 1000 * 1000, active = true),
            ),
        )
        assertEquals(1, index)
    }

    @Test
    fun ignoresTinyWindowWhenLargerReadableWindowExists() {
        val index = GuiWindowSelection.topmostReadable(
            listOf(
                candidate(GuiWindowKind.SYSTEM, layer = 15, area = 1080 * 68),
                candidate(GuiWindowKind.APPLICATION, layer = 1, area = FULL_AREA),
            ),
        )
        assertEquals(1, index)
    }

    @Test
    fun keepsTinyWindowWhenItIsTheOnlyReadableWindow() {
        val index = GuiWindowSelection.topmostReadable(
            listOf(candidate(GuiWindowKind.APPLICATION, area = 1080 * 68)),
        )
        assertEquals(0, index)
    }

    @Test
    fun selectsSingleReadableWindowOnVirtualDisplay() {
        val index = GuiWindowSelection.topmostReadable(
            listOf(
                candidate(GuiWindowKind.INPUT_METHOD, layer = 9, area = FULL_AREA),
                candidate(GuiWindowKind.APPLICATION, layer = 1, area = 700 * 1200),
            ),
        )
        assertEquals(1, index)
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

    @Test
    fun missingWindowMessagesPointAtScreenLaunch() {
        val noWindows = missingWindowMessage(emptyList(), expectedPackage = null)
        assertTrue(noWindows.contains("no windows"))
        assertTrue(noWindows.contains("screen_launch"))

        val unreadable = listOf(
            GuiWindowInfo(
                packageName = "com.example",
                title = "Bubble",
                kind = GuiWindowKind.SYSTEM,
                readable = false,
                focused = true,
            ),
        )
        val listed = missingWindowMessage(unreadable, expectedPackage = null)
        assertTrue(listed.contains("no readable application window"))
        assertTrue(listed.contains("system com.example"))
        assertTrue(listed.contains("focused"))
        assertTrue(listed.contains("screen_launch"))

        val missingPackage = missingWindowMessage(
            listOf(GuiWindowInfo(packageName = "com.example", title = null, kind = GuiWindowKind.APPLICATION, readable = true)),
            expectedPackage = "com.other",
        )
        assertTrue(missingPackage.contains("com.other"))
        assertTrue(missingPackage.contains("com.example"))
    }

    @Test
    fun windowKindNamesAreStable() {
        assertEquals("application", windowKindName(GuiWindowKind.APPLICATION))
        assertEquals("system", windowKindName(GuiWindowKind.SYSTEM))
        assertEquals("input_method", windowKindName(GuiWindowKind.INPUT_METHOD))
        assertEquals("accessibility_overlay", windowKindName(GuiWindowKind.ACCESSIBILITY_OVERLAY))
        assertEquals("magnification_overlay", windowKindName(GuiWindowKind.MAGNIFICATION_OVERLAY))
        assertEquals("other", windowKindName(GuiWindowKind.OTHER))
    }

    private companion object {
        const val FULL_AREA = 1080 * 2400
    }
}
