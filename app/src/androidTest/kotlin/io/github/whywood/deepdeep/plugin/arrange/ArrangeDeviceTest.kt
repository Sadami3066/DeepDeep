package io.github.whywood.deepdeep.plugin.arrange

import android.Manifest
import android.app.Instrumentation
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArrangeDeviceTest {
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val createdEventIds = mutableListOf<Long>()

    @Before
    fun grantCalendarPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val automation = instrumentation.uiAutomation
            listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR).forEach { permission ->
                runCatching { automation.grantRuntimePermission(context.packageName, permission) }
            }
        }
    }

    @After
    fun cleanUp() {
        val calendar = AndroidArrangeCalendarGateway(context)
        createdEventIds.forEach { id -> runCatching { calendar.delete(id) } }
    }

    @Test
    fun calendarProviderSupportsCrud() {
        val calendar = AndroidArrangeCalendarGateway(context)
        assumeTrue(calendar.hasReadPermission() && calendar.hasWritePermission())
        val calendars = calendar.calendars()
        assumeTrue(calendars.any { it.writable })
        val calendarId = calendars.first { it.writable }.id
        val start = System.currentTimeMillis() + 2L * 60L * 60L * 1_000L
        val title = "DeepDeep Arrange Test ${System.currentTimeMillis()}"

        val created = calendar.create(
            ArrangeEventDraft(
                title = title,
                startMillis = start,
                endMillis = start + 30L * 60L * 1_000L,
                allDay = false,
                location = "test location",
                description = "test description",
                calendarId = calendarId,
                reminderMinutes = listOf(10),
            ),
        )
        createdEventIds += created.id
        assertEquals(title, created.title)
        assertEquals(start, created.startMillis)
        assertEquals(calendarId, created.calendarId)

        val read = calendar.get(created.id)
        assertNotNull(read)
        assertEquals(title, read!!.title)

        val listed = calendar.list(start - 60_000L, start + 120_000L, title, 20)
        assertTrue(listed.any { it.id == created.id })

        val updated = calendar.update(
            created.id,
            ArrangeEventPatch(
                title = "$title updated",
                location = "new location",
                reminderMinutes = emptyList(),
            ),
        )
        assertNotNull(updated)
        assertEquals("$title updated", updated!!.title)
        assertEquals("new location", updated.location)

        assertTrue(calendar.delete(created.id))
        createdEventIds.remove(created.id)
        assertNull(calendar.get(created.id))
    }

    @Test
    fun allDayEventKeepsRequestedLocalDate() {
        val calendar = AndroidArrangeCalendarGateway(context)
        assumeTrue(calendar.hasReadPermission() && calendar.hasWritePermission())
        val calendars = calendar.calendars()
        assumeTrue(calendars.any { it.writable })
        val calendarId = calendars.first { it.writable }.id
        val zone = java.time.ZoneId.systemDefault()
        val requestedDate = java.time.LocalDate.now(zone).plusDays(3)
        val requested = requestedDate.toString()
        val start = parseCalendarMillis(requested, zone)
        val (rangeStart, rangeEnd) = resolveEventRange(start, null, allDay = true, zone = zone)
        val title = "DeepDeep AllDay Test ${System.currentTimeMillis()}"

        val created = calendar.create(
            ArrangeEventDraft(
                title = title,
                startMillis = rangeStart,
                endMillis = rangeEnd,
                allDay = true,
                calendarId = calendarId,
            ),
        )
        createdEventIds += created.id

        val read = calendar.get(created.id)
        assertNotNull(read)
        assertEquals(requested, formatCalendarTime(read!!.startMillis, allDay = true, zone = zone))
        assertTrue(read.startMillis % 86_400_000L == 0L)
    }
}
