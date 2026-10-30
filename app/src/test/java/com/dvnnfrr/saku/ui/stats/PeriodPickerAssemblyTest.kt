package com.dvnnfrr.saku.ui.stats

import com.dvnnfrr.saku.domain.DailyTotals
import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.Money
import com.dvnnfrr.saku.domain.PeriodMode
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodPickerAssemblyTest {

    private val today = LocalDate(2026, 10, 30)
    private val august = DateRange.ofMonth(LocalDate(2026, 8, 1))

    @Test
    fun `the month grid holds the twelve months of its year in order`() {
        val months = monthChoices(2026, august, today)

        assertEquals((1..12).map { DateRange.ofMonth(LocalDate(2026, it, 1)) }, months.map { it.range })
        assertEquals(
            listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"),
            months.map { it.label },
        )
    }

    @Test
    fun `the month grid marks the current month and highlights the selected one`() {
        val months = monthChoices(2026, august, today)

        assertEquals(listOf("Oct"), months.filter { it.current }.map { it.label })
        assertEquals(listOf("Aug"), months.filter { it.selected }.map { it.label })
    }

    @Test
    fun `another year of the month grid has neither current nor selected month`() {
        val months = monthChoices(2025, august, today)

        assertTrue(months.none { it.current || it.selected })
    }

    @Test
    fun `a picked month is the same range the arrows step to`() {
        val march2025 = monthChoices(2025, august, today)[2].range

        assertEquals(DateRange.ofMonth(LocalDate(2025, 4, 1)).previous(), march2025)
        assertEquals(PeriodMode.MONTHLY, march2025.mode)
    }

    @Test
    fun `the year grid runs from the first year with data through next year`() {
        val years = yearChoices(firstDataYear = 2025, selected = DateRange.ofYear(2026), today = today)

        assertEquals(listOf("2025", "2026", "2027"), years.map { it.label })
        assertEquals(listOf(2026), years.filter { it.current }.map { it.range.start.year })
        assertEquals(listOf(2026), years.filter { it.selected }.map { it.range.start.year })
    }

    @Test
    fun `with no data the year grid starts five years back`() {
        val years = yearChoices(firstDataYear = null, selected = DateRange.ofYear(2026), today = today)

        assertEquals((2021..2027).map { DateRange.ofYear(it) }, years.map { it.range })
    }

    @Test
    fun `a selected year outside the usual span is still offered`() {
        val early = yearChoices(firstDataYear = 2025, selected = DateRange.ofYear(2019), today = today)
        val late = yearChoices(firstDataYear = 2025, selected = DateRange.ofYear(2031), today = today)

        assertEquals(2019, early.first().range.start.year)
        assertTrue(early.single { it.selected }.range == DateRange.ofYear(2019))
        assertEquals(2031, late.last().range.start.year)
        assertTrue(late.single { it.selected }.range == DateRange.ofYear(2031))
    }

    @Test
    fun `the first year with data is the strip's first column`() {
        val days = listOf(
            DailyTotals(LocalDate(2025, 7, 20), Money.ZERO, Money(50_000)),
            DailyTotals(LocalDate(2026, 10, 3), Money(100_000), Money.ZERO),
        )

        assertEquals(2025, firstDataYear(foldStrip(days, DateRange.ofYear(2026), today)))
    }

    @Test
    fun `an empty strip has no first year with data`() {
        assertNull(firstDataYear(foldStrip(emptyList(), DateRange.ofYear(2026), today)))
        assertNull(firstDataYear(emptyList()))
    }

    @Test
    fun `today's period follows the mode`() {
        assertEquals(DateRange.ofWeek(LocalDate(2026, 10, 26)), periodContaining(today, PeriodMode.WEEKLY))
        assertEquals(DateRange.ofMonth(LocalDate(2026, 10, 1)), periodContaining(today, PeriodMode.MONTHLY))
        assertEquals(DateRange.ofYear(2026), periodContaining(today, PeriodMode.ANNUALLY))
    }

    @Test
    fun `a picked date selects the Monday to Sunday week holding it`() {
        val week = periodContaining(LocalDate(2026, 10, 15), PeriodMode.WEEKLY)

        assertEquals(LocalDate(2026, 10, 12), week.start)
        assertEquals(LocalDate(2026, 10, 18), week.endInclusive)
    }
}
