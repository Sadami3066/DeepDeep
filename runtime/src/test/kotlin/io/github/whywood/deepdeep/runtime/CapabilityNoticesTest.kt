package io.github.whywood.deepdeep.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityNoticesTest {
    @Test
    fun diffReportsAddedAndRemovedToolsAndSkills() {
        val previous = CapabilitySnapshot.of(listOf("bash", "mail_read"), listOf("mail"))
        val current = CapabilitySnapshot.of(listOf("bash", "mail_send"), listOf("mail", "pptx"))

        val delta = CapabilityNotices.diff(previous, current) ?: error("expected delta")

        assertEquals(listOf("mail_send"), delta.toolsAdded)
        assertEquals(listOf("mail_read"), delta.toolsRemoved)
        assertEquals(listOf("pptx"), delta.skillsAdded)
        assertTrue(delta.skillsRemoved.isEmpty())
    }

    @Test
    fun diffIsNullWhenNothingChanged() {
        val snapshot = CapabilitySnapshot.of(listOf("bash"), listOf("mail"))

        assertNull(CapabilityNotices.diff(snapshot, snapshot))
    }

    @Test
    fun snapshotNormalizesOrderAndDuplicates() {
        val snapshot = CapabilitySnapshot.of(
            listOf("web_search", "bash", "bash", " "),
            listOf("pptx", "mail", "pptx"),
        )

        assertEquals(listOf("bash", "web_search"), snapshot.tools)
        assertEquals(listOf("mail", "pptx"), snapshot.skills)
    }

    @Test
    fun messageCapsLongNameLists() {
        val delta = CapabilityDelta(
            toolsAdded = (1..9).map { "tool_$it" },
            skillsRemoved = listOf("mail"),
        )

        val text = CapabilityNotices.message(delta)

        assertTrue(text.contains("tool_1"))
        assertTrue(text.contains("(+3 more)"))
        assertTrue(text.contains("removed skills: mail"))
    }

    @Test
    fun snapshotAndDeltaRoundTripThroughJson() {
        val snapshot = CapabilitySnapshot.of(listOf("bash"), listOf("mail"))
        val decodedSnapshot = CapabilityNotices.decodeSnapshot(CapabilityNotices.encodeSnapshot(snapshot))
        assertEquals(snapshot, decodedSnapshot)

        val delta = CapabilityDelta(
            toolsAdded = listOf("mail_send"),
            toolsRemoved = listOf("bash"),
            skillsAdded = listOf("pptx"),
            skillsRemoved = listOf("mail"),
        )
        val decodedDelta = CapabilityNotices.decodeDelta(CapabilityNotices.encodeDelta(delta))
        assertEquals(delta, decodedDelta)
    }

    @Test
    fun malformedSnapshotDecodesToNull() {
        assertNull(CapabilityNotices.decodeSnapshot(null))
        assertNull(CapabilityNotices.decodeDelta(null))
    }
}
