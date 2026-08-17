package com.dvnnfrr.saku.ui.transactions

import com.dvnnfrr.saku.domain.Account
import com.dvnnfrr.saku.domain.AccountType
import com.dvnnfrr.saku.domain.Category
import com.dvnnfrr.saku.domain.CategoryKind
import com.dvnnfrr.saku.domain.ColorSlot
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.Subcategory
import com.dvnnfrr.saku.domain.Transaction
import com.dvnnfrr.saku.domain.TxnType
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers every scenario in specs/transaction-list/spec.md that is not pixels. */
class TransactionListAssemblyTest {

    // WIB. Every date in this file is a Jakarta date, which is the point: a
    // transaction at 23:00 UTC on the 13th is the 14th to the user.
    private val zone = TimeZone.of("Asia/Jakarta")
    private val august = DateRange.ofMonth(LocalDate(2026, 8, 1))

    private val bca = Account(
        id = 1, name = "BCA", type = AccountType.BANK, colorSlot = ColorSlot(0),
        sortOrder = 0, archived = false, openingBalance = Money.ZERO,
        openingAsOf = Instant.parse("2026-08-01T00:00:00Z"), lastReconciled = null,
        balance = Money.ZERO,
    )
    private val rdpu = bca.copy(id = 2, name = "RDPU", type = AccountType.INVESTMENT, colorSlot = ColorSlot(1))

    private val makan = Category(3, "Makan & Minum", CategoryKind.EXPENSE, ColorSlot(2), 0, false)
    private val gaji = Category(4, "Gaji", CategoryKind.INCOME, ColorSlot(3), 0, false)
    private val restoran = Subcategory(5, categoryId = 3, name = "Restoran", sortOrder = 0, archived = false)

    private val index = DisplayIndex(
        accounts = listOf(bca, rdpu),
        categories = listOf(makan, gaji),
        subcategories = listOf(restoran),
    )

    private var nextId = 100L

    private fun txn(
        day: Int,
        hour: Int = 12,
        type: TxnType = TxnType.EXPENSE,
        amount: Long,
        accountId: Long = 1,
        toAccountId: Long? = null,
        categoryId: Long? = 3,
        subcategoryId: Long? = null,
        note: String? = null,
    ): Transaction {
        // Built as a local Jakarta wall clock and converted, so the test says what it
        // means rather than hand-rolling the UTC offset.
        val at = LocalDateTime(2026, 8, day, hour, 0).toInstant(zone)
        return Transaction(
            id = nextId++, occurredAt = at, type = type, amount = Money(amount),
            accountId = accountId, toAccountId = toAccountId, categoryId = categoryId,
            subcategoryId = subcategoryId, note = note, createdAt = at,
        )
    }

    // --- Header totals -----------------------------------------------------

    @Test
    fun `a transfer is excluded from the month header`() {
        val txns = listOf(
            txn(day = 14, amount = 5_061_400),
            txn(day = 15, type = TxnType.TRANSFER, amount = 500_000, toAccountId = 2, categoryId = null),
        )

        val totals = txns.periodTotals()

        assertEquals(Money.ZERO, totals.income)
        assertEquals(Money(5_061_400), totals.expense)
        assertEquals(Money(-5_061_400), totals.net)
    }

    @Test
    fun `an adjustment is excluded from the month header`() {
        val txns = listOf(
            txn(day = 14, amount = 170_000),
            txn(day = 14, type = TxnType.ADJUSTMENT, amount = 53_500, categoryId = null),
        )

        assertEquals(Money(170_000), txns.periodTotals().expense)
    }

    @Test
    fun `income and expense are both counted`() {
        val txns = listOf(
            txn(day = 1, type = TxnType.INCOME, amount = 8_000_000, categoryId = 4),
            txn(day = 2, amount = 170_000),
        )

        val totals = txns.periodTotals()

        assertEquals(Money(8_000_000), totals.income)
        assertEquals(Money(170_000), totals.expense)
        assertEquals(Money(7_830_000), totals.net)
    }

    // --- Day sections ------------------------------------------------------

    @Test
    fun `a day mixing an expense and a transfer subtotals only the expense`() {
        val txns = listOf(
            txn(day = 14, amount = 170_000),
            txn(day = 14, type = TxnType.TRANSFER, amount = 500_000, toAccountId = 2, categoryId = null),
        )

        val sections = txns.toDaySections(zone, index)

        assertEquals(1, sections.size)
        val day = sections.single()
        assertEquals(Money(170_000), day.expense)
        assertEquals(Money.ZERO, day.income)
        // Both rows are still listed. The transfer is history, it just is not spending.
        assertEquals(2, day.rows.size)
    }

    @Test
    fun `days with no transactions get no section`() {
        val txns = listOf(txn(day = 14, amount = 170_000), txn(day = 20, amount = 45_000))

        val sections = txns.toDaySections(zone, index)

        assertEquals(2, sections.size)
        assertEquals(listOf(LocalDate(2026, 8, 20), LocalDate(2026, 8, 14)), sections.map { it.date })
    }

