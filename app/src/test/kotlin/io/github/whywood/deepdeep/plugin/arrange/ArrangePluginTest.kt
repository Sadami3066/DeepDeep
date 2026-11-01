package io.github.whywood.deepdeep.plugin.arrange

import android.content.ContextWrapper
import android.content.res.Resources
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.plugin.PluginAvailability
import io.github.whywood.deepdeep.plugin.PluginStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrangePluginTest {
    private val context = FakeContext()
    private val calendar = FakeCalendarGateway()

    @Test
    fun identityMatchesPluginContract() {
        val plugin = plugin()

        assertEquals("arrange", plugin.id)
        assertEquals("3.0.0", plugin.version)
        assertEquals(DdIcons.Clock, plugin.icon)
        assertEquals(R.string.plugin_arrange_name, plugin.nameRes)
        assertEquals(R.string.plugin_arrange_summary, plugin.summaryRes)
        assertEquals(R.string.plugin_arrange_size, plugin.sizeLabelRes)
        assertEquals(listOf("arrange_calendar"), plugin.tools().map { it.name })
    }

    @Test
    fun availabilityIsAlwaysAvailable() = runTest {
        assertEquals(PluginAvailability.Available, plugin().availability())
    }

    @Test
    fun statusReflectsCalendarPermission() = runTest {
        assertEquals("res:${R.string.plugin_arrange_status_ready}", plugin().status().label)

        calendar.readAllowed = false
        assertEquals(
            "res:${R.string.plugin_arrange_status_calendar_permission}",
            plugin().status().label,
        )
    }

    @Test
    fun actionsOnlyOfferCalendarPermissionWhenMissing() = runTest {
        val plugin = plugin()
        assertTrue(plugin.actions(PluginStatus.Ready("x")).isEmpty())

        calendar.readAllowed = false
        val actions = plugin.actions(PluginStatus.Ready("x"))
        assertEquals(listOf("permission"), actions.map { it.id })
        assertEquals(listOf(R.string.plugin_arrange_action_permission), actions.map { it.labelRes })
    }

    @Test
    fun noCustomDetailRows() = runTest {
        assertTrue(plugin().detailRows(PluginStatus.Ready("x")).isEmpty())
    }

    @Test
    fun promptBodyStaysEmptyBecauseTheToolOwnsItsGuidance() = runTest {
        assertNull(plugin().promptBody(PluginStatus.Ready("x")))
        assertTrue(plugin().tools().single().promptGuidelines.orEmpty().contains(ARRANGE_CALENDAR_GUIDELINE))
    }

    @Test
    fun skillHasFrontmatterAndCalendarOnly() {
        val skills = plugin().skills()

        assertEquals(1, skills.size)
        val skill = skills.single()
        assertEquals("deepdeep-arrange", skill.id)
        assertTrue(skill.markdown.startsWith("---\n"))
        val frontmatter = skill.markdown.substringAfter("---\n").substringBefore("\n---")
        assertTrue(frontmatter.contains("name: deepdeep-arrange"))
        assertTrue(frontmatter.contains("description: "))
        listOf("arrange_calendar", "list", "update", "delete", "reminders", "日历权限")
            .forEach { expected -> assertTrue(expected, skill.markdown.contains(expected)) }
        assertFalse(skill.markdown.contains("arrange_alarm"))
        assertFalse(skill.markdown.contains("todo"))
        listOf("READ_CALENDAR", "WRITE_CALENDAR").forEach { word ->
            assertFalse(word, skill.markdown.contains(word))
        }
    }

    private fun plugin(): ArrangePlugin = ArrangePlugin(context, calendar)

    private class FakeCalendarGateway : ArrangeCalendarGateway {
        var readAllowed = true
        var writeAllowed = true

        override fun hasReadPermission(): Boolean = readAllowed

        override fun hasWritePermission(): Boolean = writeAllowed

        override fun calendars(): List<ArrangeCalendarInfo> = emptyList()

        override fun list(startMillis: Long, endMillis: Long, query: String?, limit: Int): List<ArrangeCalendarEvent> =
            emptyList()

        override fun get(id: Long): ArrangeCalendarEvent? = null

        override fun create(draft: ArrangeEventDraft): ArrangeCalendarEvent =
            ArrangeCalendarEvent(1, 1, draft.title, draft.startMillis, draft.startMillis + 60_000, false)

        override fun update(id: Long, patch: ArrangeEventPatch): ArrangeCalendarEvent? = null

        override fun delete(id: Long): Boolean = false
    }

    private class FakeContext : ContextWrapper(null) {
        private val fakeResources = FakeResources()

        override fun getResources(): Resources = fakeResources
    }

    @Suppress("DEPRECATION")
    private class FakeResources : Resources(null, null, null) {
        override fun getString(id: Int): String = "res:$id"
    }
}
