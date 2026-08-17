package com.dvnnfrr.saku.ui.transactions

import com.dvnnfrr.saku.domain.Account
import com.dvnnfrr.saku.domain.Category
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.PeriodTotals
import com.dvnnfrr.saku.domain.Subcategory
import com.dvnnfrr.saku.domain.Transaction
import com.dvnnfrr.saku.domain.TxnType
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/*
 * Pure assembly for the transaction screen. No Compose, no Android, no coroutines —
 * every rule the spec states about grouping, subtotals and empty days is decided
 * here and can be tested on the JVM against a plain list.
 *
 * The header, the day subtotals and the calendar are all folded from the SAME list
 * of transactions rather than from three separate SQL aggregates. A month's rows are
 * already loaded for the Daily tab, so folding them costs nothing and removes the
 * only way those three figures could ever disagree with each other. The SQL
 * aggregates stay for the stats screen, which reports on periods it does not list.
 */

/** Lookups a row needs to render. Missing ids resolve to null rather than throwing. */
class DisplayIndex(
    accounts: List<Account>,
    categories: List<Category>,
    subcategories: List<Subcategory>,
) {
    private val accountsById = accounts.associateBy { it.id }
    private val categoryNames = categories.associate { it.id to it.name }
    private val subcategoryNames = subcategories.associate { it.id to it.name }

    /**
     * A deleted account would otherwise crash a row mid-scroll. Accounts referenced by
     * transactions cannot be hard-deleted, so this is defence rather than an expected
     * path — but a list screen is the wrong place to discover that assumption broke.
     */
    fun account(id: Long?): AccountRef? {
        val account = accountsById[id ?: return null] ?: return null
        return AccountRef(account.id, account.name, account.colorSlot)
    }

    fun categoryName(id: Long?): String? = id?.let { categoryNames[it] }

    fun subcategoryName(id: Long?): String? = id?.let { subcategoryNames[it] }
}

private val UNKNOWN_ACCOUNT = AccountRef(id = -1L, name = "—", colorSlot = ColorSlot.FIRST)

fun Transaction.toRowUi(index: DisplayIndex): TransactionRowUi = TransactionRowUi(
    id = id,
    type = type,
    amount = amount,
    categoryName = index.categoryName(categoryId),
    subcategoryName = index.subcategoryName(subcategoryId),
    note = note?.takeIf { it.isNotBlank() },
    account = index.account(accountId) ?: UNKNOWN_ACCOUNT,
    toAccount = index.account(toAccountId),
)

/**
 * Income and expense for the period, transfers and adjustments excluded.
 *
 * [TxnType.countsAsFlow] is the single place that rule is written down; every total
 * in the app routes through it so the four screens cannot drift apart.
 */
fun List<Transaction>.periodTotals(): PeriodTotals {
    var income = 0L
    var expense = 0L
    for (txn in this) {
        if (!txn.type.countsAsFlow) continue
        when (txn.type) {
            TxnType.INCOME -> income += txn.amount.rupiah
            TxnType.EXPENSE -> expense += txn.amount.rupiah
            else -> Unit
        }
    }
    return PeriodTotals(Money(income), Money(expense))
}

/**
 * Groups into day sections, newest day first and newest row first within a day.
 *
 * Days with no transactions get no section at all — the Daily tab renders history,
 * not a calendar. The Calendar tab is where absent days are made visible.
 */
fun List<Transaction>.toDaySections(zone: TimeZone, index: DisplayIndex): List<DaySection> =
    groupBy { it.occurredAt.toLocalDateTime(zone).date }
        .entries
        .sortedByDescending { it.key }
        .map { (date, txns) ->
            val totals = txns.periodTotals()
            DaySection(
                date = date,
                income = totals.income,
                expense = totals.expense,
                rows = txns
                    .sortedWith(compareByDescending<Transaction> { it.occurredAt }.thenByDescending { it.id })
                    .map { it.toRowUi(index) },
            )
        }

/**
 * Every day in [range], carrying a net only where the day actually holds rows.
 *
 * A day whose transactions are all transfers has a net of zero but is NOT empty, so
 * it renders as zero rather than as a gap. Deriving emptiness from the net instead of
 * from the row count would have reported such a day as unlogged.
 */
fun List<Transaction>.toCalendarMonth(range: DateRange, zone: TimeZone): CalendarMonth {
    val byDate = groupBy { it.occurredAt.toLocalDateTime(zone).date }
    val dayCount = range.start.daysUntil(range.endInclusive) + 1

    val cells = (0 until dayCount).map { offset ->
        val date = range.start.plus(offset, DateTimeUnit.DAY)
        val txns = byDate[date]
        CalendarCell(
            date = date,
            net = txns?.let { it.periodTotals().net },
        )
    }

    return CalendarMonth(
        range = range,
        leadingBlanks = range.start.dayOfWeek.isoDayNumber - DayOfWeek.MONDAY.isoDayNumber,
        cells = cells,
    )
}
