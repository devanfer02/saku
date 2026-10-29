package com.dvnnfrr.saku.ui.filter

import com.dvnnfrr.saku.domain.Account
import com.dvnnfrr.saku.domain.AccountFlows
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.Category
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.PeriodTotals
import com.dvnnfrr.saku.domain.TransactionFilter
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The scenarios of `specs/transaction-filter/spec.md` that the sheet's assembly decides. */
class FilterSheetAssemblyTest {

    private val october = DateRange.ofMonth(LocalDate(2026, 10, 1))

    private fun account(id: Long, name: String, type: AccountType) = Account(
        id = id,
        name = name,
        type = type,
        colorSlot = ColorSlot((id % ColorSlot.COUNT).toInt()),
        sortOrder = id.toInt(),
        archived = false,
        openingBalance = Money.ZERO,
        openingAsOf = Instant.fromEpochSeconds(0),
        lastReconciled = null,
        balance = Money.ZERO,
    )

    private fun category(id: Long, name: String, kind: CategoryKind = CategoryKind.EXPENSE) =
        Category(id, name, kind, ColorSlot((id % ColorSlot.COUNT).toInt()), id.toInt(), archived = false)

    private val bca = account(1, "BCA", AccountType.BANK)
    private val jago = account(2, "Jago", AccountType.BANK)
    private val cash = account(3, "Cash", AccountType.CASH)
    private val rdpu = account(4, "RDPU SucorInvest", AccountType.INVESTMENT)

    private val makan = category(10, "Makan & Minum")
    private val jajan = category(11, "Jajan & Hiburan")
    private val pemberian = category(12, "Pemberian")
    private val gaji = category(20, "Gaji", CategoryKind.INCOME)
    private val expenses = listOf(makan, jajan, pemberian)
    private val expenseIds = expenses.map { it.id }

    private fun sheet(
        filter: TransactionFilter = TransactionFilter.ALL,
        filtered: PeriodTotals = PeriodTotals.EMPTY,
        whole: PeriodTotals = PeriodTotals.EMPTY,
        flows: List<AccountFlows> = emptyList(),
        expenseCategories: List<Category> = expenses,
        accounts: List<Account> = listOf(bca, jago, cash, rdpu),
    ) = assembleFilterSheet(
        range = october,
        tab = FilterTab.INCOME,
        filter = filter,
        filtered = filtered,
        whole = whole,
        flows = flows,
        incomeCategories = listOf(gaji),
        expenseCategories = expenseCategories,
        accounts = accounts,
    )

    // Requirement: checkbox lists with an All row.

    @Test
    fun `accounts group under Cash, Bank and Investment with no Liability heading`() {
        val groups = sheet().accountGroups

        assertEquals(
            listOf(AccountType.CASH, AccountType.BANK, AccountType.INVESTMENT),
            groups.map { it.type },
        )
        assertEquals(listOf("Cash"), groups[0].rows.map { it.name })
        assertEquals(listOf("BCA", "Jago"), groups[1].rows.map { it.name })
        assertEquals(listOf("RDPU SucorInvest"), groups[2].rows.map { it.name })
    }

    @Test
    fun `a liability account brings its heading, last`() {
        val card = account(5, "Kartu Kredit", AccountType.LIABILITY)

        val groups = sheet(accounts = listOf(card, bca)).accountGroups

        assertEquals(listOf(AccountType.BANK, AccountType.LIABILITY), groups.map { it.type })
    }

    @Test
    fun `with no filter every row and every All row is ticked`() {
        val state = sheet()

        assertTrue(state.allIncome && state.allExpense && state.allAccounts)
        assertTrue(state.incomeRows.all { it.ticked })
        assertTrue(state.expenseRows.all { it.ticked })
        assertTrue(state.accountGroups.flatMap { it.rows }.all { it.ticked })
    }

    // Requirement: All row semantics.

    @Test
    fun `unticking All then ticking one category narrows to that category`() {
        val none = TransactionFilter.ALL.toggledAll(FilterTab.EXPENSES)
        assertEquals(emptySet<Long>(), none.expenseCategoryIds)

        val narrowed = none.toggled(FilterTab.EXPENSES, makan.id, expenseIds)
        assertEquals(setOf(makan.id), narrowed.expenseCategoryIds)

        val rows = sheet(filter = narrowed).expenseRows
        assertEquals(listOf(true, false, false), rows.map { it.ticked })
        assertFalse(sheet(filter = narrowed).allExpense)
    }

