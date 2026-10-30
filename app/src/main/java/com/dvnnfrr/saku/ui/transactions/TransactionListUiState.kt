package com.dvnnfrr.saku.ui.transactions

import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.PeriodTotals
import com.dvnnfrr.saku.domain.TransactionFilter
import com.dvnnfrr.saku.domain.TxnType
import kotlinx.datetime.LocalDate

enum class TransactionTab { DAILY, CALENDAR, MONTHLY }

/**
 * Everything the transaction screen draws, already resolved.
 *
 * The screen holds no ids: a row arrives with its account name and colour slot
 * already attached, so the composable never reaches back for a lookup. Currency is
 * still [Money] rather than a string — formatting is the theme's job and belongs at
 * the point of render, not here.
 */
data class TransactionListUiState(
    val range: DateRange,
    val tab: TransactionTab = TransactionTab.DAILY,
    val totals: PeriodTotals = PeriodTotals.EMPTY,
    val days: List<DaySection> = emptyList(),
    val calendar: CalendarMonth? = null,
    /** Set when the user opens a day from the calendar; cleared once consumed. */
    val pendingScrollTo: LocalDate? = null,
    /** The Calendar's picked dates, folded. Null when nothing is picked. */
    val calendarRange: CalendarRangeSummary? = null,
    /** The filter the figures above were computed under, for the bar and the action. */
    val filter: TransactionFilter = TransactionFilter.ALL,
    /** The Monthly year. Null outside the Monthly tab, which is not queried until opened. */
    val monthly: MonthlyYear? = null,
    val loading: Boolean = true,
) {
    /** What the header names and the filter sheet opens on: the year in Monthly, else the month. */
    val headerRange: DateRange
        get() = monthly?.let { DateRange.ofYear(it.year) } ?: range

    val headerTotals: PeriodTotals get() = monthly?.totals ?: totals
}

/**
 * One day header plus its rows.
 *
 * [income] and [expense] exclude transfers and adjustments, while [rows] includes
 * them. That asymmetry is the requirement, not an oversight: moving money between
 * your own accounts is visible history but is not spending.
 */
data class DaySection(
    val date: LocalDate,
    val income: Money,
    val expense: Money,
    val rows: List<TransactionRowUi>,
) {
    val net: Money get() = income - expense
}

data class TransactionRowUi(
    val id: Long,
    val type: TxnType,
    val amount: Money,
    val categoryName: String?,
    val subcategoryName: String?,
    val note: String?,
    val account: AccountRef,
    /** Present only on a transfer, which shows both ends. */
    val toAccount: AccountRef?,
    /** The category's colour for the row's dot. Null on a row with no category. */
    val categorySlot: ColorSlot? = null,
)

/** An account reduced to what a row draws: a name and a swatch. */
data class AccountRef(
    val id: Long,
    val name: String,
    val colorSlot: ColorSlot,
)

/**
 * The calendar grid for one month.
 *
 * [leadingBlanks] is how many cells to skip before the 1st so it lands under its
 * weekday, with weeks running Monday to Sunday to match [DateRange.ofWeek].
 */
data class CalendarMonth(
    val range: DateRange,
    val leadingBlanks: Int,
    val cells: List<CalendarCell>,
)

/**
 * A day in the grid.
 *
 * [net] is null when the day holds no transactions at all, which is deliberately not
 * the same as a net of zero. The whole point of the calendar is to expose stretches
 * of days that were never logged, and rendering an unlogged day as "0" hides exactly
 * the gap the user is looking for.
 */
data class CalendarCell(
    val date: LocalDate,
    val net: Money?,
) {
    val hasTransactions: Boolean get() = net != null
}
