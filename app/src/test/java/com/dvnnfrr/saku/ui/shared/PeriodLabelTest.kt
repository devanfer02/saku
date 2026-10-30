package com.dvnnfrr.saku.ui.shared

import com.dvnnfrr.saku.domain.DateRange
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
