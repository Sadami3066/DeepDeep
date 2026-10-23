package io.github.whywood.deepdeep.runtime

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

class InvalidCronExpressionException(message: String) : Exception(message)

internal class CronExpression private constructor(
    private val minutes: BooleanArray,
    private val hours: BooleanArray,
    private val daysOfMonth: BooleanArray,
    private val months: BooleanArray,
    private val daysOfWeek: BooleanArray,
    private val dayOfMonthRestricted: Boolean,
    private val dayOfWeekRestricted: Boolean,
) {
    internal fun minuteValues(): Set<Int> = (0..59).filter { minutes[it] }.toSet()

    internal fun hourValues(): Set<Int> = (0..23).filter { hours[it] }.toSet()

    internal fun dayOfMonthValues(): Set<Int> = (1..31).filter { daysOfMonth[it] }.toSet()

    internal fun monthValues(): Set<Int> = (1..12).filter { months[it] }.toSet()

    internal fun weekdayValues(): Set<DayOfWeek> =
        DayOfWeek.entries.filter { daysOfWeek[it.value % 7] }.toSet()

    fun nextRun(now: ZonedDateTime): ZonedDateTime? {
        val zone = now.zone
        var date = now.toLocalDate()
        repeat(MAX_SEARCH_DAYS) {
            if (matchesDate(date)) {
                for (hour in 0..23) {
                    if (!hours[hour]) continue
                    for (minute in 0..59) {
                        if (!minutes[minute]) continue
                        val candidate = ZonedDateTime.of(date, LocalTime.of(hour, minute), zone)
                        if (candidate.isAfter(now)) return candidate
                    }
                }
            }
            date = date.plusDays(1)
        }
        return null
    }

    private fun matchesDate(date: LocalDate): Boolean {
        if (!months[date.monthValue]) return false
        val dom = daysOfMonth[date.dayOfMonth]
        val dow = daysOfWeek[date.dayOfWeek.value % 7]
        return if (dayOfMonthRestricted && dayOfWeekRestricted) dom || dow else dom && dow
    }

    companion object {
        private const val MAX_SEARCH_DAYS = 1827

        private val MONTH_NAMES = listOf(
            "JAN", "FEB", "MAR", "APR", "MAY", "JUN",
            "JUL", "AUG", "SEP", "OCT", "NOV", "DEC",
        )

        private val WEEKDAY_NAMES = listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")

        fun parse(expression: String): CronExpression {
            val fields = expression.trim().split(Regex("\\s+"))
            if (fields.size != 5) {
                throw InvalidCronExpressionException("cron 需要 5 段：分钟 小时 日 月 星期")
            }
            val minutes = parseField(fields[0], 0, 59, null, "分钟")
            val hours = parseField(fields[1], 0, 23, null, "小时")
            val dom = parseField(fields[2], 1, 31, null, "日")
            val months = parseField(fields[3], 1, 12, MONTH_NAMES, "月")
            val rawWeekdays = parseField(fields[4], 0, 7, WEEKDAY_NAMES, "星期")
            val weekdays = BooleanArray(7) { index ->
                rawWeekdays[index] || (index == 0 && rawWeekdays[7])
            }
            return CronExpression(
                minutes = minutes,
                hours = hours,
                daysOfMonth = dom,
                months = months,
                daysOfWeek = weekdays,
                dayOfMonthRestricted = !(1..31).all { dom[it] },
                dayOfWeekRestricted = !(0..6).all { weekdays[it] },
            )
        }

        private fun parseField(
            text: String,
            min: Int,
            max: Int,
            names: List<String>?,
            label: String,
        ): BooleanArray {
            if (text.isBlank()) throw InvalidCronExpressionException("$label 段为空")
            val selected = BooleanArray(max + 1)
            for (rawPart in text.split(',')) {
                val part = rawPart.trim()
                if (part.isEmpty()) throw InvalidCronExpressionException("$label 段包含空项")
                val slash = part.indexOf('/')
                val base = if (slash >= 0) part.substring(0, slash) else part
                val step = if (slash >= 0) {
                    part.substring(slash + 1).toIntOrNull()
                        ?.takeIf { it >= 1 }
                        ?: throw InvalidCronExpressionException("$label 段的步长无效：$part")
                } else {
                    1
                }
                val range = when {
                    base == "*" || base == "?" -> min..max
                    '-' in base -> {
                        val start = parseValue(base.substringBefore('-'), min, max, names, label)
                        val end = parseValue(base.substringAfter('-'), min, max, names, label)
                        if (start > end) {
                            throw InvalidCronExpressionException("$label 段的范围无效：$part")
                        }
                        start..end
                    }

                    slash >= 0 -> parseValue(base, min, max, names, label)..max
                    else -> {
                        val value = parseValue(base, min, max, names, label)
                        value..value
                    }
                }
                var value = range.first
                while (value <= range.last) {
                    selected[value] = true
                    value += step
                }
            }
            if (!selected.any { it }) throw InvalidCronExpressionException("$label 段没有有效取值")
            return selected
        }

        private fun parseValue(
            raw: String,
            min: Int,
            max: Int,
            names: List<String>?,
            label: String,
        ): Int {
            val token = raw.trim()
            val numeric = token.toIntOrNull()
            if (numeric != null) {
                if (numeric !in min..max) {
                    throw InvalidCronExpressionException("$label 段超出范围：$token（$min..$max）")
                }
                return numeric
            }
            val named = names?.indexOfFirst { it.equals(token, ignoreCase = true) } ?: -1
            if (named >= 0) return named + if (names === MONTH_NAMES) 1 else 0
            throw InvalidCronExpressionException("$label 段取值无效：$token")
        }
    }
}

