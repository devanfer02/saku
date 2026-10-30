package com.dvnnfrr.saku.ui.filter

import com.dvnnfrr.saku.domain.Account
import com.dvnnfrr.saku.domain.AccountFlows
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.Category
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.PeriodTotals
import com.dvnnfrr.saku.domain.TransactionFilter
import com.dvnnfrr.saku.domain.sharePercent

/*
 * Pure assembly for the filter sheet. No Compose, no Android, no coroutines: the tick and
 * "All" transitions, the account grouping, the ring shares and the bar's summary are all
 * decided here and tested on the JVM.
 *
 * Which rows pass the filter is NOT decided here. That is `TXN_FILTER_WHERE`, in SQL, the
 * one implementation of the rule.
 */

enum class FilterTab { INCOME, EXPENSES, ACCOUNT }

/** One ticked or unticked item on the INCOME or EXPENSES tab. */
data class CheckRow(
    val id: Long,
    val name: String,
    val colorSlot: ColorSlot,
    val ticked: Boolean,
)

/** One account on the ACCOUNT tab, with its four figures for the sheet's period. */
data class AccountCheckRow(
    val id: Long,
    val name: String,
    val colorSlot: ColorSlot,
    val ticked: Boolean,
    val flows: AccountFlows,
)

/** A heading on the ACCOUNT tab. The screen names [type]; this file holds no copy. */
data class AccountCheckGroup(
    val type: AccountType,
    val rows: List<AccountCheckRow>,
)

/**
 * A header ring: the filtered figure and its share of the unfiltered one.
 *
 * [percent] is null when the unfiltered figure is zero, so a month with no income shows
 * an empty ring and no percentage rather than "0%".
 */
data class RingFigure(
    val amount: Money,
    val percent: Int?,
)

data class FilterSheetUiState(
    val range: DateRange,
    val tab: FilterTab = FilterTab.INCOME,
    val income: RingFigure = RingFigure(Money.ZERO, null),
    val expense: RingFigure = RingFigure(Money.ZERO, null),
    /** Filtered income less filtered expense. */
    val total: Money = Money.ZERO,
    val allIncome: Boolean = true,
    val incomeRows: List<CheckRow> = emptyList(),
    val allExpense: Boolean = true,
    val expenseRows: List<CheckRow> = emptyList(),
    val allAccounts: Boolean = true,
    val accountGroups: List<AccountCheckGroup> = emptyList(),
    val loading: Boolean = true,
)

/** Cash, Bank, Investment, Liability: the order the ACCOUNT tab lists its headings in. */
private val GROUP_ORDER = listOf(
    AccountType.CASH,
    AccountType.BANK,
    AccountType.INVESTMENT,
    AccountType.LIABILITY,
)

private fun Set<Long>?.ticks(id: Long): Boolean = this == null || id in this

/**
 * One item ticked or unticked.
 *
 * Unticking while "All" is ticked keeps every other listed item ticked. Ticking the last
 * unticked item turns the selection back into "All" (null), which is what re-includes
 * archived items and anything created later. That normalisation happens only here, on a
 * tick, never passively: a filter of Makan & Minum alone must not become "All" just
 * because Makan & Minum is the only category left listed.
 */
fun toggledItem(selection: Set<Long>?, id: Long, listed: List<Long>): Set<Long>? {
    if (selection.ticks(id)) return (selection ?: listed.toSet()) - id
    val next = selection.orEmpty() + id
    return if (next.containsAll(listed)) null else next
}

/** The "All" row: ticking it ticks everything, unticking it unticks everything. */
fun allToggled(selection: Set<Long>?): Set<Long>? = if (selection == null) emptySet() else null

/** [toggledItem] applied to whichever of the three selections [tab] edits. */
fun TransactionFilter.toggled(tab: FilterTab, id: Long, listed: List<Long>): TransactionFilter =
    when (tab) {
        FilterTab.INCOME -> copy(incomeCategoryIds = toggledItem(incomeCategoryIds, id, listed))
        FilterTab.EXPENSES -> copy(expenseCategoryIds = toggledItem(expenseCategoryIds, id, listed))
        FilterTab.ACCOUNT -> copy(accountIds = toggledItem(accountIds, id, listed))
    }

