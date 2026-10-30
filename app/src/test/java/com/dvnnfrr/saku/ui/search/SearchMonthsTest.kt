package com.dvnnfrr.saku.ui.search

import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.Transaction
import com.dvnnfrr.saku.domain.TransactionFilter
import com.dvnnfrr.saku.domain.TxnType
import com.dvnnfrr.saku.ui.transactions.DisplayIndex
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The scenarios of `search-by-month/spec.md`, through `assembleSearch` where it decides. */
class SearchMonthsTest {

    private val zone = TimeZone.of("Asia/Jakarta")
    private val index = DisplayIndex(emptyList(), emptyList(), emptyList())
    private var nextId = 1L

    private fun txn(
        note: String,
        amount: Long,
        at: LocalDateTime,
        type: TxnType = TxnType.EXPENSE,
        accountId: Long = 1,
    ): Transaction {
        val instant = at.toInstant(zone)
        return Transaction(
            id = nextId++,
            occurredAt = instant,
            type = type,
            amount = Money(amount),
            accountId = accountId,
            toAccountId = if (type == TxnType.TRANSFER) 2 else null,
            categoryId = if (type.requiresCategory) 1 else null,
            note = note,
            createdAt = instant,
        )
    }

    private fun day(month: Int, day: Int, year: Int = 2026) = LocalDateTime(year, month, day, 12, 0)

    private fun month(month: Int, year: Int = 2026) = DateRange.ofMonth(LocalDate(year, month, 1))

    private fun search(query: String, txns: List<Transaction>) =
        assembleSearch(query, TransactionFilter.ALL, txns.searchable(), null, zone, index)

    @Test
    fun `a month with no match inside the span is listed as empty`() {
        val months = search(
            "arisan",
            listOf(
                txn("Uang Arisan", 500_000, day(8, 3)),
                txn("Uang Arisan", 750_000, day(10, 5)),
            ),
        ).months

        assertEquals(listOf(month(10), month(9), month(8)), months.map { it.range })
        assertEquals(listOf(750_000L, 0L, 500_000L), months.map { it.expense.rupiah })
        assertEquals(listOf(1, 0, 1), months.map { it.count })
    }

    @Test
    fun `the span crosses a year end and stops at the oldest match`() {
        val months = search(
            "arisan",
            listOf(
                txn("arisan", 100_000, day(11, 30, 2025)),
                txn("arisan", 100_000, day(1, 2, 2026)),
            ),
        ).months

        assertEquals(listOf(month(1), month(12, 2025), month(11, 2025)), months.map { it.range })
    }

    @Test
    fun `transfers are counted but not summed`() {
        val october = search(
            "arisan",
            listOf(
                txn("arisan", 100_000, day(10, 1)),
                txn("arisan", 1_000_000, day(10, 2), type = TxnType.TRANSFER),
                txn("arisan", 50_000, day(9, 1)),
            ),
        ).months.first()

        assertEquals(Money(100_000), october.expense)
        assertEquals(Money.ZERO, october.income)
        assertEquals(2, october.count)
    }

    @Test
    fun `only the rows that passed the filter are folded`() {
        val card = listOf(txn("arisan", 200_000, day(10, 1)), txn("arisan", 300_000, day(8, 1)))
        val cash = listOf(txn("arisan", 900_000, day(9, 1), accountId = 3))

        val state = assembleSearch(
            query = "arisan",
            filter = TransactionFilter(accountIds = setOf(1)),
            filtered = card.searchable(),
            unfiltered = (card + cash).searchable(),
            zone = zone,
            index = index,
        )

        val september = state.months.single { it.range == month(9) }
        assertEquals(0, september.count)
        assertEquals(Money.ZERO, september.expense)
    }

    @Test
    fun `matches in one month give no breakdown`() {
        val months = search(
            "arisan",
            listOf(txn("arisan", 1, day(10, 1)), txn("arisan", 2, day(10, 30))),
        ).months

        assertTrue(months.isEmpty())
    }

    @Test
    fun `bars run against the largest month`() {
        val months = search(
            "arisan",
            listOf(
                txn("arisan", 400_000, day(10, 1)),
                txn("arisan", 1_000_000, day(9, 1)),
                txn("arisan", 250_000, day(8, 1)),
            ),
        ).months

        assertEquals(listOf(400, 1_000, 250), months.map { it.barPermille })
        assertTrue(months.all { it.side == TxnType.EXPENSE })
    }

    @Test
    fun `an income search shows money in`() {
        val months = search(
            "gaji",
            listOf(
                txn("gaji", 8_000_000, day(10, 25), type = TxnType.INCOME),
                txn("gaji", 16_000_000, day(9, 25), type = TxnType.INCOME),
            ),
        ).months

        assertTrue(months.all { it.side == TxnType.INCOME })
        assertEquals(listOf(Money(8_000_000), Money(16_000_000)), months.map { it.value })
        assertEquals(listOf(500, 1_000), months.map { it.barPermille })
        assertEquals(Money.ZERO, months.first().otherSide)
    }

    @Test
    fun `the other side is carried for the supporting line`() {
        val october = search(
            "arisan",
            listOf(
                txn("arisan", 300_000, day(10, 1)),
                txn("arisan", 50_000, day(10, 2), type = TxnType.INCOME),
                txn("arisan", 300_000, day(9, 1)),
            ),
        ).months.first()

        assertEquals(TxnType.EXPENSE, october.side)
        assertEquals(Money(300_000), october.value)
        assertEquals(Money(50_000), october.otherSide)
    }

    @Test
    fun `the scroll index lands on the month's newest day header`() {
        val state = search(
            "arisan",
            listOf(
                txn("arisan", 1, day(10, 20)),
                txn("arisan", 2, day(10, 20)),
                txn("arisan", 3, day(10, 3)),
                txn("arisan", 4, day(8, 14)),
            ),
        )
        val august = state.months.single { it.range == month(8) }

        // Leading: summary and heading. Months: Oct, Sep, Aug. Days: 20 Oct (header and
        // two rows), 3 Oct (header and one row), then 14 Aug's header.
        val leading = 2
        assertEquals(leading + 3 + 5, searchScrollIndex(leading, state.months, state.days, august))
        assertEquals(leading + 3, searchScrollIndex(leading, state.months, state.days, state.months.first()))
    }

    @Test
    fun `an empty month has no scroll target`() {
        val state = search("arisan", listOf(txn("arisan", 1, day(10, 1)), txn("arisan", 1, day(8, 1))))
        val september = state.months.single { it.range == month(9) }

        assertNull(searchScrollIndex(2, state.months, state.days, september))
    }
}
