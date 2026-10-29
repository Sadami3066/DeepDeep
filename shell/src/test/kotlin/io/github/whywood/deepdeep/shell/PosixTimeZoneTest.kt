package io.github.whywood.deepdeep.shell

import java.time.Instant
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class PosixTimeZoneTest {
    private val july = Instant.parse("2026-07-15T12:00:00Z").toEpochMilli()
    private val january = Instant.parse("2026-01-15T12:00:00Z").toEpochMilli()

    @Test
    fun shanghaiIsCstMinusEight() {
        assertEquals("CST-8", posixTimeZone(TimeZone.getTimeZone("Asia/Shanghai"), july))
        assertEquals("CST-8", posixTimeZone(TimeZone.getTimeZone("Asia/Shanghai"), january))
    }

    @Test
    fun utcHasZeroOffset() {
        assertEquals("UTC0", posixTimeZone(TimeZone.getTimeZone("Etc/UTC"), july))
    }

    @Test
    fun daylightSavingZoneUsesActiveAbbreviationAndOffset() {
        assertEquals("EDT+4", posixTimeZone(TimeZone.getTimeZone("America/New_York"), july))
        assertEquals("EST+5", posixTimeZone(TimeZone.getTimeZone("America/New_York"), january))
    }

    @Test
    fun halfHourOffsetUsesMinutes() {
        assertEquals("IST-5:30", posixTimeZone(TimeZone.getTimeZone("Asia/Kolkata"), july))
        assertEquals("NST+3:30", posixTimeZone(TimeZone.getTimeZone("America/St_Johns"), january))
    }

    @Test
    fun nonLetterCharactersAreDroppedFromAbbreviation() {
        assertEquals("GMT-8", posixTimeZone(TimeZone.getTimeZone("Etc/GMT-8"), july))
    }

    @Test
    fun zeroOffsetWithStandardAbbreviation() {
        assertEquals("GMT0", posixTimeZone(TimeZone.getTimeZone("Europe/London"), january))
    }
}
