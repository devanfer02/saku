package com.dvnnfrr.saku.domain

/** Which month Trans and Stats count in, once a pay cycle is set. */
enum class MonthView {
    CALENDAR,
    PAY,
}

/**
 * The pay cycle and the month view chosen over it.
 *
 * [view] is remembered even while [startDay] is unset, so clearing and re-setting the
 * cycle comes back to the view the user last picked; it simply has no effect meanwhile.
 */
data class PeriodSettings(
    /** The day of the month a pay month starts on, 1 to 31, or null for none set. */
    val startDay: Int? = null,
    val view: MonthView = MonthView.CALENDAR,
) {
    init {
        require(startDay == null || startDay in 1..31) { "Pay start day $startDay is not a day of the month" }
    }

    /** The start day months are built from right now: null means calendar months. */
    val activeStartDay: Int? get() = startDay.takeIf { view == MonthView.PAY }

    /** The switch to show, or null to hide it because no pay cycle is set. */
    val switchView: MonthView? get() = view.takeIf { startDay != null }
}
