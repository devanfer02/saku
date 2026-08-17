package com.dvnnfrr.saku.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DateRangeTest {

    private val jakarta = TimeZone.of("Asia/Jakarta")

    @Test
    fun `month range spans the whole calendar month`() {
        val range = DateRange.ofMonth(LocalDate(2026, 8, 17))
        assertEquals(LocalDate(2026, 8, 1), range.start)
        assertEquals(LocalDate(2026, 8, 31), range.endInclusive)
    }

    @Test
    fun `month range handles February in a leap year`() {
        val range = DateRange.ofMonth(LocalDate(2028, 2, 10))
        assertEquals(LocalDate(2028, 2, 29), range.endInclusive)
    }

    @Test
    fun `week range runs Monday to Sunday`() {
        // 2026-08-17 is a Monday; 2026-08-19 a Wednesday.
        val fromWednesday = DateRange.ofWeek(LocalDate(2026, 8, 19))
        assertEquals(LocalDate(2026, 8, 17), fromWednesday.start)
        assertEquals(LocalDate(2026, 8, 23), fromWednesday.endInclusive)
    }

    @Test
    fun `week range containing a Sunday does not roll forward`() {
        val fromSunday = DateRange.ofWeek(LocalDate(2026, 8, 23))
        assertEquals(LocalDate(2026, 8, 17), fromSunday.start)
        assertEquals(LocalDate(2026, 8, 23), fromSunday.endInclusive)
    }

    @Test
    fun `year range spans January to December`() {
        val range = DateRange.ofYear(2026)
        assertEquals(LocalDate(2026, 1, 1), range.start)
        assertEquals(LocalDate(2026, 12, 31), range.endInclusive)
    }

    @Test
    fun `stepping a month back crosses the year boundary`() {
        val january = DateRange.ofMonth(LocalDate(2026, 1, 15))
        val december = january.previous()
        assertEquals(LocalDate(2025, 12, 1), december.start)
        assertEquals(LocalDate(2025, 12, 31), december.endInclusive)
    }

    @Test
    fun `stepping a month forward from a long month lands correctly`() {
        val january = DateRange.ofMonth(LocalDate(2026, 1, 31))
        val february = january.next()
        assertEquals(LocalDate(2026, 2, 1), february.start)
        assertEquals(LocalDate(2026, 2, 28), february.endInclusive)
    }

    @Test
    fun `a custom range steps by its own length`() {
        val range = DateRange.custom(LocalDate(2026, 8, 1), LocalDate(2026, 8, 10))
        val previous = range.previous()
        assertEquals(10, previous.start.daysUntil(range.start))
        assertEquals(PeriodMode.CUSTOM, previous.mode)
    }

    @Test
    fun `contains is inclusive at both ends`() {
        val range = DateRange.ofMonth(LocalDate(2026, 8, 1))
        assertTrue(LocalDate(2026, 8, 1) in range)
        assertTrue(LocalDate(2026, 8, 31) in range)
        assertFalse(LocalDate(2026, 7, 31) in range)
        assertFalse(LocalDate(2026, 9, 1) in range)
    }

    @Test
    fun `end instant is exclusive so it excludes the following midnight`() {
        val range = DateRange.ofMonth(LocalDate(2026, 8, 1))
        val end = range.endInstantExclusive(jakarta)
        // 1 September 00:00 Jakarta is the first instant NOT in August.
        assertEquals(
            LocalDate(2026, 9, 1),
            end.toLocalDateTime(jakarta).date,
        )
    }

    @Test
    fun `a range cannot end before it starts`() {
        val error = runCatching {
            DateRange(LocalDate(2026, 8, 10), LocalDate(2026, 8, 1))
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}
