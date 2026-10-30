package com.dvnnfrr.saku.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PayMonthTest {

    private fun range(start: LocalDate, end: LocalDate, day: Int) =
        DateRange(start, end, PeriodMode.MONTHLY, day)

    private fun lengthOfMonth(date: LocalDate): Int =
        LocalDate(date.year, date.month, 1).plus(1, DateTimeUnit.MONTH).plus(-1, DateTimeUnit.DAY).dayOfMonth

    @Test
    fun `start day 25 on 30 Oct 2026 runs 25 Oct to 24 Nov`() {
        val pay = DateRange.ofPayMonth(LocalDate(2026, 10, 30), 25)

        assertEquals(LocalDate(2026, 10, 25), pay.start)
        assertEquals(LocalDate(2026, 11, 24), pay.endInclusive)
    }

    @Test
    fun `a day before the start day belongs to the pay month that began last month`() {
        val pay = DateRange.ofPayMonth(LocalDate(2026, 10, 24), 25)

        assertEquals(LocalDate(2026, 9, 25), pay.start)
        assertEquals(LocalDate(2026, 10, 24), pay.endInclusive)
    }

    @Test
    fun `start day 30 on 30 Oct 2026 matches the settings preview`() {
        val current = DateRange.ofPayMonth(LocalDate(2026, 10, 30), 30)
        val previous = current.previous()

        assertEquals(range(LocalDate(2026, 10, 30), LocalDate(2026, 11, 29), 30), current)
        assertEquals(range(LocalDate(2026, 9, 30), LocalDate(2026, 10, 29), 30), previous)
    }

    @Test
    fun `start day 31 steps across February without drifting`() {
        val january = DateRange.ofPayMonth(LocalDate(2026, 1, 31), 31)
        assertEquals(LocalDate(2026, 1, 31), january.start)
        assertEquals(LocalDate(2026, 2, 27), january.endInclusive)

        val february = january.next()
        assertEquals(LocalDate(2026, 2, 28), february.start)
        assertEquals(LocalDate(2026, 3, 30), february.endInclusive)

        val march = february.next()
        assertEquals(LocalDate(2026, 3, 31), march.start)
        assertEquals(february, march.previous())
        assertEquals(january, february.previous())
    }

    @Test
    fun `start day 30 in a leap February clamps to the 29th`() {
        val pay = DateRange.ofPayMonth(LocalDate(2028, 3, 1), 30)

        assertEquals(LocalDate(2028, 2, 29), pay.start)
        assertEquals(LocalDate(2028, 3, 29), pay.endInclusive)
    }

    @Test
    fun `the comparison period for 30 Sep to 29 Oct is 30 Aug to 29 Sep`() {
        val pay = DateRange.ofPayMonth(LocalDate(2026, 10, 1), 30)

        assertEquals(range(LocalDate(2026, 8, 30), LocalDate(2026, 9, 29), 30), pay.previous())
    }

    @Test
    fun `a twelve-month shift lands on the same pay month a year on`() {
        val pay = DateRange.ofPayMonth(LocalDate(2026, 2, 28), 31)

        assertEquals(DateRange.ofPayMonth(LocalDate(2027, 2, 28), 31), pay.shift(12))
    }

    @Test
    fun `monthOf with no start day is the calendar month`() {
        val day = LocalDate(2026, 10, 30)

        assertEquals(DateRange.ofMonth(day), DateRange.monthOf(day, null))
        assertEquals(DateRange.ofPayMonth(day, 25), DateRange.monthOf(day, 25))
        assertNull(DateRange.ofMonth(day).payStartDay)
    }

    @Test
    fun `a start day outside 1 to 31 is refused`() {
        for (day in listOf(0, 32, -1)) {
            try {
                DateRange.ofPayMonth(LocalDate(2026, 10, 1), day)
                fail("start day $day was accepted")
            } catch (expected: IllegalArgumentException) {
                // Refused, as it should be.
            }
        }
    }

    /**
     * The whole rule at once: for every start day, walk 2025 to 2027 a day at a time and
     * check each day lands in exactly one pay month, that stepping is adjacent and
     * reversible, and that day 1 is the calendar month.
     */
    @Test
    fun `every start day covers every day of 2025 to 2027 exactly once`() {
        val first = LocalDate(2025, 1, 1)
        val last = LocalDate(2027, 12, 31)

        for (startDay in 1..31) {
            // Walk forward by next() from the pay month holding the first day.
            var pay = DateRange.ofPayMonth(first, startDay)
            var day = first
            while (day <= last) {
                if (day > pay.endInclusive) {
                    val following = pay.next()
                    assertEquals(
                        "D=$startDay: ${following.start} follows ${pay.endInclusive}",
                        pay.endInclusive.plus(1, DateTimeUnit.DAY),
                        following.start,
                    )
                    assertEquals("D=$startDay: previous() undoes next()", pay, following.previous())
                    pay = following
                }
                assertTrue("D=$startDay: $day in $pay", day in pay)
                // Built from the day itself, the pay month must be the one the walk reached.
                assertEquals("D=$startDay on $day", pay, DateRange.ofPayMonth(day, startDay))

                assertEquals(
                    "D=$startDay: ${pay.start} is the clamped start day",
                    minOf(startDay, lengthOfMonth(pay.start)),
                    pay.start.dayOfMonth,
                )
                if (startDay == 1) assertEquals(DateRange.ofMonth(day), pay)

                day = day.plus(1, DateTimeUnit.DAY)
            }
        }
    }

    @Test
    fun `the majority month names a pay month`() {
        // 25 Oct to 24 Nov: 7 days of October, 24 of November.
        assertEquals(LocalDate(2026, 11, 1), DateRange.ofPayMonth(LocalDate(2026, 10, 30), 25).majorityMonth())
        // 5 Oct to 4 Nov: 27 days of October, 4 of November.
        assertEquals(LocalDate(2026, 10, 1), DateRange.ofPayMonth(LocalDate(2026, 10, 30), 5).majorityMonth())
    }

    @Test
    fun `a tied pay month is named after the later month`() {
        // 16 Sep to 15 Oct: 15 days each side.
        val tied = DateRange.ofPayMonth(LocalDate(2026, 9, 20), 16)
        assertEquals(LocalDate(2026, 9, 16), tied.start)
        assertEquals(LocalDate(2026, 10, 15), tied.endInclusive)

        assertEquals(LocalDate(2026, 10, 1), tied.majorityMonth())
    }

    @Test
    fun `rebasing onto pay months keeps the month holding today`() {
        val today = LocalDate(2026, 10, 30)

        assertEquals(DateRange.ofPayMonth(today, 30), DateRange.ofMonth(today).rebased(30, today))
        assertEquals(DateRange.ofMonth(today), DateRange.ofPayMonth(today, 30).rebased(null, today))
    }

    @Test
    fun `rebasing a past month keeps its name`() {
        val today = LocalDate(2026, 10, 30)
        val march = DateRange.ofMonth(LocalDate(2026, 3, 1))

        // Start day 5: the pay month holding 1 Mar is called February, so March is the next.
        assertEquals(DateRange.ofPayMonth(LocalDate(2026, 3, 5), 5), march.rebased(5, today))
        // Start day 30: the pay month holding 1 Mar is already called March.
        assertEquals(DateRange.ofPayMonth(LocalDate(2026, 3, 1), 30), march.rebased(30, today))
        // And back again.
        assertEquals(march, march.rebased(5, today).rebased(null, today))
    }

    @Test
    fun `rebasing to the same basis or to start day 1 changes nothing`() {
        val today = LocalDate(2026, 10, 30)
        val pay = DateRange.ofPayMonth(LocalDate(2026, 3, 1), 30)
        val march = DateRange.ofMonth(LocalDate(2026, 3, 1))

        assertEquals(pay, pay.rebased(30, today))
        assertEquals(march, march.rebased(1, today))
    }

    @Test
    fun `a calendar month and a year are their own majority`() {
        assertEquals(LocalDate(2026, 2, 1), DateRange.ofMonth(LocalDate(2026, 2, 10)).majorityMonth())
        assertEquals(2026, DateRange.ofYear(2026).majorityMonth().year)
    }
}
