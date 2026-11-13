package io.github.whywood.deepdeep.design

import org.junit.Assert.assertEquals
import org.junit.Test

class DdSheetDragTest {
    @Test
    fun dragDownAccumulates() {
        assertEquals(60f, sheetDragOffset(0f, 60f, 1000f), 0.001f)
        assertEquals(120f, sheetDragOffset(60f, 60f, 1000f), 0.001f)
    }

    @Test
    fun upwardDeltaAtRestIsIgnored() {
        assertEquals(0f, sheetDragOffset(0f, -120f, 1000f), 0.001f)
    }

    @Test
    fun upwardDeltaFromPartialRestoresToRest() {
        assertEquals(0f, sheetDragOffset(40f, -80f, 1000f), 0.001f)
        assertEquals(20f, sheetDragOffset(70f, -50f, 1000f), 0.001f)
    }

    @Test
    fun dragClampsToSheetHeight() {
        assertEquals(1000f, sheetDragOffset(900f, 400f, 1000f), 0.001f)
    }

    @Test
    fun zeroHeightNeverMoves() {
        assertEquals(0f, sheetDragOffset(0f, 120f, 0f), 0.001f)
    }

    @Test
    fun dismissesByDistance() {
        assertEquals(1000f, sheetDismissTarget(260f, 1000f, 0f, 2000f), 0.001f)
    }

    @Test
    fun staysWhenBelowThresholds() {
        assertEquals(0f, sheetDismissTarget(200f, 1000f, 0f, 2000f), 0.001f)
        assertEquals(0f, sheetDismissTarget(250f, 1000f, 0f, 2000f), 0.001f)
    }

    @Test
    fun dismissesByDownwardVelocity() {
        assertEquals(1000f, sheetDismissTarget(10f, 1000f, 2500f, 2000f), 0.001f)
    }

    @Test
    fun upwardVelocityDoesNotDismiss() {
        assertEquals(0f, sheetDismissTarget(10f, 1000f, -2500f, 2000f), 0.001f)
    }

    @Test
    fun zeroHeightNeverDismisses() {
        assertEquals(0f, sheetDismissTarget(100f, 0f, 5000f, 2000f), 0.001f)
    }
}
