package com.dvnnfrr.saku.ui.stats

import com.dvnnfrr.saku.domain.DateRange
import kotlinx.datetime.LocalDate

/*
 * Date and percentage wording that is the screen's own, kept pure so the strings can be
 * asserted without a Compose harness.
 *
 * Month names are a hardcoded list for the same reason `PeriodBar` and `formatIdr` use
 * one: a device set to another locale would otherwise relabel the app's own chrome while
 * the rest of it stayed in English.
 */

private val SHORT_MONTHS = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

private const val EN_DASH = "–"

private fun shortMonth(date: LocalDate): String = SHORT_MONTHS[date.monthNumber - 1]

/**
 * The range as explicit start and end dates — `1 Aug 2026 – 31 Aug 2026`.
 *
 * Shown under the period header even in Monthly mode, where the header already reads
 * "August 2026". The spec asks for the boundaries in full, and a user stepping through
 * weeks otherwise has to work out which seven days "Weekly" currently means.
 */
fun explicitRangeLabel(range: DateRange): String {
    val start = "${range.start.dayOfMonth} ${shortMonth(range.start)} ${range.start.year}"
    if (range.start == range.endInclusive) return start
    val end = "${range.endInclusive.dayOfMonth} ${shortMonth(range.endInclusive)} " +
        "${range.endInclusive.year}"
    return "$start $EN_DASH $end"
}

/**
 * A movement against the previous period — `+12%`, `-4%`, `0%`.
 *
 * The sign is always written, including on an increase, because the figure beside it is
 * an expense: "12%" alone next to a spending total reads as a share of something.
 */
fun formatPercentChange(percent: Int): String = if (percent > 0) "+$percent%" else "$percent%"
