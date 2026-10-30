package com.dvnnfrr.saku.ui.shared

import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.PeriodMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale
import kotlinx.datetime.LocalDate

class PeriodLabelTest {

    private val originalLocale: Locale = Locale.getDefault()

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `a month reads as its name and year`() {
        assertEquals("August 2026", periodLabel(DateRange.ofMonth(LocalDate(2026, 8, 17))))
        assertEquals("January 2026", periodLabel(DateRange.ofMonth(LocalDate(2026, 1, 1))))
        assertEquals("December 2025", periodLabel(DateRange.ofMonth(LocalDate(2025, 12, 31))))
    }

    @Test
    fun `a pay month reads as its majority month with its span beneath`() {
        val late = DateRange.ofPayMonth(LocalDate(2026, 10, 30), 25)
        assertEquals("November 2026", periodLabel(late))
        assertEquals("25 Oct to 24 Nov 2026", paySpanLabel(late))

        val early = DateRange.ofPayMonth(LocalDate(2026, 10, 30), 5)
        assertEquals("October 2026", periodLabel(early))
        assertEquals("5 Oct to 4 Nov 2026", paySpanLabel(early))
    }

    @Test
    fun `a tied pay month reads as the later month`() {
        val tied = DateRange.ofPayMonth(LocalDate(2026, 9, 20), 16)
        assertEquals("October 2026", periodLabel(tied))
        assertEquals("16 Sep to 15 Oct 2026", paySpanLabel(tied))
    }

    @Test
    fun `a pay month across the new year names both years in its span`() {
        val pay = DateRange.ofPayMonth(LocalDate(2027, 1, 2), 30)
        assertEquals("January 2027", periodLabel(pay))
        assertEquals("30 Dec 2026 to 29 Jan 2027", paySpanLabel(pay))
    }

    @Test
    fun `a year of pay months reads as the year it mostly falls in`() {
        val span = DateRange(LocalDate(2025, 12, 30), LocalDate(2026, 12, 29), PeriodMode.ANNUALLY)
        assertEquals("2026", periodLabel(span))
    }

    @Test
    fun `a year reads as the year alone`() {
        assertEquals("2026", periodLabel(DateRange.ofYear(2026)))
    }

    @Test
    fun `a week inside one month names the month once`() {
        assertEquals("17 – 23 Aug 2026", periodLabel(DateRange.ofWeek(LocalDate(2026, 8, 17))))
    }

    @Test
    fun `a week straddling two months names both`() {
        assertEquals("27 Jul – 2 Aug 2026", periodLabel(DateRange.ofWeek(LocalDate(2026, 7, 30))))
    }

    @Test
    fun `a custom range crossing a year names both years`() {
        val range = DateRange.custom(LocalDate(2025, 12, 29), LocalDate(2026, 1, 4))
        assertEquals("29 Dec 2025 – 4 Jan 2026", periodLabel(range))
    }

    @Test
    fun `a single-day range reads as one date`() {
        val range = DateRange.custom(LocalDate(2026, 8, 17), LocalDate(2026, 8, 17))
        assertEquals("17 Aug 2026", periodLabel(range))
    }

    @Test
    fun `the label does not follow the device locale`() {
        val range = DateRange.ofMonth(LocalDate(2026, 8, 17))
        for (locale in listOf(Locale.US, Locale.GERMANY, Locale.JAPAN, Locale.forLanguageTag("id-ID"))) {
            Locale.setDefault(locale)
            assertEquals("August 2026", periodLabel(range))
        }
    }

    @Test
    fun `every month has a name`() {
        for (month in 1..12) {
            val label = periodLabel(DateRange.ofMonth(LocalDate(2026, month, 1)))
            assertEquals("$label should end in the year", true, label.endsWith(" 2026"))
        }
    }
}
