package io.github.whywood.deepdeep.plugin.arrange

import android.content.ContextWrapper
import android.content.res.Resources
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.plugin.androidArrangeManifest
import io.github.whywood.deepdeep.plugin.api.IconRef
import io.github.whywood.deepdeep.plugin.api.LocalizedText
import io.github.whywood.deepdeep.plugin.api.PluginAvailability
import io.github.whywood.deepdeep.plugin.api.PluginSessionInfo
import io.github.whywood.deepdeep.plugin.api.PluginStatus
import io.github.whywood.deepdeep.plugin.internal.testPluginHost
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrangePluginTest {
    private val context = FakeContext()
    private val calendar = FakeCalendarGateway()
    private val host = testPluginHost(androidArrangeManifest())

    @Test
    fun manifestMatchesPluginContract() {
        val manifest = androidArrangeManifest()

        assertEquals("arrange", manifest.id)
        assertEquals("3.0.0", manifest.version)
        assertEquals(IconRef.Resource(DdIcons.Clock.res), manifest.icon)
        assertEquals(LocalizedText.Resource(R.string.plugin_arrange_name), manifest.name)
        assertEquals(LocalizedText.Resource(R.string.plugin_arrange_summary), manifest.summary)
        assertEquals(LocalizedText.Resource(R.string.plugin_arrange_size), manifest.sizeLabel)
        assertEquals(listOf("arrange_calendar"), manifest.provides.tools)
        assertEquals(listOf("deepdeep-arrange"), manifest.provides.skills)
        assertTrue(manifest.provides.settings.isEmpty())
    }

    @Test
    fun contributionsExposeCalendarTool() {
        val tools = plugin().contributionsFor(host, session()).tools

        assertEquals(listOf("arrange_calendar"), tools.map { it.name })
        assertTrue(tools.single().promptGuidelines.contains(ARRANGE_CALENDAR_GUIDELINE))
    }

    @Test
    fun availabilityIsAlwaysAvailable() = runTest {
        assertEquals(PluginAvailability.Available, plugin().availability(host))
    }

    @Test
    fun statusReflectsCalendarPermission() = runTest {
        assertEquals(expectedLabel(R.string.plugin_arrange_status_ready), plugin().status(host).label)

        calendar.readAllowed = false
        assertEquals(
            expectedLabel(R.string.plugin_arrange_status_calendar_permission),
            plugin().status(host).label,
        )
    }

    @Test
    fun actionsOnlyOfferCalendarPermissionWhenMissing() {
        val plugin = plugin()
        assertTrue(plugin.actions(host, PluginStatus.Ready("x")).isEmpty())

        calendar.readAllowed = false
        val actions = plugin.actions(host, PluginStatus.Ready("x"))
        assertEquals(listOf("permission"), actions.map { it.id })
        assertEquals(
            listOf(LocalizedText.Resource(R.string.plugin_arrange_action_permission)),
            actions.map { it.label },
        )
    }

    @Test
    fun noCustomDetailRows() {
        assertTrue(plugin().detailRows(host, PluginStatus.Ready("x")).isEmpty())
    }

    @Test
    fun promptSectionsStayEmptyBecauseTheToolOwnsItsGuidance() {
        assertTrue(plugin().promptSections(host).isEmpty())
    }

    @Test
    fun skillHasFrontmatterAndCalendarOnly() {
        val skills = plugin().skills(host)

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

    private fun expectedLabel(id: Int): String = "text:$id"

    private fun session(): PluginSessionInfo = PluginSessionInfo(sessionId = "session", cwd = "/tmp")

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
