package io.github.whywood.deepdeep.ui.workshop

import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.runtime.WorkshopInvalidReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkshopCreateValidationTest {
    @Test
    fun rejectsEmptyOrBlankName() {
        assertEquals(WorkshopNameError.EMPTY, workshopNameError(""))
        assertEquals(WorkshopNameError.EMPTY, workshopNameError("   "))
    }

    @Test
    fun rejectsTooLongName() {
        assertEquals(WorkshopNameError.TOO_LONG, workshopNameError("x".repeat(41)))
        assertEquals(WorkshopNameError.TOO_LONG, workshopNameError("  ${"x".repeat(41)}  "))
    }

    @Test
    fun acceptsValidNames() {
        assertNull(workshopNameError("周报看板"))
        assertNull(workshopNameError("x".repeat(40)))
        assertNull(workshopNameError("  board  "))
    }

    @Test
    fun mapsEveryInvalidReasonToLabel() {
        val expected = mapOf(
            WorkshopInvalidReason.MANIFEST_MISSING to R.string.workshop_reason_manifest_missing,
            WorkshopInvalidReason.MANIFEST_UNREADABLE to R.string.workshop_reason_manifest_unreadable,
            WorkshopInvalidReason.MANIFEST_INVALID to R.string.workshop_reason_manifest_invalid,
            WorkshopInvalidReason.ENTRY_MISSING to R.string.workshop_reason_entry_missing,
            WorkshopInvalidReason.ID_MISMATCH to R.string.workshop_reason_id_mismatch,
        )
        assertEquals(WorkshopInvalidReason.entries.toSet(), expected.keys)
        expected.forEach { (reason, label) ->
            assertEquals(label, invalidReasonLabelRes(reason))
        }
    }
}
