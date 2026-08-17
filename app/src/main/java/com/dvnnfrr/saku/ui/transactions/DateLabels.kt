package com.dvnnfrr.saku.ui.transactions

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

/*
 * Date labels for the transaction screen.
 *
 * Built from the enum names rather than a java.time formatter so they cannot shift
 * with the device locale. Amounts already refuse to follow the locale — a device set
 * to en-US still reads Rp 5.061.400 — and a day header that switched language while
 * the figure beside it did not would look like a bug.
 */

internal fun DayOfWeek.shortLabel(): String = name.take(3).titleCase()

internal fun Month.shortLabel(): String = name.take(3).titleCase()

private fun String.titleCase(): String = first() + drop(1).lowercase()

/** Day header: `14 Fri Aug 2026`, as the spec spells it out. */
fun LocalDate.dayHeaderLabel(): String =
    "$dayOfMonth ${dayOfWeek.shortLabel()} ${month.shortLabel()} $year"

/** Column headings for the calendar grid, Monday first to match DateRange.ofWeek. */
val WEEKDAY_HEADINGS: List<String> = listOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY,
    DayOfWeek.SUNDAY,
).map { it.shortLabel() }