fun TransactionFilter.toggledAll(tab: FilterTab): TransactionFilter = when (tab) {
    FilterTab.INCOME -> copy(incomeCategoryIds = allToggled(incomeCategoryIds))
    FilterTab.EXPENSES -> copy(expenseCategoryIds = allToggled(expenseCategoryIds))
    FilterTab.ACCOUNT -> copy(accountIds = allToggled(accountIds))
}

fun ringFigure(filtered: Money, whole: Money): RingFigure =
    RingFigure(amount = filtered, percent = sharePercent(filtered, whole))

/**
 * Accounts under their type headings, in [GROUP_ORDER], each group in the order [accounts]
 * arrived in. A group with no account gets no heading, which is how Liability stays hidden
 * until a liability account exists.
 */
fun groupAccounts(
    accounts: List<Account>,
    flows: List<AccountFlows>,
    selection: Set<Long>?,
): List<AccountCheckGroup> {
    val flowsById = flows.associateBy { it.accountId }
    return GROUP_ORDER.mapNotNull { type ->
        val rows = accounts.filter { it.type == type }.map { account ->
            AccountCheckRow(
                id = account.id,
                name = account.name,
                colorSlot = account.colorSlot,
                ticked = selection.ticks(account.id),
                flows = flowsById[account.id] ?: AccountFlows.none(account.id),
            )
        }
        rows.takeIf { it.isNotEmpty() }?.let { AccountCheckGroup(type, it) }
    }
}

private fun List<Category>.toCheckRows(selection: Set<Long>?): List<CheckRow> =
    map { CheckRow(it.id, it.name, it.colorSlot, selection.ticks(it.id)) }

/**
 * The whole sheet. [filtered] and [whole] are the period's totals with and without the
 * filter; the categories and accounts are the active (non-archived) ones, in the user's
 * order.
 */
fun assembleFilterSheet(
    range: DateRange,
    tab: FilterTab,
    filter: TransactionFilter,
    filtered: PeriodTotals,
    whole: PeriodTotals,
    flows: List<AccountFlows>,
    incomeCategories: List<Category>,
    expenseCategories: List<Category>,
    accounts: List<Account>,
): FilterSheetUiState = FilterSheetUiState(
    range = range,
    tab = tab,
    income = ringFigure(filtered.income, whole.income),
    expense = ringFigure(filtered.expense, whole.expense),
    total = filtered.net,
    allIncome = filter.incomeCategoryIds == null,
    incomeRows = incomeCategories.toCheckRows(filter.incomeCategoryIds),
    allExpense = filter.expenseCategoryIds == null,
    expenseRows = expenseCategories.toCheckRows(filter.expenseCategoryIds),
    allAccounts = filter.accountIds == null,
    accountGroups = groupAccounts(accounts, flows, filter.accountIds),
    loading = false,
)

/**
 * Names what an active filter selects, for the "Filtered" bar: "2 accounts · 1 expense
 * category". Accounts first, then expenses, then income; a tab at "All" says nothing, and
 * one with nothing ticked says so in words. Empty when the filter is inactive.
 *
 * Copy in an assembly file, against the habit elsewhere, because the phrase is the rule
 * under test: the spec's scenarios quote it. The app ships in English only.
 */
fun filterSummary(filter: TransactionFilter): String = listOfNotNull(
    filter.accountIds?.let { counted(it.size, "account", "accounts", "no accounts") },
    filter.expenseCategoryIds?.let {
        counted(it.size, "expense category", "expense categories", "no expenses")
    },
    filter.incomeCategoryIds?.let {
        counted(it.size, "income category", "income categories", "no income")
    },
).joinToString(" · ")

private fun counted(count: Int, one: String, many: String, none: String): String = when (count) {
    0 -> none
    1 -> "1 $one"
    else -> "$count $many"
}
