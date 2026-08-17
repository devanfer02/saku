package com.dvnnfrr.saku.ui.transactions

import com.dvnnfrr.saku.domain.Money
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class DateLabelsTest {

    @Test
    fun `a day header reads day, weekday, month and year`() {
        assertEquals("14 Fri Aug 2026", LocalDate(2026, 8, 14).dayHeaderLabel())
        assertEquals("1 Thu Jan 2026", LocalDate(2026, 1, 1).dayHeaderLabel())
    }

    @Test
    fun `the label does not follow the device locale`() {
        val original = Locale.getDefault()
        try {
            // Turkish is the classic trap: its lowercase of "I" is a dotless i, so a
            // locale-sensitive title-case would render "FRI" as "Frı".
            Locale.setDefault(Locale.forLanguageTag("tr"))
            assertEquals("14 Fri Aug 2026", LocalDate(2026, 8, 14).dayHeaderLabel())
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
    fun `a compact net fits a calendar cell`() {
        assertEquals("0", compactNet(Money.ZERO))
        assertEquals("45k", compactNet(Money(45_000)))
        assertEquals("-170k", compactNet(Money(-170_000)))
        assertEquals("5m", compactNet(Money(5_061_400)))
        assertEquals("-5m", compactNet(Money(-5_061_400)))
    }

    @Test
    fun `an amount under a thousand does not round away to zero`() {
        // Truncating 500 to "0" would read as a day with no net rather than a small one.
        assertEquals("<1k", compactNet(Money(400)))
        assertEquals("-<1k", compactNet(Money(-400)))
    }

    @Test
    fun `a compact net rounds rather than truncating`() {
        // 1.999.000 truncated to "1m" reads as half the value on a cell whose whole
        // job is a glance.
        assertEquals("2m", compactNet(Money(1_999_000)))
        assertEquals("-2m", compactNet(Money(-1_980_000)))
        assertEquals("1k", compactNet(Money(500)))
    }
}
