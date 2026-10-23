package io.github.whywood.deepdeep.runtime

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class ScheduleCronTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = ZonedDateTime.of(2026, 10, 23, 12, 0, 0, 0, zone)

    private fun next(expression: String, from: ZonedDateTime = now): ZonedDateTime? =
        CronExpression.parse(expression).nextRun(from)

    @Test
    fun supportsListsRangesAndSteps() {
        assertEquals(
            ZonedDateTime.of(2026, 10, 24, 8, 15, 0, 0, zone),
            next("15,45 8-10 * * *"),
        )
        assertEquals(
            ZonedDateTime.of(2026, 10, 23, 18, 0, 0, 0, zone),
            next("0 */6 * * *"),
        )
        assertEquals(
            ZonedDateTime.of(2026, 10, 24, 8, 0, 0, 0, zone),
            next("0 8-10/2 * * *"),
        )
        assertEquals(
            ZonedDateTime.of(2026, 10, 23, 12, 15, 0, 0, zone),
            next("*/15 * * * *"),
        )
    }

    @Test
    fun supportsMonthAndWeekdayNames() {
        assertEquals(
            ZonedDateTime.of(2027, 1, 1, 0, 0, 0, 0, zone),
            next("0 0 1 JAN *"),
        )
        assertEquals(
            ZonedDateTime.of(2026, 10, 25, 9, 0, 0, 0, zone),
            next("0 9 * * SUN"),
        )
        assertEquals(
            ZonedDateTime.of(2026, 10, 26, 0, 0, 0, 0, zone),
            next("0 0 * * MON-FRI"),
        )
    }

    @Test
    fun sundayCanBeZeroOrSeven() {
        assertEquals(
            ZonedDateTime.of(2026, 10, 25, 9, 0, 0, 0, zone),
            next("0 9 * * 0"),
        )
        assertEquals(
            ZonedDateTime.of(2026, 10, 25, 9, 0, 0, 0, zone),
            next("0 9 * * 7"),
        )
    }

    @Test
    fun dayOfMonthAndWeekdayUseOrSemantics() {
        assertEquals(
            ZonedDateTime.of(2026, 10, 26, 9, 0, 0, 0, zone),
            next("0 9 1 * MON"),
        )
    }

    @Test
    fun handlesLeapDay() {
        assertEquals(
            ZonedDateTime.of(2028, 2, 29, 0, 0, 0, 0, zone),
            next("0 0 29 2 *"),
        )
        assertNull(next("0 0 30 2 *"))
    }

    @Test
    fun nextRunIsStrictlyAfterNow() {
        val exact = ZonedDateTime.of(2026, 10, 23, 12, 30, 0, 0, zone)
        assertEquals(
            ZonedDateTime.of(2026, 10, 24, 12, 30, 0, 0, zone),
            next("30 12 * * *", exact),
        )
        assertEquals(
            ZonedDateTime.of(2026, 10, 23, 12, 31, 0, 0, zone),
            next("* * * * *", exact),
        )
    }

    @Test
    fun rejectsMalformedExpressions() {
        val invalid = listOf(
            "* * * *",
            "* * * * * *",
            "60 * * * *",
            "* 24 * * *",
            "* * 32 * *",
            "* * * 13 *",
            "* * * * 8",
            "*/0 * * * *",
            "5-1 * * * *",
            "MON * * * *",
        )
        invalid.forEach { expression ->
            try {
                CronExpression.parse(expression)
                fail("expected invalid cron: $expression")
            } catch (_: InvalidCronExpressionException) {
            }
        }
    }
}
