package com.dvnnfrr.saku.ui.transactions

import com.dvnnfrr.saku.domain.DailyTotals
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.PeriodTotals
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The scenarios of `specs/transaction-monthly-view/spec.md` that the assembly decides. */
class MonthlyAssemblyTest {

    private val today = LocalDate(2026, 10, 30)

    private fun day(y: Int, m: Int, d: Int, income: Long = 0, expense: Long = 0) =
        DailyTotals(LocalDate(y, m, d), Money(income), Money(expense))

    private fun year(
        year: Int,
        days: List<DailyTotals> = emptyList(),
        expanded: Month? = null,
    ) = assembleMonthlyYear(year, days, today, expanded)

    @Test
    fun `the query covers the year widened to whole weeks`() {
        val range = monthlyQueryRange(2026)

        // 1 Jan 2026 is a Thursday, 31 Dec 2026 a Thursday.
        assertEquals(LocalDate(2025, 12, 29), range.start)
        assertEquals(LocalDate(2027, 1, 3), range.endInclusive)
    }

    @Test
    fun `the current year lists started months newest first`() {
        val months = year(2026).months

        assertEquals(10, months.size)
        assertEquals(Month.OCTOBER, months.first().month)
        assertEquals(Month.JANUARY, months.last().month)
    }

    @Test
    fun `a past year lists all twelve months, December first`() {
        val months = year(2025).months

        assertEquals(12, months.size)
        assertEquals(Month.DECEMBER, months.first().month)
    }

    @Test
    fun `a future year lists nothing`() {
        assertTrue(year(2027).months.isEmpty())
    }

    @Test
    fun `an unlogged month is empty, not zero`() {
        val months = year(2026, listOf(day(2026, 10, 2, expense = 50_000))).months

        assertEquals(PeriodTotals(Money.ZERO, Money(50_000)), months.first().totals)
        assertNull(months.single { it.month == Month.MARCH }.totals)
    }

    @Test
    fun `the year header counts only days inside the year`() {
        val days = listOf(
            day(2025, 12, 30, expense = 10_000),
            day(2026, 3, 1, income = 96_000_000),
            day(2026, 3, 2, expense = 61_000_000),
            day(2027, 1, 2, expense = 7_000),
        )

        val totals = year(2026, days).totals

        assertEquals(Money(96_000_000), totals.income)
        assertEquals(Money(61_000_000), totals.expense)
        assertEquals(Money(35_000_000), totals.net)
    }

    @Test
    fun `October 2026 lists five started weeks with full-span labels`() {
        val october = year(2026, expanded = Month.OCTOBER).months.first()

        assertEquals(
            listOf("26.10 ~ 01.11", "19.10 ~ 25.10", "12.10 ~ 18.10", "05.10 ~ 11.10", "28.09 ~ 04.10"),
            october.weeks.map { it.range.dottedSpanLabel() },
        )
        assertTrue(october.expanded)
    }

    @Test
    fun `the week containing today is the current one`() {
        val weeks = year(2026).months.first().weeks

        assertEquals(listOf(true, false, false, false, false), weeks.map { it.isCurrent })
    }

    @Test
    fun `a week across the month edge counts all seven days under both months`() {
        val days = listOf(day(2026, 9, 28, expense = 100_000), day(2026, 10, 2, expense = 50_000))
        val months = year(2026, days).months

        val underOctober = months.single { it.month == Month.OCTOBER }.weeks.last()
        val underSeptember = months.single { it.month == Month.SEPTEMBER }.weeks.first()

        assertEquals(DateRange.ofWeek(LocalDate(2026, 9, 28)), underOctober.range)
        assertEquals(Money(150_000), underOctober.totals?.expense)
        assertEquals(underOctober.range, underSeptember.range)
        assertEquals(Money(150_000), underSeptember.totals?.expense)
        // A tap opens the week's last day inside the month the row sits under.
        assertEquals(LocalDate(2026, 10, 4), underOctober.openDate)
        assertEquals(LocalDate(2026, 9, 30), underSeptember.openDate)
    }

    @Test
    fun `January's oldest week reaches into December of the year before`() {
        val days = listOf(day(2025, 12, 29, expense = 20_000), day(2026, 1, 3, expense = 5_000))
        val january = year(2026, days).months.last()

        val oldest = january.weeks.last()
        assertEquals("29.12 ~ 04.01", oldest.range.dottedSpanLabel())
        assertEquals(Money(25_000), oldest.totals?.expense)
    }

