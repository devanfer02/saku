package com.dvnnfrr.saku.ui.paycycle

import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.ui.shared.paySpanLabel
import com.dvnnfrr.saku.ui.shared.periodLabel
import kotlinx.datetime.LocalDate

/*
 * Pure assembly for the pay cycle screen. No Compose, no Android, no coroutines.
 */

/** One previewed pay month: its name and, beneath it, its exact days. */
data class PayMonthPreview(
    val range: DateRange,
    val label: String,
    val span: String,
)

/**
 * Everything the pay cycle screen draws. The previews are null while no start day is
 * set, because there is no pay month to show.
 */
data class PayCycleUiState(
    val startDay: Int? = null,
    val current: PayMonthPreview? = null,
    val previous: PayMonthPreview? = null,
)

/** The current and previous pay months around [today] for [startDay]. */
fun assemblePayCycle(startDay: Int?, today: LocalDate): PayCycleUiState {
    if (startDay == null) return PayCycleUiState()
    val current = DateRange.ofPayMonth(today, startDay)
    return PayCycleUiState(
        startDay = startDay,
        current = current.toPreview(),
        previous = current.previous().toPreview(),
    )
}

private fun DateRange.toPreview() = PayMonthPreview(
    range = this,
    label = periodLabel(this),
    span = paySpanLabel(this),
)
