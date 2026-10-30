package com.dvnnfrr.saku.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * The weekly logging reminder as the user set it. Off on a fresh install, with Sunday
 * evening preselected so turning it on is one tap for the common case.
 */
data class ReminderSchedule(
    val enabled: Boolean = false,
    val day: DayOfWeek = DayOfWeek.SUNDAY,
    val time: LocalTime = LocalTime(20, 0),
)

/**
 * The first moment strictly after [now] that falls on the schedule's day and time in
 * [zone]. "Strictly" so that re-arming from inside the alarm that just fired lands a
 * week later instead of firing again at once.
 *
 * Built from the local date and time, not by adding seven days of seconds, so a zone
 * with daylight saving still fires at the same wall-clock time.
 */
fun nextReminderAt(now: Instant, schedule: ReminderSchedule, zone: TimeZone): Instant {
    val local = now.toLocalDateTime(zone)
    val daysAhead = (schedule.day.isoDayNumber - local.dayOfWeek.isoDayNumber + DAYS_IN_WEEK) % DAYS_IN_WEEK
    val date = local.date.plus(daysAhead, DateTimeUnit.DAY)
    val candidate = LocalDateTime(date, schedule.time).toInstant(zone)
    if (candidate > now) return candidate
    return LocalDateTime(date.plus(DAYS_IN_WEEK, DateTimeUnit.DAY), schedule.time).toInstant(zone)
}

private const val DAYS_IN_WEEK = 7

/** What the reminder says about the user's logging, before it becomes copy. */
sealed interface ReminderNudge {
    /** The ledger holds nothing yet. */
    data object NothingYet : ReminderNudge

    /** Something was entered today; the reminder still shows, with a lighter line. */
    data object LoggedToday : ReminderNudge

    /** [days] calendar days since the last entry, one or more. */
    data class DaysSince(val days: Int) : ReminderNudge
}

/**
 * Calendar days in [zone] between [lastEntry] and [now], so an entry at 23:50 yesterday
 * reads as yesterday rather than as "0 days". An entry dated in the future counts as
 * today: it was typed, and the reminder is about typing.
 */
fun reminderNudge(lastEntry: Instant?, now: Instant, zone: TimeZone): ReminderNudge {
    if (lastEntry == null) return ReminderNudge.NothingYet
    val days = lastEntry.toLocalDateTime(zone).date.daysUntil(now.toLocalDateTime(zone).date)
    if (days <= 0) return ReminderNudge.LoggedToday
    return ReminderNudge.DaysSince(days)
}
