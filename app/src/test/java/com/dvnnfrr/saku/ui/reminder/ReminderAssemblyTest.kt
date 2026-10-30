package com.dvnnfrr.saku.ui.reminder

import com.dvnnfrr.saku.domain.ReminderSchedule
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderAssemblyTest {

    private val zone = TimeZone.of("Asia/Jakarta")

    // Friday 30 Oct 2026, 10:00.
    private val now = LocalDateTime(2026, 10, 30, 10, 0).toInstant(zone)

    @Test
    fun `off shows the preselected day and time and no next reminder`() {
        val state = assembleReminder(ReminderSchedule(), canNotify = true, now = now, zone = zone)

        assertFalse(state.enabled)
        assertEquals(DayOfWeek.SUNDAY, state.day)
        assertEquals("20:00", state.timeLabel)
        assertNull(state.next)
        assertFalse(state.blocked)
    }

    @Test
    fun `on names the coming reminder`() {
        val state = assembleReminder(ReminderSchedule(enabled = true), canNotify = true, now = now, zone = zone)

        assertEquals(NextReminder("Sun", "1 Nov 2026", "20:00"), state.next)
    }

    @Test
    fun `a new day moves the next reminder`() {
        val wednesday = ReminderSchedule(enabled = true, day = DayOfWeek.WEDNESDAY, time = LocalTime(7, 30))
        val state = assembleReminder(wednesday, canNotify = true, now = now, zone = zone)

        assertEquals(NextReminder("Wed", "4 Nov 2026", "07:30"), state.next)
        assertEquals("07:30", state.timeLabel)
    }

    @Test
    fun `blocked only while on`() {
        assertTrue(assembleReminder(ReminderSchedule(enabled = true), canNotify = false, now = now, zone = zone).blocked)
        assertFalse(assembleReminder(ReminderSchedule(enabled = false), canNotify = false, now = now, zone = zone).blocked)
    }

    @Test
    fun `the days run Monday to Sunday with short names`() {
        assertEquals(
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
            REMINDER_DAYS.map(::reminderDayLabel),
        )
    }
}
