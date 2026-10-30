package com.dvnnfrr.saku.ui.stats

import com.dvnnfrr.saku.domain.DateRange
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The range picker's headline. Material's own read "Jan 1, 2026 - Dec 31, 2026" at
 * headline size, month first, and wrapped onto two lines on a 412dp phone.
 */
class RangePickerHeadlineTest {

    @Test
    fun `a whole year reads day first, as the stats screen writes it`() {
        assertEquals(
            "1 Jan 2026 – 31 Dec 2026",
            rangePickerHeadline(LocalDate(2026, 1, 1), LocalDate(2026, 12, 31)),
        )
    }

    @Test
    fun `the headline matches the label the stats screen shows once applied`() {
        val start = LocalDate(2025, 7, 28)
        val end = LocalDate(2026, 8, 16)
        assertEquals(
            explicitRangeLabel(DateRange.custom(start, end)),
            rangePickerHeadline(start, end),
        )
    }

    @Test
    fun `a start with no end reads as the one day Done would apply`() {
        assertEquals("17 Aug 2026", rangePickerHeadline(LocalDate(2026, 8, 17), null))
    }

    @Test
    fun `nothing picked has no headline`() {
        assertNull(rangePickerHeadline(null, null))
    }

    @Test
    fun `the longest span label stays short enough for one line`() {
        val label = rangePickerHeadline(LocalDate(2026, 12, 28), LocalDate(2027, 12, 31))!!
        // 25 characters at the heading size is about 215dp, inside the ~280dp the
        // headline row has beside the mode toggle on a 360dp dialog.
        assertFalse(label.length > 25)
        // The house style bans the em dash; the span uses an en dash.
        assertFalse(label.contains(EM_DASH))
    }

    private companion object {
        val EM_DASH = Char(0x2014).toString()
    }
}
