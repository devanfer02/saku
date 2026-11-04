package com.dvnnfrr.saku.ui.search

import com.dvnnfrr.saku.domain.Money
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ranges, monthly points, the Y axis and the nearest point of the price chart. */
class PriceChartAssemblyTest {

    private fun point(date: LocalDate, price: Long) = PricePoint(date, Money(price))

    private fun history(points: List<PricePoint>) = historyOf("MINYAKGORENG2L", "Minyak Goreng 2L", points)

    /** One point on the 10th of every month from [start], [months] of them, price 30.000 plus 100 a month. */
    private fun monthly(start: LocalDate, months: Int) =
        (0 until months).map { i -> point(start.plus(i, DateTimeUnit.MONTH), 30_000L + 100L * i) }

    // The owner's search: 19 rows from Jul 2025 to Oct 2026.
    private val owner = history(monthly(LocalDate(2025, 7, 28), 16) + point(LocalDate(2026, 10, 30), 39_000))

    @Test
    fun `a history over a year offers the ranges that cut it and opens on one year`() {
        assertEquals(listOf(PriceRange.SIX_MONTHS, PriceRange.ONE_YEAR, PriceRange.ALL), owner.ranges())
        assertEquals(PriceRange.ONE_YEAR, owner.defaultRange())
    }

    @Test
    fun `a history under six months offers All alone`() {
        val short = history(monthly(LocalDate(2026, 7, 10), 4))
        assertEquals(listOf(PriceRange.ALL), short.ranges())
        assertEquals(PriceRange.ALL, short.defaultRange())
    }

    @Test
    fun `a range holding under two points is not offered`() {
        // Bought once in 2023, then once in the last year: one year holds a single point.
        val sparse = history(listOf(point(LocalDate(2023, 1, 5), 30_000), point(LocalDate(2026, 10, 1), 38_000)))
        assertEquals(listOf(PriceRange.ALL), sparse.ranges())
        assertEquals(PriceRange.ALL, sparse.defaultRange())
    }

    @Test
    fun `a range showing the same points as a longer one is not offered`() {
        // The emulator's data: rows every year or so since 2016, then two in late October 2026.
        val gappy = history(
            listOf(2016, 2018, 2020, 2022, 2024).map { point(LocalDate(it, 3, 10), 30_000) } +
                point(LocalDate(2026, 10, 28), 38_900) + point(LocalDate(2026, 10, 30), 35_459),
        )
        // 6M and 1Y both hold the two October points; 3Y adds March 2024.
        assertEquals(listOf(PriceRange.ONE_YEAR, PriceRange.THREE_YEARS, PriceRange.ALL), gappy.ranges())
        assertEquals(PriceRange.ONE_YEAR, gappy.defaultRange())
    }

    @Test
    fun `a range keeps the points from its start and recomputes the figures`() {
        val year = owner.inRange(PriceRange.ONE_YEAR)
        // Latest 30 Oct 2026, so the year starts 30 Oct 2025: Nov 2025 onwards.
        assertEquals(LocalDate(2025, 11, 28), year.first.date)
        assertEquals(owner.latest, year.latest)
        assertEquals(Money(30_400), year.lowest.unitPrice)
        assertEquals(Money(39_000), year.highest.unitPrice)
        assertEquals(Money(39_000 - 30_400), year.change)
        assertEquals(28, year.changePercent)
        assertSame(owner, owner.inRange(PriceRange.ALL))
    }

    @Test
    fun `eighteen months or less draws every day`() {
        val chart = priceChart(owner.points)
        assertFalse(chart.monthly)
        assertEquals(owner.points.size, chart.points.size)
        assertEquals(LocalDate(2025, 7, 28).toEpochDays().toLong(), chart.points.first().position)
    }

    @Test
    fun `a few trips a year over ten years stay as days`() {
        val yearly = (2016..2026).map { point(LocalDate(it, 3, 10), 30_000L + it) }
        val chart = priceChart(yearly)
        assertFalse(chart.monthly)
        assertEquals(11, chart.points.size)
    }

