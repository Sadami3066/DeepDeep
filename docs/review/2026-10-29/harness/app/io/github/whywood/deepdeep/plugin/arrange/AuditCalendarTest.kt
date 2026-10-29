package io.github.whywood.deepdeep.plugin.arrange

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class AuditCalendarTest {
    @Test fun shanghaiDateOnlyAllDayEventMovesToPreviousDay() {
        val zone = ZoneId.of("Asia/Shanghai")
        val input = parseCalendarMillis("2026-10-30", zone)
        val range = resolveEventRange(input, null, allDay = true)
        val displayed = formatCalendarTime(range.first, allDay = true, zone = zone)
        assertEquals("2026-10-29", displayed)
        println("AUDIT calendar requested=2026-10-30 actual=2026-10-29 zone=Asia/Shanghai")
    }
}
