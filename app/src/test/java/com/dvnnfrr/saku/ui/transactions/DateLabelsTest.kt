package com.dvnnfrr.saku.ui.transactions

import com.dvnnfrr.saku.domain.DateRange
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class DateLabelsTest {

    @Test
    fun `a day header reads weekday, day and short month`() {
        assertEquals("Thu 29 Oct", LocalDate(2026, 10, 29).dayHeaderLabel())
        assertEquals("Thu 1 Jan", LocalDate(2026, 1, 1).dayHeaderLabel())
    }

    @Test
    fun `a date on its own carries its year`() {
        assertEquals("29 Oct 2026", LocalDate(2026, 10, 29).dateLabel())
    }

    @Test
    fun `the label does not follow the device locale`() {
        val original = Locale.getDefault()
        try {
            // Turkish is the classic trap: its lowercase of "I" is a dotless i, so a
            // locale-sensitive title-case would render "FRI" as "Frı".
            Locale.setDefault(Locale.forLanguageTag("tr"))
            assertEquals("Fri 14 Aug", LocalDate(2026, 8, 14).dayHeaderLabel())
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `the calendar headings run Monday first`() {
        assertEquals(
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
            WEEKDAY_HEADINGS,
        )
    }

    @Test
    fun `strip columns name a week by its Monday, a month by its short name, a year by its number`() {
        assertEquals("26 Oct", DateRange.ofWeek(LocalDate(2026, 10, 29)).stripColumnLabel())
        assertEquals("Oct", DateRange.ofMonth(LocalDate(2026, 10, 1)).stripColumnLabel())
        assertEquals("Jan 26", DateRange.ofMonth(LocalDate(2026, 1, 1)).stripColumnLabel())
        assertEquals("2026", DateRange.ofYear(2026).stripColumnLabel())
    }
}