sealed interface CronSummary {
    data object EveryMinute : CronSummary

    data class EveryMinutes(val every: Int) : CronSummary

    data class EveryHours(val every: Int, val minute: Int) : CronSummary

    data class Daily(val time: LocalTime) : CronSummary

    data class Weekly(val days: Set<DayOfWeek>, val time: LocalTime) : CronSummary

    data class Monthly(val day: Int, val time: LocalTime) : CronSummary

    data class Yearly(val month: Int, val day: Int, val time: LocalTime) : CronSummary

    data class Custom(val expression: String) : CronSummary
}

fun summarizeCron(expression: String): CronSummary? {
    val cron = try {
        CronExpression.parse(expression)
    } catch (_: InvalidCronExpressionException) {
        return null
    }
    val minutes = cron.minuteValues()
    val hours = cron.hourValues()
    val dom = cron.dayOfMonthValues()
    val months = cron.monthValues()
    val dows = cron.weekdayValues()
    val time = if (minutes.size == 1 && hours.size == 1) {
        LocalTime.of(hours.first(), minutes.first())
    } else {
        null
    }
    val minutesAll = minutes.size == FULL_MINUTES
    val hoursAll = hours.size == FULL_HOURS
    val domAll = dom.size == FULL_DAYS
    val monthsAll = months.size == FULL_MONTHS
    val dowsAll = dows.size == FULL_WEEKDAYS
    return when {
        minutesAll && hoursAll && domAll && monthsAll && dowsAll -> CronSummary.EveryMinute
        domAll && monthsAll && dowsAll -> when {
            hoursAll && minutes.size == 1 -> CronSummary.EveryHours(1, minutes.first())
            hoursAll -> stepOf(minutes, FULL_MINUTES)?.let { CronSummary.EveryMinutes(it) }
                ?: CronSummary.Custom(expression)
            time != null -> CronSummary.Daily(time)
            minutes.size == 1 -> stepOf(hours, FULL_HOURS)?.let {
                CronSummary.EveryHours(it, minutes.first())
            } ?: CronSummary.Custom(expression)
            else -> CronSummary.Custom(expression)
        }
        time != null && minutes.size == 1 && hours.size == 1 -> when {
            domAll && monthsAll -> CronSummary.Weekly(dows, time)
            dom.size == 1 && monthsAll -> CronSummary.Monthly(dom.first(), time)
            dom.size == 1 && months.size == 1 -> CronSummary.Yearly(months.first(), dom.first(), time)
            else -> CronSummary.Custom(expression)
        }
        else -> CronSummary.Custom(expression)
    }
}

private const val FULL_MINUTES = 60
private const val FULL_HOURS = 24
private const val FULL_DAYS = 31
private const val FULL_MONTHS = 12
private const val FULL_WEEKDAYS = 7

private fun stepOf(values: Set<Int>, full: Int): Int? {
    if (values.size <= 1) return null
    val sorted = values.sorted()
    if (sorted.first() != 0) return null
    val step = sorted[1] - sorted[0]
    if (step <= 1) return null
    if (sorted.indices.any { sorted[it] != it * step }) return null
    if (sorted.last() + step < full) return null
    return step
}
