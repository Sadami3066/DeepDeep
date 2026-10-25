package io.github.whywood.deepdeep.plugin.arrange

import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ArrangeAlarmsTest {
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private lateinit var directory: File
    private lateinit var store: ArrangeAlarmStore

    @Before
    fun setUp() {
        directory = File(System.getProperty("java.io.tmpdir"), "arrange-alarms-${System.nanoTime()}")
        directory.mkdirs()
        store = ArrangeAlarmStore(directory)
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun oneShotAlarmUsesTodayWhenTimeIsAhead() {
        val now = epoch(2026, 10, 25, 10, 0)
        val trigger = nextAlarmTrigger(18, 30, emptyList(), now, zone)
        assertEquals(LocalDateTime.of(2026, 10, 25, 18, 30), localText(trigger))
    }

    @Test
    fun oneShotAlarmMovesToTomorrowWhenTimeHasPassed() {
        val now = epoch(2026, 10, 25, 10, 0)
        val trigger = nextAlarmTrigger(7, 0, emptyList(), now, zone)
        assertEquals(LocalDateTime.of(2026, 10, 26, 7, 0), localText(trigger))
    }

    @Test
    fun nextTriggerIsStrictlyLaterThanNow() {
        val now = epoch(2026, 10, 25, 7, 30)
        val trigger = nextAlarmTrigger(7, 30, emptyList(), now, zone)
        assertTrue(trigger > now)
        assertEquals(LocalDateTime.of(2026, 10, 26, 7, 30), localText(trigger))
    }

    @Test
    fun repeatingAlarmCanFireLaterToday() {
        val now = epoch(2026, 10, 25, 10, 0)
        val trigger = nextAlarmTrigger(12, 0, listOf(7), now, zone)
        assertEquals(LocalDateTime.of(2026, 10, 25, 12, 0), localText(trigger))
    }

    @Test
    fun repeatingAlarmUsesNextMatchingWeekday() {
        val now = epoch(2026, 10, 25, 10, 0)
        val trigger = nextAlarmTrigger(9, 0, listOf(1, 3), now, zone)
        assertEquals(LocalDateTime.of(2026, 10, 26, 9, 0), localText(trigger))
    }

    @Test
    fun repeatingAlarmWrapsAcrossWeek() {
        val now = epoch(2026, 10, 25, 10, 0)
        val trigger = nextAlarmTrigger(9, 0, listOf(7), now, zone)
        assertEquals(LocalDateTime.of(2026, 11, 1, 9, 0), localText(trigger))
    }

    @Test
    fun everyDayRepeatUsesTomorrowAfterTimePassed() {
        val now = epoch(2026, 10, 25, 10, 0)
        val trigger = nextAlarmTrigger(9, 0, (1..7).toList(), now, zone)
        assertEquals(LocalDateTime.of(2026, 10, 26, 9, 0), localText(trigger))
    }

    @Test
    fun triggerUsesProvidedZone() {
        val utc = ZoneId.of("UTC")
        val nowUtc = LocalDateTime.of(2026, 10, 24, 23, 30).atZone(utc).toInstant().toEpochMilli()
        val trigger = nextAlarmTrigger(8, 0, emptyList(), nowUtc, zone)
        assertEquals(
            LocalDateTime.of(2026, 10, 25, 8, 0).atZone(zone).toInstant().toEpochMilli(),
            trigger,
        )
    }

    @Test
    fun timerTriggerAddsDuration() {
        val now = 1_700_000_000_000L
        assertEquals(now + 90_000L, nextTimerTrigger(now, 90))
        assertEquals(now + 1_000L, nextTimerTrigger(now, 1))
    }

    @Test
    fun storePersistsAlarmsBetweenInstances() {
        val alarm = sampleAlarm(id = "alarm-1", days = listOf(1, 3, 5), triggerAt = 12_345L)
        store.add(alarm)
        val reopened = ArrangeAlarmStore(directory)
        assertEquals(listOf(alarm), reopened.list())
        assertTrue(File(directory, ARRANGE_ALARMS_FILE).isFile)
        assertFalse(File(directory, "$ARRANGE_ALARMS_FILE.tmp").exists())
    }

    @Test
    fun storeCorruptedFileReadsAsEmptyAndCanBeRewritten() {
        File(directory, ARRANGE_ALARMS_FILE).writeText("{broken")
        assertTrue(store.list().isEmpty())
        File(directory, ARRANGE_ALARMS_FILE).writeText("""{"alarms":[{"id":"x"}]}""")
        assertTrue(store.list().isEmpty())
        store.add(sampleAlarm(id = "recovered"))
        assertEquals(listOf("recovered"), store.list().map { it.id })
    }

    @Test
    fun storeUpdatesAndRemovesAlarms() {
        val alarm = sampleAlarm(id = "a")
        store.add(alarm)
        val updated = store.update(alarm.copy(label = "新的", enabled = false))
        assertEquals("新的", updated?.label)
        assertEquals("新的", store.find("a")?.label)
        assertNull(store.update(alarm.copy(id = "missing")))
        assertTrue(store.remove("a"))
        assertFalse(store.remove("a"))
        assertNull(store.find("a"))
    }

    @Test
    fun oneShotAlarmIsDisabledAfterFiring() {
        val triggerAt = epoch(2026, 10, 25, 7, 0)
        store.add(sampleAlarm(id = "one-shot", hour = 7, minute = 0, triggerAt = triggerAt))
        val fired = store.fire("one-shot", triggerAt + 1_000)
        assertNotNull(fired)
        assertFalse(fired!!.enabled)
        assertFalse(store.find("one-shot")!!.enabled)
    }

    @Test
    fun repeatingAlarmIsRescheduledAfterFiring() {
        val triggerAt = epoch(2026, 10, 25, 12, 0)
        store.add(
            sampleAlarm(
                id = "repeat",
                hour = 12,
                minute = 0,
                days = listOf(7),
                triggerAt = triggerAt,
            ),
        )
        val fired = store.fire("repeat", triggerAt + 1_000)
        assertNotNull(fired)
        assertTrue(fired!!.enabled)
        assertEquals(LocalDateTime.of(2026, 11, 1, 12, 0), localText(fired.triggerAt))
        assertEquals(fired, store.find("repeat"))
    }

    @Test
    fun timerIsDisabledAfterFiring() {
        val now = epoch(2026, 10, 25, 10, 0)
        store.add(
            sampleAlarm(
                id = "timer",
                timer = true,
                triggerAt = nextTimerTrigger(now, 60),
            ),
        )
        val fired = store.fire("timer", now + 60_000)
        assertNotNull(fired)
        assertFalse(fired!!.enabled)
    }

    @Test
    fun nextSystemAlarmPrefersEarliestEnabledEntry() {
        store.add(sampleAlarm(id = "late", triggerAt = 5_000))
        store.add(sampleAlarm(id = "disabled", triggerAt = 1_000, enabled = false))
        store.add(sampleAlarm(id = "early", triggerAt = 3_000))
        assertEquals(ArrangeSystemAlarm(3_000, "闹钟"), store.nextOwnAlarm())
        store.remove("early")
        assertEquals(ArrangeSystemAlarm(5_000, "闹钟"), store.nextOwnAlarm())
        store.remove("late")
        assertNull(store.nextOwnAlarm())
    }

    @Test
    fun missingAlarmFireReturnsNull() {
        assertNull(store.fire("missing", 1_000))
    }

    private fun sampleAlarm(
        id: String,
        hour: Int = 7,
        minute: Int = 0,
        days: List<Int> = emptyList(),
        enabled: Boolean = true,
        timer: Boolean = false,
        triggerAt: Long = epoch(2026, 10, 26, 7, 0),
    ): ArrangeAlarm = ArrangeAlarm(
        id = id,
        label = "闹钟",
        hour = hour,
        minute = minute,
        days = days,
        enabled = enabled,
        timer = timer,
        triggerAt = triggerAt,
        createdAt = 0L,
    )

    private fun epoch(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun localText(millis: Long): LocalDateTime =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()
}
