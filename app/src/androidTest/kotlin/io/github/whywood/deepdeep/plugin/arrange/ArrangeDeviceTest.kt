package io.github.whywood.deepdeep.plugin.arrange

import android.Manifest
import android.app.Instrumentation
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    private val createdAlarmIds = mutableListOf<String>()

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
        val alarms = AndroidArrangeAlarmEngine(context)
        createdAlarmIds.forEach { id -> runCatching { alarms.deleteAlarm(id) } }
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
    fun alarmEngineSupportsCrudWithoutRinging() {
        val engine = AndroidArrangeAlarmEngine(context)
        assumeTrue(engine.canScheduleExactAlarms())
        val created = engine.createAlarm(
            ArrangeAlarmDraft(
                hour = 7,
                minute = 30,
                label = "DeepDeep Arrange Alarm",
                days = emptyList(),
                enabled = false,
            ),
        )
        createdAlarmIds += created.id
        assertFalse(created.enabled)
        assertTrue(engine.list().any { it.id == created.id })

        val updated = engine.updateAlarm(
            created.id,
            ArrangeAlarmDraft(
                hour = 8,
                minute = 15,
                label = "DeepDeep Arrange Alarm Updated",
                days = listOf(1, 3),
                enabled = false,
            ),
        )
        assertNotNull(updated)
        assertEquals(8, updated!!.hour)
        assertEquals(15, updated.minute)
        assertEquals(listOf(1, 3), updated.days)

        assertTrue(engine.deleteAlarm(created.id))
        createdAlarmIds.remove(created.id)
        assertTrue(engine.list().none { it.id == created.id })
    }

    @Test
    fun alarmEngineSchedulesAndCancelsExactAlarm() {
        val engine = AndroidArrangeAlarmEngine(context)
        assumeTrue(engine.canScheduleExactAlarms())
        val created = engine.createAlarm(
            ArrangeAlarmDraft(
                hour = 23,
                minute = 59,
                label = "DeepDeep Arrange Scheduled",
                days = emptyList(),
                enabled = true,
            ),
        )
        createdAlarmIds += created.id
        val next = engine.nextSystemAlarm()
        assertNotNull(next)
        assertTrue(next!!.triggerAt > System.currentTimeMillis())
        assertTrue(engine.deleteAlarm(created.id))
        createdAlarmIds.remove(created.id)
    }
    @Test
    fun timerRingsThroughForegroundService() {
        val engine = AndroidArrangeAlarmEngine(context)
        assumeTrue(engine.canScheduleExactAlarms())
        val created = engine.createTimer(2, "DeepDeep Arrange Ringing Test")
        createdAlarmIds += created.id
        val manager = context.getSystemService(NotificationManager::class.java)
        val deadline = System.currentTimeMillis() + 12_000L
        var ringing = false
        while (System.currentTimeMillis() < deadline && !ringing) {
            Thread.sleep(500L)
            ringing = manager.activeNotifications.any { it.notification.category == "alarm" }
        }
        assertTrue(ringing)
        Thread.sleep(8_000)
        context.stopService(
            Intent(context, ArrangeAlarmService::class.java)
                .setAction(ArrangeAlarmService.ACTION_STOP),
        )
        assertTrue(engine.deleteAlarm(created.id))
        createdAlarmIds.remove(created.id)
    }

}
