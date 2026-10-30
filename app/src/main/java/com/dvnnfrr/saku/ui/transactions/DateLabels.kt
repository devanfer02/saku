package com.dvnnfrr.saku.ui.transactions

import com.dvnnfrr.saku.domain.DateRange
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

/**
 * Day header: `Thu 29 Oct`. No year, because the month header above the list already
 * names it, and the weekday first because that is how a day gets remembered.
 */
fun LocalDate.dayHeaderLabel(): String =
    "${dayOfWeek.shortLabel()} $dayOfMonth ${month.shortLabel()}"

/** A date standing on its own, where no header supplies the year: `29 Oct 2026`. */
fun LocalDate.dateLabel(): String = "$dayOfMonth ${month.shortLabel()} $year"

/**
 * A Monthly row's span: `28.09 ~ 04.10`, the full week even across a month edge, so a
 * row that counts days of the next month says so.
 */
fun DateRange.dottedSpanLabel(): String = "${start.dotted()} ~ ${endInclusive.dotted()}"

private fun LocalDate.dotted(): String =
    "${dayOfMonth.toString().padStart(2, '0')}.${monthNumber.toString().padStart(2, '0')}"

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
