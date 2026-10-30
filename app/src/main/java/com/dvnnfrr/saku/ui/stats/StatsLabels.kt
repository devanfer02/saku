package com.dvnnfrr.saku.ui.stats

import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.ui.transactions.dateLabel

/*
 * Date and percentage wording that is the screen's own, kept pure so the strings can be
 * asserted without a Compose harness.
 *
 * Month names are a hardcoded list for the same reason `PeriodBar` and `formatIdr` use
 * one: a device set to another locale would otherwise relabel the app's own chrome while
 * the rest of it stayed in English.
 */

private const val EN_DASH = "–"

/**
 * The range as explicit start and end dates — `1 Aug 2026 – 31 Aug 2026`.
 *
 * Shown under the period header even in Monthly mode, where the header already reads
 * "August 2026". The spec asks for the boundaries in full, and a user stepping through
 * weeks otherwise has to work out which seven days "Weekly" currently means.
 */
fun explicitRangeLabel(range: DateRange): String {
    val start = range.start.dateLabel()
    if (range.start == range.endInclusive) return start
    return "$start $EN_DASH ${range.endInclusive.dateLabel()}"
}