    @Test
    fun `ten years draws one point per month at its average, rounded half up`() {
        val days = monthly(LocalDate(2016, 11, 10), 120) +
            point(LocalDate(2026, 10, 20), 30_000) + point(LocalDate(2026, 10, 25), 30_001)
        val chart = priceChart(days.sortedBy { it.date })
        assertTrue(chart.monthly)
        assertEquals(120, chart.points.size)
        assertEquals(LocalDate(2016, 11, 1), chart.points.first().date)
        val october = chart.points.last()
        assertEquals(LocalDate(2026, 10, 1), october.date)
        // (41.900 + 30.000 + 30.001) / 3 = 33.967
        assertEquals(Money(33_967), october.unitPrice)
        assertEquals(october.date.toEpochDays().toLong(), october.position)
    }

    @Test
    fun `monthly average rounds half up`() {
        val points = monthlyPoints(listOf(point(LocalDate(2026, 1, 2), 100), point(LocalDate(2026, 1, 3), 101)))
        assertEquals(Money(101), points.single().unitPrice)
    }

    @Test
    fun `the Y axis takes round steps around the prices, not from zero`() {
        val axis = priceAxis(Money(35_000), Money(41_000))
        assertEquals(Money(2_500), axis.step)
        assertEquals(Money(35_000), axis.low)
        assertEquals(Money(42_500), axis.high)
        assertEquals(listOf(35_000L, 37_500L, 40_000L, 42_500L), axis.ticks.map { it.rupiah })
    }

    @Test
    fun `the Y axis spans big and small prices in three or four lines`() {
        val big = priceAxis(Money(1_200_000), Money(5_000_000))
        assertEquals(listOf(0L, 2_000_000L, 4_000_000L, 6_000_000L), big.ticks.map { it.rupiah })
        val small = priceAxis(Money(3_000), Money(3_500))
        assertEquals(listOf(3_000L, 3_200L, 3_400L, 3_600L), small.ticks.map { it.rupiah })
    }

    @Test
    fun `a huge jump keeps a linear axis from zero in round juta`() {
        // A phone: 2 jt, 18 jt, 38 jt, then 5 jt.
        val phone = history(
            listOf(
                point(LocalDate(2019, 3, 10), 2_000_000),
                point(LocalDate(2021, 6, 15), 18_000_000),
                point(LocalDate(2023, 9, 20), 38_000_000),
                point(LocalDate(2026, 2, 14), 5_000_000),
            ),
        )
        val chart = priceChart(phone.points)
        assertFalse(chart.monthly)
        assertEquals(listOf(0L, 20_000_000L, 40_000_000L), chart.axis.ticks.map { it.rupiah })
        assertEquals(Money(3_000_000), phone.change)
        assertEquals(150, phone.changePercent)
        assertEquals(Money(38_000_000), phone.highest.unitPrice)
        // 1Y holds one point and 3Y two (Sep 2023 and Feb 2026).
        assertEquals(listOf(PriceRange.THREE_YEARS, PriceRange.ALL), phone.ranges())
        assertEquals(PriceRange.ALL, phone.defaultRange())
    }

    @Test
    fun `a flat price gets a band around it, never below zero`() {
        val flat = priceAxis(Money(38_900), Money(38_900))
        assertEquals(listOf(35_000L, 40_000L, 45_000L), flat.ticks.map { it.rupiah })
        val tiny = priceAxis(Money(50), Money(50))
        assertEquals(0L, tiny.low.rupiah)
        assertEquals(Money(200), tiny.high)
    }

    @Test
    fun `a touch selects the nearest point by date, the earlier on a tie`() {
        val positions = listOf(0L, 10L, 40L, 41L)
        assertEquals(0, nearestPoint(positions, -5.0))
        assertEquals(0, nearestPoint(positions, 4.9))
        assertEquals(0, nearestPoint(positions, 5.0))
        assertEquals(1, nearestPoint(positions, 5.1))
        assertEquals(2, nearestPoint(positions, 39.0))
        assertEquals(3, nearestPoint(positions, 40.6))
        assertEquals(3, nearestPoint(positions, 99.0))
        assertEquals(0, nearestPoint(listOf(7L), 100.0))
    }
}