    @Test
    fun `weeks that have not started are left out`() {
        val weeks = assembleMonthlyYear(2026, emptyList(), LocalDate(2026, 10, 14), Month.OCTOBER)
            .months.first().weeks

        assertEquals(listOf("12.10 ~ 18.10", "05.10 ~ 11.10", "28.09 ~ 04.10"), weeks.map { it.range.dottedSpanLabel() })
    }

    @Test
    fun `only the chosen month is expanded`() {
        val months = year(2026, expanded = Month.AUGUST).months

        assertEquals(listOf(Month.AUGUST), months.filter { it.expanded }.map { it.month })
        assertFalse(year(2026, expanded = null).months.any { it.expanded })
    }

    // Daily's scroll target, for a week opened from Monthly.

    private fun section(d: Int, rows: Int) = DaySection(
        date = LocalDate(2026, 10, d),
        income = Money.ZERO,
        expense = Money.ZERO,
        rows = List(rows) { index ->
            TransactionRowUi(
                id = d * 10L + index,
                type = com.dvnnfrr.saku.domain.TxnType.EXPENSE,
                amount = Money(1),
                categoryName = null,
                subcategoryName = null,
                note = null,
                account = AccountRef(1, "BCA", com.dvnnfrr.saku.domain.ColorSlot.FIRST),
                toAccount = null,
            )
        },
    )

    @Test
    fun `with a pay cycle the year holds the pay months named in it`() {
        val months = monthsOfYear(2026, 30)

        assertEquals(12, months.size)
        assertEquals(LocalDate(2025, 12, 30), months.first().start)
        assertEquals(LocalDate(2026, 12, 29), months.last().endInclusive)
        assertEquals(Month.JANUARY, months.first().majorityMonth().month)
        months.zipWithNext().forEach { (earlier, later) -> assertEquals(earlier.next(), later) }
    }

    @Test
    fun `every start day gives each year a contiguous run of pay months`() {
        for (startDay in 1..31) {
            val years = (2025..2027).map { monthsOfYear(it, startDay) }
            val all = years.flatten()
            all.zipWithNext().forEach { (earlier, later) -> assertEquals("D=$startDay", earlier.next(), later) }
            years.forEachIndexed { index, months ->
                months.forEach { assertEquals("D=$startDay", 2025 + index, it.majorityMonth().year) }
            }
        }
    }

    @Test
    fun `start day 16 can name two pay months after one month`() {
        // 16 Apr to 15 May ties and goes to May; 16 May to 15 Jun is mostly May.
        val names = monthsOfYear(2026, 16).map { it.majorityMonth().month }

        assertEquals(2, names.count { it == Month.MAY })
        assertEquals(0, names.count { it == Month.APRIL })
    }

    @Test
    fun `a pay year is folded and labelled from its pay months`() {
        val days = listOf(
            day(2025, 12, 30, income = 8_000_000),
            day(2026, 10, 29, expense = 50_000),
            day(2026, 10, 30, income = 8_000_000),
        )

        val year = assembleMonthlyYear(2026, days, today, Month.OCTOBER, payStartDay = 30)

        assertEquals(LocalDate(2025, 12, 30), year.range.start)
        assertEquals(PeriodTotals(Money(16_000_000), Money(50_000)), year.totals)
        val newest = year.months.first()
        assertEquals(Month.OCTOBER, newest.month)
        assertEquals(Month.NOVEMBER, newest.labelMonth)
        assertTrue(newest.expanded)
        assertEquals(PeriodTotals(Money(8_000_000), Money.ZERO), newest.totals)
        assertEquals(LocalDate(2025, 12, 29), monthlyQueryRange(2026, 30).start)
    }

    @Test
    fun `Daily scrolls to the day or to the nearest earlier day with rows`() {
        val days = listOf(section(20, 2), section(16, 1), section(10, 3))

        assertEquals(3, scrollIndexFor(days, LocalDate(2026, 10, 16)))
        assertEquals(3, scrollIndexFor(days, LocalDate(2026, 10, 18)))
        assertEquals(0, scrollIndexFor(days, LocalDate(2026, 10, 25)))
        // Nothing on or before the 5th: the oldest day is the nearest.
        assertEquals(5, scrollIndexFor(days, LocalDate(2026, 10, 5)))
        assertNull(scrollIndexFor(emptyList(), LocalDate(2026, 10, 5)))
    }
}