    @Test
    fun `days run newest first and rows within a day run newest first`() {
        val early = txn(day = 14, hour = 8, amount = 10_000)
        val late = txn(day = 14, hour = 19, amount = 20_000)
        val other = txn(day = 3, amount = 30_000)

        val sections = listOf(early, other, late).toDaySections(zone, index)

        assertEquals(listOf(LocalDate(2026, 8, 14), LocalDate(2026, 8, 3)), sections.map { it.date })
        assertEquals(listOf(late.id, early.id), sections.first().rows.map { it.id })
    }

    @Test
    fun `a local date late in the day is not pulled back by the UTC offset`() {
        // 23:00 in Jakarta on the 14th is 16:00 UTC on the 14th; grouping in UTC
        // would be fine here, but 06:00 local on the 15th is 23:00 UTC on the 14th
        // and would land on the wrong day.
        val earlyMorning = txn(day = 15, hour = 6, amount = 25_000)

        val sections = listOf(earlyMorning).toDaySections(zone, index)

        assertEquals(LocalDate(2026, 8, 15), sections.single().date)
    }

    // --- Rows --------------------------------------------------------------

    @Test
    fun `a row carries its category, subcategory, note and account swatch`() {
        val row = txn(day = 14, amount = 170_000, subcategoryId = 5, note = "Padang")
            .toRowUi(index)

        assertEquals("Makan & Minum", row.categoryName)
        assertEquals("Restoran", row.subcategoryName)
        assertEquals("Padang", row.note)
        assertEquals("BCA", row.account.name)
        assertEquals(ColorSlot(0), row.account.colorSlot)
        assertNull(row.toAccount)
    }

    @Test
    fun `a transfer row carries both accounts`() {
        val row = txn(
            day = 15, type = TxnType.TRANSFER, amount = 500_000,
            accountId = 1, toAccountId = 2, categoryId = null,
        ).toRowUi(index)

        assertEquals("BCA", row.account.name)
        assertEquals("RDPU", row.toAccount?.name)
        assertEquals(ColorSlot(1), row.toAccount?.colorSlot)
        assertEquals(TxnType.TRANSFER, row.type)
    }

    @Test
    fun `a blank note is treated as no note`() {
        assertNull(txn(day = 14, amount = 1_000, note = "   ").toRowUi(index).note)
    }

    @Test
    fun `an archived subcategory still resolves its name`() {
        val archived = restoran.copy(archived = true)
        val withArchived = DisplayIndex(listOf(bca), listOf(makan), listOf(archived))

        val row = txn(day = 14, amount = 1_000, subcategoryId = 5).toRowUi(withArchived)

        // Archiving does not rewrite history, so the label has to survive it.
        assertEquals("Restoran", row.subcategoryName)
    }

    // --- Calendar ----------------------------------------------------------

    @Test
    fun `a month has one cell per day and August starts on a Saturday`() {
        val calendar = emptyList<Transaction>().toCalendarMonth(august, zone)

        assertEquals(31, calendar.cells.size)
        // 1 Aug 2026 is a Saturday: five blanks for Mon–Fri.
        assertEquals(5, calendar.leadingBlanks)
    }

    @Test
    fun `unlogged days render empty rather than zero`() {
        val txns = listOf(txn(day = 21, amount = 45_000))

        val calendar = txns.toCalendarMonth(august, zone)

        val gap = calendar.cells.filter { it.date.dayOfMonth in 1..20 }
        assertEquals(20, gap.size)
        assertTrue(gap.all { it.net == null })
        assertTrue(gap.none { it.hasTransactions })
    }

    @Test
    fun `a day holding only a transfer nets zero but is not empty`() {
        val txns = listOf(
            txn(day = 15, type = TxnType.TRANSFER, amount = 500_000, toAccountId = 2, categoryId = null),
        )

        val cell = txns.toCalendarMonth(august, zone).cells.single { it.date.dayOfMonth == 15 }

        // Deriving emptiness from the net would have reported this day as unlogged.
        assertEquals(Money.ZERO, cell.net)
        assertTrue(cell.hasTransactions)
    }

    @Test
    fun `a cell nets income against expense`() {
        val txns = listOf(
            txn(day = 10, type = TxnType.INCOME, amount = 1_000_000, categoryId = 4),
            txn(day = 10, amount = 250_000),
        )

        val cell = txns.toCalendarMonth(august, zone).cells.single { it.date.dayOfMonth == 10 }

        assertEquals(Money(750_000), cell.net)
    }

    @Test
    fun `February in a leap year has 29 cells`() {
        val february = DateRange.ofMonth(LocalDate(2028, 2, 1))

        val calendar = emptyList<Transaction>().toCalendarMonth(february, zone)

        assertEquals(29, calendar.cells.size)
        assertFalse(calendar.cells.any { it.hasTransactions })
    }
}
