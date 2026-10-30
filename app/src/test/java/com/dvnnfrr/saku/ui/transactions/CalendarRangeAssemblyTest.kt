package com.dvnnfrr.saku.ui.transactions

import com.dvnnfrr.saku.domain.Account
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.Category
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.Transaction
import com.dvnnfrr.saku.domain.TxnType
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarRangeAssemblyTest {

    private val zone = TimeZone.of("Asia/Jakarta")

    private val bca = Account(
        id = 1, name = "BCA", type = AccountType.BANK, colorSlot = ColorSlot(0),
        sortOrder = 0, archived = false, openingBalance = Money.ZERO,
        openingAsOf = Instant.parse("2026-08-01T00:00:00Z"), lastReconciled = null,
        balance = Money.ZERO,
    )
    private val rdpu = bca.copy(id = 2, name = "RDPU", type = AccountType.INVESTMENT, colorSlot = ColorSlot(1))
    private val makan = Category(3, "Makan & Minum", CategoryKind.EXPENSE, ColorSlot(2), 0, false)
    private val gaji = Category(4, "Gaji", CategoryKind.INCOME, ColorSlot(3), 0, false)

    private val index = DisplayIndex(listOf(bca, rdpu), listOf(makan, gaji))

    private var nextId = 100L

    private fun txn(
        day: Int,
        hour: Int = 12,
        type: TxnType = TxnType.EXPENSE,
        amount: Long,
        toAccountId: Long? = null,
        categoryId: Long? = 3,
    ): Transaction {
        val at = LocalDateTime(2026, 8, day, hour, 0).toInstant(zone)
        return Transaction(
            id = nextId++, occurredAt = at, type = type, amount = Money(amount),
            accountId = 1, toAccountId = toAccountId, categoryId = categoryId,
            subcategoryId = null, note = null, createdAt = at,
        )
    }

    private fun aug(day: Int) = LocalDate(2026, 8, day)

    // --- Tap sequence --------------------------------------------------------

    @Test
    fun `a first tap picks one day`() {
        val picked = null.tap(aug(12))

        assertEquals(DateRange.custom(aug(12), aug(12)), picked?.range)
    }

    @Test
    fun `tapping the only picked day again clears it`() {
        assertNull(CalendarSelection(aug(12)).tap(aug(12)))
    }

    @Test
    fun `a second tap closes the range in either order`() {
        val forwards = CalendarSelection(aug(12)).tap(aug(18))
        val backwards = CalendarSelection(aug(18)).tap(aug(12))

        assertEquals(DateRange.custom(aug(12), aug(18)), forwards?.range)
        assertEquals(DateRange.custom(aug(12), aug(18)), backwards?.range)
    }

    @Test
    fun `a third tap starts a new selection`() {
        val picked = CalendarSelection(aug(12), aug(18)).tap(aug(14))

        assertEquals(CalendarSelection(aug(14)), picked)
    }

    @Test
    fun `tapping an end of a closed range starts over on it rather than clearing`() {
        assertEquals(CalendarSelection(aug(12)), CalendarSelection(aug(12), aug(18)).tap(aug(12)))
    }

    @Test
    fun `a drag that never leaves its cell is one day, so the next tap extends it`() {
        val swept = CalendarSelection.swept(aug(9), aug(9))

        assertEquals(CalendarSelection(aug(9)), swept)
        assertEquals(DateRange.custom(aug(9), aug(20)), swept.tap(aug(20))?.range)
    }

    @Test
    fun `a backwards drag still orders its range`() {
        assertEquals(DateRange.custom(aug(3), aug(9)), CalendarSelection.swept(aug(9), aug(3)).range)
    }

    // --- Fold ----------------------------------------------------------------

    @Test
    fun `only rows inside the range are folded`() {
        val txns = listOf(
            txn(day = 11, amount = 10_000),
            txn(day = 12, amount = 20_000),
            txn(day = 14, amount = 30_000),
            txn(day = 15, amount = 40_000),
        )

        val summary = txns.toCalendarRangeSummary(DateRange.custom(aug(12), aug(14)), zone, index)

        assertEquals(Money(50_000), summary.totals.expense)
        assertEquals(listOf(aug(14), aug(12)), summary.days.map { it.date })
    }

    @Test
    fun `a single day folds that day alone`() {
        val txns = listOf(
            txn(day = 12, amount = 20_000),
            txn(day = 12, type = TxnType.INCOME, amount = 100_000, categoryId = 4),
            txn(day = 13, amount = 99_000),
        )

        val summary = txns.toCalendarRangeSummary(DateRange.custom(aug(12), aug(12)), zone, index)

        assertEquals(listOf(aug(12)), summary.days.map { it.date })
        assertEquals(Money(100_000), summary.totals.income)
        assertEquals(Money(20_000), summary.totals.expense)
        assertEquals(Money(80_000), summary.totals.net)
    }

    @Test
    fun `a range with no rows folds to zero totals and no days`() {
        val txns = listOf(txn(day = 1, amount = 20_000))

        val summary = txns.toCalendarRangeSummary(DateRange.custom(aug(20), aug(25)), zone, index)

        assertEquals(Money.ZERO, summary.totals.income)
        assertEquals(Money.ZERO, summary.totals.expense)
        assertTrue(summary.days.isEmpty())
    }

    @Test
    fun `a transfer is listed but counts toward neither In nor Out`() {
        val txns = listOf(
            txn(day = 12, amount = 20_000),
            txn(day = 13, type = TxnType.TRANSFER, amount = 500_000, toAccountId = 2, categoryId = null),
        )

        val summary = txns.toCalendarRangeSummary(DateRange.custom(aug(12), aug(13)), zone, index)

        assertEquals(Money.ZERO, summary.totals.income)
        assertEquals(Money(20_000), summary.totals.expense)
        assertEquals(txns.periodTotals(), summary.totals)
        val transfer = summary.days.first { it.date == aug(13) }
        assertEquals(listOf(TxnType.TRANSFER), transfer.rows.map { it.type })
        assertEquals("RDPU", transfer.rows.single().toAccount?.name)
    }

    @Test
    fun `a row is dated by the local day, not the UTC one`() {
        // 06:00 WIB on the 13th is 23:00 UTC on the 12th.
        val txns = listOf(txn(day = 13, hour = 6, amount = 20_000))

        val summary = txns.toCalendarRangeSummary(DateRange.custom(aug(13), aug(13)), zone, index)

        assertEquals(Money(20_000), summary.totals.expense)
    }

    @Test
    fun `days and rows come in Daily's order, newest first`() {
        val txns = listOf(
            txn(day = 12, hour = 8, amount = 1_000),
            txn(day = 12, hour = 20, amount = 2_000),
            txn(day = 14, amount = 3_000),
        )

        val summary = txns.toCalendarRangeSummary(DateRange.custom(aug(12), aug(14)), zone, index)

        assertEquals(txns.toDaySections(zone, index), summary.days)
    }
}
