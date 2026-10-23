package io.github.whywood.deepdeep.runtime

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleModelsTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = ZonedDateTime.of(2026, 10, 23, 12, 0, 0, 0, zone)

    @Test
    fun onceFiresOnlyInTheFuture() {
        val future = ScheduleTrigger.Once(LocalDate.of(2026, 10, 24), LocalTime.of(9, 0)).nextRun(now)
        assertEquals(ZonedDateTime.of(2026, 10, 24, 9, 0, 0, 0, zone), future)

        val past = ScheduleTrigger.Once(LocalDate.of(2026, 10, 22), LocalTime.of(9, 0)).nextRun(now)
        assertNull(past)
    }

    @Test
    fun onceIsStrictlyAfterNow() {
        val trigger = ScheduleTrigger.Once(now.toLocalDate(), now.toLocalTime())
        assertNull(trigger.nextRun(now))
    }

    @Test
    fun dailyMovesToNextDayWhenTimePassed() {
        val passed = ScheduleTrigger.Daily(LocalTime.of(9, 0)).nextRun(now)
        assertEquals(ZonedDateTime.of(2026, 10, 24, 9, 0, 0, 0, zone), passed)

        val upcoming = ScheduleTrigger.Daily(LocalTime.of(13, 30)).nextRun(now)
        assertEquals(ZonedDateTime.of(2026, 10, 23, 13, 30, 0, 0, zone), upcoming)

        val exactlyNow = ScheduleTrigger.Daily(now.toLocalTime()).nextRun(now)
        assertEquals(ZonedDateTime.of(2026, 10, 24, 12, 0, 0, 0, zone), exactlyNow)
    }

    @Test
    fun weeklyPicksNextMatchingWeekday() {
        assertEquals(DayOfWeek.FRIDAY, now.dayOfWeek)

        val laterToday = ScheduleTrigger.Weekly(setOf(DayOfWeek.FRIDAY), LocalTime.of(18, 0)).nextRun(now)
        assertEquals(ZonedDateTime.of(2026, 10, 23, 18, 0, 0, 0, zone), laterToday)

        val nextWeek = ScheduleTrigger.Weekly(setOf(DayOfWeek.FRIDAY), LocalTime.of(9, 0)).nextRun(now)
        assertEquals(ZonedDateTime.of(2026, 10, 30, 9, 0, 0, 0, zone), nextWeek)

        val monday = ScheduleTrigger.Weekly(setOf(DayOfWeek.MONDAY), LocalTime.of(9, 0)).nextRun(now)
        assertEquals(ZonedDateTime.of(2026, 10, 26, 9, 0, 0, 0, zone), monday)

        val multiple = ScheduleTrigger.Weekly(
            setOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY),
            LocalTime.of(8, 0),
        ).nextRun(now)
        assertEquals(ZonedDateTime.of(2026, 10, 25, 8, 0, 0, 0, zone), multiple)
    }

    @Test
    fun emptyWeeklyDaysNeverFires() {
        assertNull(ScheduleTrigger.Weekly(emptySet(), LocalTime.of(9, 0)).nextRun(now))
    }

    @Test
    fun triggerJsonRoundTrip() {
        val triggers = listOf(
            ScheduleTrigger.Once(LocalDate.of(2026, 12, 1), LocalTime.of(9, 30)),
            ScheduleTrigger.Daily(LocalTime.of(7, 5)),
            ScheduleTrigger.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), LocalTime.of(18, 0)),
        )
        triggers.forEach { trigger ->
            assertEquals(trigger, scheduleTriggerFromJsonObject(trigger.toJsonObject()))
        }
    }

    @Test
    fun weeklyDaysAreStoredAsIsoNumbers() {
        val json = ScheduleTrigger.Weekly(
            setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
            LocalTime.of(9, 0),
        ).toJsonObject()
        val days = json["days"] as kotlinx.serialization.json.JsonArray
        assertEquals(listOf("1", "5"), days.map { (it as JsonPrimitive).content })
    }

    @Test
    fun weeklyDaysDecodeLegacyNames() {
        val json = buildJsonObject {
            put("type", "weekly")
            put("time", "09:00")
            put("days", buildJsonArray {
                add(JsonPrimitive("MONDAY"))
                add(JsonPrimitive(5))
            })
        }
        assertEquals(
            ScheduleTrigger.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), LocalTime.of(9, 0)),
            scheduleTriggerFromJsonObject(json),
        )
    }

    @Test
    fun scheduleJsonRoundTrip() {
        val schedule = Schedule(
            id = "schedule-1",
            name = "Morning brief",
            prompt = "Summarize my notes",
            trigger = ScheduleTrigger.Daily(LocalTime.of(8, 0)),
            enabled = false,
            sessionId = "session-1",
            sessionFile = "/data/sessions/session-1.jsonl",
            cwd = "/data/home",
            nextRunAt = 1_800_000_000_000L,
            lastDeliveredAt = 1_700_000_000_000L,
            lastError = ScheduleError.DELIVERY_FAILED,
            createdAt = 100,
            updatedAt = 200,
        )
        assertEquals(schedule, scheduleFromJsonObject(schedule.toJsonObject()))
        assertEquals(ZonedDateTime.of(2026, 10, 24, 8, 0, 0, 0, zone), schedule.nextRun(now))
    }

    @Test
    fun lastErrorIsStoredAsEnumName() {
        val schedule = Schedule(
            id = "id",
            name = "name",
            prompt = "prompt",
            trigger = ScheduleTrigger.Daily(LocalTime.of(8, 0)),
            sessionId = "session",
            sessionFile = "/tmp/session.jsonl",
            cwd = "/tmp",
            lastError = ScheduleError.SESSION_MISSING,
        )
        val json = schedule.toJsonObject()
        assertEquals("SESSION_MISSING", (json["lastError"] as JsonPrimitive).content)
        assertEquals(ScheduleError.SESSION_MISSING, scheduleFromJsonObject(json).lastError)
    }

    @Test
    fun missingOptionalFieldsDecodeAsNull() {
        val json = buildJsonObject {
            put("id", "id-1")
            put("name", "name")
            put("prompt", "prompt")
            put("trigger", ScheduleTrigger.Daily(LocalTime.of(8, 0)).toJsonObject())
            put("enabled", true)
            put("sessionId", "session-1")
            put("sessionFile", "/tmp/session.jsonl")
            put("cwd", "/tmp")
            put("createdAt", 1)
            put("updatedAt", 2)
        }
        val schedule = scheduleFromJsonObject(json)
        assertNull(schedule.nextRunAt)
        assertNull(schedule.lastDeliveredAt)
        assertNull(schedule.lastError)
    }

    @Test
    fun invalidJsonThrowsCorruptException() {
        val errors = listOf(
            runCatching { scheduleFromJsonObject(buildJsonObject { }) }.exceptionOrNull(),
            runCatching {
                scheduleFromJsonObject(
                    buildJsonObject {
                        put("id", "x")
                    },
                )
            }.exceptionOrNull(),
            runCatching { scheduleTriggerFromJsonObject(buildJsonObject { }) }.exceptionOrNull(),
            runCatching {
                scheduleTriggerFromJsonObject(
                    buildJsonObject {
                        put("type", "weekly")
                        put("time", "09:00")
                        put("days", buildJsonArray { add(JsonPrimitive(9)) })
                    },
                )
            }.exceptionOrNull(),
            runCatching {
                scheduleFromJsonObject(
                    buildJsonObject {
                        put("id", "x")
                        put("name", "n")
                        put("prompt", "p")
                        put("trigger", ScheduleTrigger.Daily(LocalTime.of(8, 0)).toJsonObject())
                        put("enabled", true)
                        put("sessionId", "s")
                        put("sessionFile", "f")
                        put("cwd", "c")
                        put("lastError", "NOT_AN_ERROR")
                        put("createdAt", 1)
                        put("updatedAt", 2)
                    },
                )
            }.exceptionOrNull(),
        )
        assertTrue(errors.all { it is CorruptScheduleDataException })
    }
}
