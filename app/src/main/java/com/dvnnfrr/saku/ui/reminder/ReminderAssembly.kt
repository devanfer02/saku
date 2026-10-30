package com.dvnnfrr.saku.ui.reminder

import com.dvnnfrr.saku.domain.ReminderSchedule
import com.dvnnfrr.saku.domain.nextReminderAt
import com.dvnnfrr.saku.ui.transactions.clockLabel
import com.dvnnfrr.saku.ui.transactions.dateLabel
import com.dvnnfrr.saku.ui.transactions.shortLabel
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/*
 * Pure assembly for the reminder setting. No Compose, no Android.
 */

data class ReminderUiState(
    val enabled: Boolean = false,
    val day: DayOfWeek = DayOfWeek.SUNDAY,
    val time: LocalTime = LocalTime(20, 0),
    val timeLabel: String = "20:00",
    /** On, but the system will not show Saku's notifications. */
    val blocked: Boolean = false,
    /** When the next reminder fires; null while the reminder is off. */
    val next: NextReminder? = null,
)

/** The pieces of "Next reminder: Sun 8 Nov 2026 at 20:00". */
data class NextReminder(val day: String, val date: String, val time: String)

/** The days in the order the setting lists them: Monday first, as the calendar does. */
val REMINDER_DAYS: List<DayOfWeek> = DayOfWeek.entries

/** Short day names for the day options: `Mon` … `Sun`. */
fun reminderDayLabel(day: DayOfWeek): String = day.shortLabel()

fun assembleReminder(
    schedule: ReminderSchedule,
    canNotify: Boolean,
    now: Instant,
    zone: TimeZone,
): ReminderUiState {
    val next = if (schedule.enabled) {
        val at = nextReminderAt(now, schedule, zone).toLocalDateTime(zone)
        NextReminder(at.dayOfWeek.shortLabel(), at.date.dateLabel(), at.time.clockLabel())
    } else {
        null
    }
    return ReminderUiState(
        enabled = schedule.enabled,
        day = schedule.day,
        time = schedule.time,
        timeLabel = schedule.time.clockLabel(),
        blocked = schedule.enabled && !canNotify,
        next = next,
    )
}
