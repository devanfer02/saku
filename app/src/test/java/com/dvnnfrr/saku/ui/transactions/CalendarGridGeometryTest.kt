package com.dvnnfrr.saku.ui.transactions

import com.dvnnfrr.saku.domain.DateRange
import com.dvnnfrr.saku.domain.Transaction
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Maps a touch point on the Calendar grid to its day, which the drag relies on. */
class CalendarGridGeometryTest {

    // 700 px wide: 100 px columns. Five weeks of 100 px rows with 10 px gaps: 540 px.
    private fun slot(x: Float, y: Float) = calendarSlotAt(x, y, width = 700, height = 540, weeks = 5, rowGapPx = 10f)

    // August 2026 starts on a Saturday: five blanks, then the 1st in slot 5.
    private val august = emptyList<Transaction>()
        .toCalendarMonth(DateRange.ofMonth(LocalDate(2026, 8, 1)), TimeZone.UTC)

    @Test
    fun `a point maps to its row and column`() {
        assertEquals(0, slot(5f, 5f))
        assertEquals(6, slot(699f, 5f))
        assertEquals(7 + 2, slot(250f, 150f))
        assertEquals(4 * 7 + 6, slot(650f, 530f))
    }

    @Test
    fun `a point in a row gap belongs to the row above`() {
        assertEquals(0, slot(5f, 105f))
        assertEquals(7, slot(5f, 111f))
    }

    @Test
    fun `a point off the grid clamps to the nearest edge cell`() {
        assertEquals(0, slot(-40f, -40f))
        assertEquals(4 * 7 + 6, slot(900f, 900f))
    }

    @Test
    fun `a blank slot has no day for a tap, but the nearest day for a drag`() {
        assertNull(august.dayAtSlot(4))
        assertEquals(LocalDate(2026, 8, 1), august.dayAtSlot(5))
        assertEquals(LocalDate(2026, 8, 1), august.nearestDayAtSlot(0))
        assertEquals(LocalDate(2026, 8, 31), august.nearestDayAtSlot(41))
    }
}
