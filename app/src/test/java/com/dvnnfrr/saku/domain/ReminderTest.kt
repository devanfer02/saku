package com.dvnnfrr.saku.domain

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Test

/** `logging-reminder/spec.md`: the weekly schedule and the notification's day count. */
class ReminderTest {

    private val jakarta = TimeZone.of("Asia/Jakarta")
    private val sunday8pm = ReminderSchedule(enabled = true)

    // 1 Nov 2026 is a Sunday.
    private fun at(day: Int, hour: Int, minute: Int = 0, month: Int = 11, zone: TimeZone = jakarta) =
        LocalDateTime(2026, month, day, hour, minute).toInstant(zone)

    @Test
    fun `defaults to off on Sunday at 20_00`() {
        val schedule = ReminderSchedule()
        assertEquals(false, schedule.enabled)
        assertEquals(DayOfWeek.SUNDAY, schedule.day)
        assertEquals(LocalTime(20, 0), schedule.time)
    }

    @Test
    fun `later the same day fires today`() {
        assertEquals(at(1, 20), nextReminderAt(at(1, 19), sunday8pm, jakarta))
    }

    @Test
    fun `exactly at the time fires next week`() {
        assertEquals(at(8, 20), nextReminderAt(at(1, 20), sunday8pm, jakarta))
    }

    @Test
    fun `just after the time fires next week`() {
        assertEquals(at(8, 20), nextReminderAt(at(1, 20, 1), sunday8pm, jakarta))
    }

    @Test
    fun `midweek fires on the coming Sunday`() {
        // Wednesday 4 Nov.
        assertEquals(at(8, 20), nextReminderAt(at(4, 9), sunday8pm, jakarta))
    }

    @Test
    fun `another day and time`() {
        val friday730 = ReminderSchedule(enabled = true, day = DayOfWeek.FRIDAY, time = LocalTime(7, 30))
        // Sunday 1 Nov to Friday 6 Nov.
        assertEquals(at(6, 7, 30), nextReminderAt(at(1, 21), friday730, jakarta))
    }

    @Test
    fun `daylight saving keeps the wall-clock time`() {
        // Europe/Berlin leaves summer time on Sunday 25 Oct 2026.
        val berlin = TimeZone.of("Europe/Berlin")
        val saturday = at(day = 24, hour = 21, month = 10, zone = berlin)
        assertEquals(
            at(day = 25, hour = 20, month = 10, zone = berlin),
            nextReminderAt(saturday, sunday8pm, berlin),
        )
    }

    @Test
    fun `nothing ever entered`() {
        assertEquals(ReminderNudge.NothingYet, reminderNudge(null, at(1, 20), jakarta))
    }

    @Test
    fun `entered earlier today`() {
        assertEquals(ReminderNudge.LoggedToday, reminderNudge(at(1, 0, 5), at(1, 20), jakarta))
    }

    @Test
    fun `late yesterday is one day, not zero`() {
        assertEquals(ReminderNudge.DaysSince(1), reminderNudge(at(31, 23, 50, month = 10), at(1, 20), jakarta))
    }

    @Test
    fun `Monday to Sunday is six days`() {
        assertEquals(ReminderNudge.DaysSince(6), reminderNudge(at(26, 8, month = 10), at(1, 20), jakarta))
    }

    @Test
    fun `an entry dated ahead counts as today`() {
        assertEquals(ReminderNudge.LoggedToday, reminderNudge(at(3, 12), at(1, 20), jakarta))
    }
}