    @Test
    fun `unticking one item under All keeps the others ticked`() {
        val filter = TransactionFilter.ALL.toggled(FilterTab.EXPENSES, pemberian.id, expenseIds)

        assertEquals(setOf(makan.id, jajan.id), filter.expenseCategoryIds)
        assertFalse(sheet(filter = filter).allExpense)
    }

    @Test
    fun `re-ticking the last unticked item returns to All`() {
        val without = TransactionFilter.ALL.toggled(FilterTab.EXPENSES, pemberian.id, expenseIds)

        val again = without.toggled(FilterTab.EXPENSES, pemberian.id, expenseIds)

        // Null, not the three ids: that is what re-includes a category archived earlier.
        assertNull(again.expenseCategoryIds)
        assertTrue(sheet(filter = again).allExpense)
    }

    @Test
    fun `ticking All restores everything`() {
        val narrowed = TransactionFilter(expenseCategoryIds = setOf(makan.id))

        assertNull(narrowed.toggledAll(FilterTab.EXPENSES).expenseCategoryIds)
    }

    @Test
    fun `a category created while filtered starts unticked`() {
        val filter = TransactionFilter(expenseCategoryIds = setOf(makan.id))
        val fresh = category(13, "Kopi")

        val rows = sheet(filter = filter, expenseCategories = expenses + fresh).expenseRows

        assertFalse(rows.single { it.id == fresh.id }.ticked)
    }

    @Test
    fun `the account tab edits only the account selection`() {
        val filter = TransactionFilter.ALL.toggled(
            FilterTab.ACCOUNT,
            bca.id,
            listOf(bca.id, jago.id, cash.id, rdpu.id),
        )

        assertEquals(setOf(jago.id, cash.id, rdpu.id), filter.accountIds)
        assertNull(filter.expenseCategoryIds)
        assertNull(filter.incomeCategoryIds)
    }

    // Requirement: sheet header shows the filtered share.

    @Test
    fun `the rings show the filtered share of the whole month`() {
        val state = sheet(
            filtered = PeriodTotals(Money(8_000_000), Money(1_500_000)),
            whole = PeriodTotals(Money(10_000_000), Money(6_000_000)),
        )

        assertEquals(RingFigure(Money(8_000_000), 80), state.income)
        assertEquals(RingFigure(Money(1_500_000), 25), state.expense)
        assertEquals(Money(6_500_000), state.total)
    }

    @Test
    fun `a month with no income shows no percentage`() {
        val state = sheet(whole = PeriodTotals(Money.ZERO, Money(6_000_000)))

        assertEquals(RingFigure(Money.ZERO, null), state.income)
    }

    // Requirement: per-account in and out totals.

    @Test
    fun `each account row carries its own four figures`() {
        val bcaFlows = AccountFlows(bca.id, Money(8_000_000), Money(2_400_000), Money(300_000), Money(500_000))

        val rows = sheet(flows = listOf(bcaFlows)).accountGroups.flatMap { it.rows }.associateBy { it.id }

        assertEquals(bcaFlows, rows.getValue(bca.id).flows)
        assertEquals(AccountFlows.none(jago.id), rows.getValue(jago.id).flows)
    }

    // Requirement: an active filter is always visible.

    @Test
    fun `the summary names what is selected`() {
        assertEquals("", filterSummary(TransactionFilter.ALL))
        assertEquals("2 accounts", filterSummary(TransactionFilter(accountIds = setOf(bca.id, jago.id))))
        assertEquals(
            "2 accounts · 1 expense category",
            filterSummary(
                TransactionFilter(accountIds = setOf(bca.id, jago.id), expenseCategoryIds = setOf(makan.id)),
            ),
        )
        assertEquals(
            "1 account · 3 expense categories · 1 income category",
            filterSummary(
                TransactionFilter(
                    accountIds = setOf(cash.id),
                    expenseCategoryIds = expenseIds.toSet(),
                    incomeCategoryIds = setOf(gaji.id),
                ),
            ),
        )
    }

    @Test
    fun `an empty selection is named in words`() {
        assertEquals("no income", filterSummary(TransactionFilter(incomeCategoryIds = emptySet())))
        assertEquals("no accounts", filterSummary(TransactionFilter(accountIds = emptySet())))
    }
}
